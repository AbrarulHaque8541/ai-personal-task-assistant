package com.cue.daymark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Host coverage for the interrupted-export contract that the portable export path now depends on.
 *
 * <p>A SAF destination is an already-created document, so the previous {@code openOutputStream(uri,
 * "w")} write left a partial file under the final backup name whenever the copy was interrupted. The
 * regressions below pin the replacement behaviour: the document is marked incomplete first, the copy
 * is verified against the staged archive, an independent read-back is required, and a failed copy
 * never leaves a complete-looking backup behind.
 */
public final class PortableExportWriterSmoke {
    private static int assertions;

    private PortableExportWriterSmoke() {
    }

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("daymark-portable-export").toFile();
        try {
            copyIsMarkedVerifiedAndFinalized(root);
            interruptedCopyStaysMarkedIncompleteAndIsRemoved(root);
            cancellationLeavesNoCompleteLookingBackup(root);
            oversizedAndEmptyStagedArchivesAreRejected(root);
            unsafeDisplayNamesAndFinalizationFailureAreRejected(root);
            System.out.println("PASS portable export writer smoke tests: " + assertions + " assertions");
        } finally {
            erase(root);
        }
    }

    private static void copyIsMarkedVerifiedAndFinalized(File root) throws Exception {
        File staged = stagedArchive(root, "success", 256 * 1024);
        FakeDocument document = new FakeDocument("daymark-backup.dmbackup");
        String finalized = PortableExportWriter.write(staged, document, "token0123",
                "daymark-backup.dmbackup", () -> false);
        check("daymark-backup.dmbackup".equals(finalized), "a verified copy finalizes under the suggested name");
        check(document.writeOpenedBeforeFinalize, "the content was written before the name was finalized");
        check(document.readBackOpened, "the final copy was independently read back before finalization");
        check(document.openForWriteCalls == 1 && document.openForReadCalls == 1,
                "exactly one write and one read-back are performed");
        check(Files.mismatch(staged.toPath(), document.contentFile().toPath()) == -1,
                "the finalized document is byte-identical to the staged archive");
        check(document.renameHistory.get(0).contains(PortableExportWriter.INCOMPLETE_MARKER),
                "the document is marked incomplete before any bytes are written");
        check(!document.renameHistory.get(document.renameHistory.size() - 1)
                        .contains(PortableExportWriter.INCOMPLETE_MARKER),
                "the finalized name no longer carries the incomplete marker");
        check(!document.deleted, "a verified copy is never deleted");
    }

    private static void interruptedCopyStaysMarkedIncompleteAndIsRemoved(File root) throws Exception {
        File staged = stagedArchive(root, "interrupted", 128 * 1024);
        FakeDocument document = new FakeDocument("daymark-backup.dmbackup", 32 * 1024);
        boolean rejected = false;
        try {
            PortableExportWriter.write(staged, document, "token0123", "daymark-backup.dmbackup", () -> false);
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, "a write interrupted part-way through is reported as a failure");
        check(!document.finalNamePresent(),
                "an interrupted copy never ends up under the final backup name");
        check(document.deleted, "an interrupted copy is removed from the destination when the provider allows it");
        check(document.renameHistory.get(0).contains(PortableExportWriter.INCOMPLETE_MARKER),
                "the interrupted document was conspicuous before the failure");
    }

    private static void cancellationLeavesNoCompleteLookingBackup(File root) throws Exception {
        File staged = stagedArchive(root, "cancelled", 256 * 1024);
        List<Long> reads = new ArrayList<>();
        FakeDocument document = new FakeDocument("daymark-backup.dmbackup", -1, reads);
        boolean rejected = false;
        try {
            PortableExportWriter.write(staged, document, "token0123", "daymark-backup.dmbackup",
                    () -> reads.size() > 1);
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, "a cancelled export aborts the copy");
        check(PortableExportWriter.isMarkedIncomplete(document.currentName())
                        || document.deleted || !document.finalNamePresent(),
                "a cancelled export never leaves a complete-looking backup behind");
    }

    private static void oversizedAndEmptyStagedArchivesAreRejected(File root) throws Exception {
        File empty = new File(root, "empty.dmbackup");
        check(empty.createNewFile(), "empty fixture created");
        FakeDocument emptyDocument = new FakeDocument("daymark-backup.dmbackup");
        expectIOException(() -> PortableExportWriter.write(empty, emptyDocument, "token0123",
                        "daymark-backup.dmbackup", () -> false),
                "an empty staged archive is rejected before touching the destination");
        check(emptyDocument.renameHistory.isEmpty(),
                "an invalid staged archive never renames or writes the destination");

        File oversized = new File(root, "oversized.dmbackup");
        try (OutputStream output = new FileOutputStream(oversized)) {
            long remaining = PortableBackupCodec.MAX_ARCHIVE_BYTES + 1;
            byte[] block = new byte[1024 * 1024];
            while (remaining > 0) {
                int count = (int) Math.min(remaining, block.length);
                output.write(block, 0, count);
                remaining -= count;
            }
        }
        FakeDocument oversizedDocument = new FakeDocument("daymark-backup.dmbackup");
        expectIOException(() -> PortableExportWriter.write(oversized, oversizedDocument, "token0123",
                        "daymark-backup.dmbackup", () -> false),
                "a staged archive beyond the archive cap is rejected before writing");
        check(oversizedDocument.renameHistory.isEmpty(),
                "an oversized staged archive never renames or writes the destination");
    }

    private static void unsafeDisplayNamesAndFinalizationFailureAreRejected(File root) throws Exception {
        File staged = stagedArchive(root, "names", 16 * 1024);
        expectIOException(() -> PortableExportWriter.write(staged,
                        new FakeDocument("daymark-backup.dmbackup"), "../escape",
                        "daymark-backup.dmbackup", () -> false),
                "an unsafe temporary token is rejected");

        FakeDocument renamed = new FakeDocument("user-renamed-backup.dmbackup");
        String finalized = PortableExportWriter.write(staged, renamed, "token0123",
                "daymark-backup.dmbackup", () -> false);
        check("user-renamed-backup.dmbackup".equals(finalized),
                "a document the user renamed in the picker keeps the user's chosen name");
        check(renamed.renameHistory.get(0).startsWith("user-renamed-backup.dmbackup"
                        + PortableExportWriter.INCOMPLETE_MARKER),
                "the user's own name is also marked incomplete while writing");

        FakeDocument noRename = new FakeDocument("daymark-backup.dmbackup");
        noRename.failRenameToFinal = true;
        expectIOException(() -> PortableExportWriter.write(staged, noRename, "token0123",
                        "daymark-backup.dmbackup", () -> false),
                "a provider that cannot finalize the name reports failure instead of claiming success");
        check(!noRename.deleted,
                "bytes that were verified and read back are not discarded when only the name cannot be finalized");
        check(PortableExportWriter.isMarkedIncomplete(noRename.currentName()),
                "a copy retained after a finalization failure stays conspicuously marked incomplete");
        check(!noRename.finalNamePresent(),
                "a finalization failure never leaves the document under the final backup name");
    }

    private static File stagedArchive(File root, String name, int size) throws IOException {
        File staged = new File(root, name + ".dmbackup");
        byte[] block = new byte[size];
        for (int index = 0; index < size; index++) block[index] = (byte) (index * 31 + 7);
        try (OutputStream output = new FileOutputStream(staged)) {
            output.write(block);
        }
        return staged;
    }

    /** In-memory SAF stand-in: same document, rename/open-in-place/read-back/delete semantics. */
    private static final class FakeDocument implements PortableExportWriter.Document {
        private final String initialName;
        private final int writeLimit;
        private final List<Long> readCounters;
        private final List<String> renameHistory = new ArrayList<>();
        private ByteArrayOutputStream content = new ByteArrayOutputStream();
        private String name;
        private String lastIncompleteName;
        private boolean writeOpenedBeforeFinalize;
        private boolean readBackOpened;
        private int openForWriteCalls;
        private int openForReadCalls;
        private boolean deleted;
        private boolean failRenameToFinal;

        FakeDocument(String initialName) {
            this(initialName, -1, null);
        }

        FakeDocument(String initialName, int writeLimit) {
            this(initialName, writeLimit, null);
        }

        FakeDocument(String initialName, int writeLimit, List<Long> readCounters) {
            this.initialName = initialName;
            this.name = initialName;
            this.writeLimit = writeLimit;
            this.readCounters = readCounters;
        }

        File contentFile() throws IOException {
            File file = Files.createTempFile("daymark-export-destination", ".bin").toFile();
            try (OutputStream output = new FileOutputStream(file)) {
                output.write(content.toByteArray());
            }
            file.deleteOnExit();
            return file;
        }

        String currentName() {
            return name;
        }

        boolean finalNamePresent() {
            return initialName.equals(name) && !writeOpenedBeforeFinalize;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public String renameTo(String displayName) throws IOException {
            if (failRenameToFinal && lastIncompleteName != null
                    && !displayName.contains(PortableExportWriter.INCOMPLETE_MARKER)) {
                return null;
            }
            renameHistory.add(displayName);
            name = displayName;
            if (displayName.contains(PortableExportWriter.INCOMPLETE_MARKER)) {
                lastIncompleteName = displayName;
            }
            return name;
        }

        @Override
        public OutputStream openForWrite() {
            writeOpenedBeforeFinalize = lastIncompleteName != null && name.equals(lastIncompleteName);
            openForWriteCalls++;
            ByteArrayOutputStream sink = content;
            int limit = writeLimit;
            return new OutputStream() {
                private int written;

                @Override
                public void write(int value) throws IOException {
                    enforce(1);
                    sink.write(value);
                }

                @Override
                public void write(byte[] buffer, int offset, int length) throws IOException {
                    enforce(length);
                    sink.write(buffer, offset, length);
                }

                private void enforce(int length) throws IOException {
                    if (limit >= 0 && written + length > limit) {
                        throw new IOException("simulated destination write failure");
                    }
                    written += length;
                    if (readCounters != null) readCounters.add((long) length);
                }
            };
        }

        @Override
        public InputStream openForRead() {
            readBackOpened = true;
            openForReadCalls++;
            return new ByteArrayInputStream(content.toByteArray());
        }

        @Override
        public boolean delete() {
            deleted = true;
            content = new ByteArrayOutputStream();
            return true;
        }
    }

    private interface IoOperation {
        void run() throws Exception;
    }

    private static void expectIOException(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void erase(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) erase(child);
        }
        file.delete();
    }
}

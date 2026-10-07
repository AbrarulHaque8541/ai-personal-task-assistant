package com.cue.daymark;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class AttachmentBlobStoreSmoke {
    private static int assertions;
    private static final String TASK_A = "00000000-0000-4000-8000-000000000001";
    private static final String TASK_B = "00000000-0000-4000-8000-000000000002";

    private AttachmentBlobStoreSmoke() { }

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("daymark-attachments-smoke").toFile();
        try {
            encryptedCopySurvivesReopenAndCanBeDeleted(root);
            payloadSubstitutionAcrossAttachmentOrTaskFailsAuthentication(root);
            failedReadsAndInterruptedImportsLeaveNoPlaintextOrPartial(root);
            exactFileLimitPassesAndOversizeFails(root);
            quotasCancellationAndLowSpaceAreEnforcedDuringStreaming(root);
            corruptedPayloadFailsAuthentication(root);
            verifyReadableAuthenticatesOrFailsClosed(root);
            System.out.println("PASS encrypted attachment smoke tests: " + assertions + " assertions");
        } finally {
            erase(root);
        }
    }

    private static void encryptedCopySurvivesReopenAndCanBeDeleted(File root) throws Exception {
        File directory = new File(root, "roundtrip");
        KeyVault key = new KeyVault();
        AttachmentBlobStore first = new AttachmentBlobStore(directory, key);
        byte[] plaintext = "private local attachment bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String id = AttachmentBlobStore.newId();
        check(first.importStream(TASK_A, id, new ByteArrayInputStream(plaintext)) == plaintext.length,
                "import records measured byte count");
        File encrypted = new File(directory, id + ".enc");
        check(encrypted.isFile(), "committed payload uses the app-owned opaque ID");
        check(!Arrays.equals(plaintext, Files.readAllBytes(encrypted.toPath())),
                "payload at rest is not plaintext");

        AttachmentBlobStore reopened = new AttachmentBlobStore(directory, key);
        try (InputStream input = reopened.openInput(TASK_A, id)) {
            check(Arrays.equals(plaintext, input.readAllBytes()), "app reopen decrypts the committed payload");
        }
        String orphanId = AttachmentBlobStore.newId();
        reopened.importStream(TASK_A, orphanId, new ByteArrayInputStream(new byte[] { 9, 8, 7 }));
        new File(directory, AttachmentBlobStore.newId() + ".pending").createNewFile();
        check(reopened.cleanupOrphans(Collections.singleton(id)) == 2,
                "startup cleanup removes unreferenced payloads and interrupted staging files");
        check(reopened.exists(id) && !reopened.exists(orphanId), "cleanup preserves only referenced payloads");

        reopened.delete(id);
        check(!reopened.exists(id), "attachment deletion removes the app-owned payload");
        expectIOException(() -> reopened.openInput(TASK_A, id), "missing attachment is reported, not treated as empty data");
        check(!reopened.exists("../untrusted"), "provider-supplied path cannot become an ID");
    }

    private static void failedReadsAndInterruptedImportsLeaveNoPlaintextOrPartial(File root) throws Exception {
        File directory = new File(root, "interrupted");
        AttachmentBlobStore store = new AttachmentBlobStore(directory, new KeyVault());
        String id = AttachmentBlobStore.newId();
        AtomicBoolean encryptedStageObserved = new AtomicBoolean();
        InputStream broken = new InputStream() {
            private int emitted;
            @Override public int read() throws IOException {
                if (emitted++ >= 10_000) throw new IOException("simulated provider read failure");
                return 37;
            }
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                if (emitted >= 10_000) {
                    File stage = new File(directory, id + ".pending");
                    byte[] header = Files.readAllBytes(stage.toPath());
                    encryptedStageObserved.set(header.length > 4 && header[0] == 'D' && header[1] == 'M'
                            && header[2] == 'A' && header[3] == '2');
                    throw new IOException("simulated provider read failure");
                }
                int count = Math.min(length, 10_000 - emitted);
                Arrays.fill(buffer, offset, offset + count, (byte) 37);
                emitted += count;
                return count;
            }
        };
        expectIOException(() -> store.importStream(TASK_A, id, broken), "provider read error is propagated");
        check(encryptedStageObserved.get(), "an interrupted staging file contains ciphertext, never plaintext");
        check(!store.exists(id), "failed provider read never becomes a committed attachment");
        check(emptyDirectory(directory), "interrupted import staging file is deleted");
    }

    private static void exactFileLimitPassesAndOversizeFails(File root) throws Exception {
        File directory = new File(root, "limits");
        AttachmentBlobStore store = new AttachmentBlobStore(directory, new KeyVault());
        String exactId = AttachmentBlobStore.newId();
        long max = AttachmentLogic.MAX_FILE_BYTES;
        check(store.importStream(TASK_A, exactId, new RepeatingInputStream(max)) == max,
                "a stream exactly at 20 MiB is accepted");
        File committed = new File(directory, exactId + ".enc");
        check(committed.length() == max + 32, "ciphertext size is bounded by plaintext cap plus GCM overhead");

        String overId = AttachmentBlobStore.newId();
        expectFileLimit(() -> store.importStream(TASK_A, overId, new RepeatingInputStream(max + 1)),
                "a stream one byte above 20 MiB is rejected even without a provider size hint");
        check(!store.exists(overId), "oversize input leaves no committed payload");
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".pending"));
        check(files != null && files.length == 0, "oversize staging file is cleaned up");

        String overstatedHintId = AttachmentBlobStore.newId();
        check(store.importStream(TASK_A, overstatedHintId, new ByteArrayInputStream(new byte[] { 1, 2, 3 }),
                        AttachmentLogic.MAX_TOTAL_BYTES - max, 100, () -> false) == 3,
                "a false high provider size hint does not replace the actual committed byte count");
    }

    private static void quotasCancellationAndLowSpaceAreEnforcedDuringStreaming(File root) throws Exception {
        File totalDirectory = new File(root, "total-stream-cap");
        AttachmentBlobStore totalStore = new AttachmentBlobStore(totalDirectory, new KeyVault());
        String totalId = AttachmentBlobStore.newId();
        expectStorageLimit(() -> totalStore.importStream(TASK_A, totalId, new RepeatingInputStream(11), 10, 1,
                        () -> false),
                "actual streamed bytes cannot exceed remaining total quota despite a smaller provider size hint");
        check(emptyDirectory(totalDirectory), "total-limit rejection removes all staged bytes");

        File unknownHintDirectory = new File(root, "unknown-total-stream-cap");
        AttachmentBlobStore unknownHintStore = new AttachmentBlobStore(unknownHintDirectory, new KeyVault());
        expectStorageLimit(() -> unknownHintStore.importStream(TASK_A, AttachmentBlobStore.newId(),
                        new RepeatingInputStream(11), 10, -1, () -> false),
                "actual streamed bytes cannot exceed remaining total quota when provider size is unknown");
        check(emptyDirectory(unknownHintDirectory), "unknown-size rejection removes all staged bytes");

        File cancelDirectory = new File(root, "cancelled");
        AttachmentBlobStore cancelStore = new AttachmentBlobStore(cancelDirectory, new KeyVault());
        String cancelId = AttachmentBlobStore.newId();
        AtomicInteger checks = new AtomicInteger();
        expectCancelled(() -> cancelStore.importStream(TASK_A, cancelId, new RepeatingInputStream(1024 * 1024),
                        AttachmentLogic.MAX_TOTAL_BYTES, -1, () -> checks.incrementAndGet() > 2),
                "user cancellation interrupts an active stream before commit");
        check(emptyDirectory(cancelDirectory), "cancelled import leaves neither payload nor staging file");

        File preflightDirectory = new File(root, "low-space-preflight");
        AttachmentBlobStore preflightStore = new AttachmentBlobStore(preflightDirectory, new KeyVault(),
                directory -> 1);
        expectStorageSpace(() -> preflightStore.importStream(TASK_A, AttachmentBlobStore.newId(),
                        new ByteArrayInputStream(new byte[] { 1 }), AttachmentLogic.MAX_TOTAL_BYTES, 1,
                        () -> false), "low free space is rejected before import starts");
        check(emptyDirectory(preflightDirectory), "low-space preflight creates no staging file");

        File midCopyDirectory = new File(root, "low-space-mid-copy");
        AtomicInteger spaceChecks = new AtomicInteger();
        AttachmentBlobStore midCopyStore = new AttachmentBlobStore(midCopyDirectory, new KeyVault(),
                directory -> spaceChecks.incrementAndGet() == 1 ? Long.MAX_VALUE : 0);
        expectStorageSpace(() -> midCopyStore.importStream(TASK_A, AttachmentBlobStore.newId(),
                        new RepeatingInputStream(40_000), AttachmentLogic.MAX_TOTAL_BYTES, -1,
                        () -> false), "space exhausted during copy aborts before commit");
        check(emptyDirectory(midCopyDirectory), "mid-copy low-space failure cleans up encrypted staging");
    }

    private static void corruptedPayloadFailsAuthentication(File root) throws Exception {
        File directory = new File(root, "corrupt");
        AttachmentBlobStore store = new AttachmentBlobStore(directory, new KeyVault());
        String id = AttachmentBlobStore.newId();
        byte[] plaintext = new byte[1024 * 1024];
        Arrays.fill(plaintext, (byte) 0x53);
        store.importStream(TASK_A, id, new ByteArrayInputStream(plaintext));
        File file = new File(directory, id + ".enc");
        try (RandomAccessFile bytes = new RandomAccessFile(file, "rw")) {
            bytes.seek(bytes.length() - 1);
            int last = bytes.read();
            bytes.seek(bytes.length() - 1);
            bytes.write(last ^ 1);
        }
        AtomicInteger plaintextReleased = new AtomicInteger();
        boolean rejected = false;
        try (InputStream input = store.openInput(TASK_A, id)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0) plaintextReleased.addAndGet(count);
            }
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, "modified attachment ciphertext fails AES-GCM authentication");
        check(plaintextReleased.get() == 0,
                "a tampered multi-chunk GCM payload emits no plaintext before authentication fails");
    }

    private static void verifyReadableAuthenticatesOrFailsClosed(File root) throws Exception {
        File directory = new File(root, "verify-readable");
        AttachmentBlobStore store = new AttachmentBlobStore(directory, new KeyVault());
        String id = AttachmentBlobStore.newId();
        byte[] plaintext = new byte[64 * 1024 + 7];
        Arrays.fill(plaintext, (byte) 0x41);
        long written = store.importStream(TASK_A, id, new ByteArrayInputStream(plaintext));
        check(store.verifyReadable(TASK_A, id) == written,
                "a healthy payload authenticates to its exact byte count");
        check(store.isReadable(TASK_A, id), "a healthy payload is reported readable");

        // Flip one byte inside the GCM tag: the payload still exists on disk, so an existence-only
        // check would pass, but authenticated verification must fail closed.
        File file = new File(directory, id + ".enc");
        try (RandomAccessFile bytes = new RandomAccessFile(file, "rw")) {
            bytes.seek(bytes.length() - 3);
            int value = bytes.read();
            bytes.seek(bytes.length() - 3);
            bytes.write(value ^ 0x20);
        }
        check(store.exists(id), "the tampered payload still exists, so existence alone is not integrity");
        expectIOException(() -> store.verifyReadable(TASK_A, id),
                "a present-but-corrupt payload fails authenticated verification");
        check(!store.isReadable(TASK_A, id), "the fail-closed readable check reports a corrupt payload");

        // A truncated payload is likewise rejected rather than silently treated as stored.
        String truncatedId = AttachmentBlobStore.newId();
        store.importStream(TASK_A, truncatedId, new ByteArrayInputStream(plaintext));
        File truncated = new File(directory, truncatedId + ".enc");
        try (RandomAccessFile bytes = new RandomAccessFile(truncated, "rw")) {
            bytes.setLength(bytes.length() - 20);
        }
        check(store.exists(truncatedId), "the truncated payload still exists");
        expectIOException(() -> store.verifyReadable(TASK_A, truncatedId),
                "a truncated payload fails authenticated verification");

        // A payload must not verify under a different owning task (AAD binds task + id).
        expectIOException(() -> store.verifyReadable(TASK_B, id),
                "a payload cannot be verified under a different owning task");
    }

    private static void payloadSubstitutionAcrossAttachmentOrTaskFailsAuthentication(File root) throws Exception {
        File directory = new File(root, "associated-data");
        AttachmentBlobStore store = new AttachmentBlobStore(directory, new KeyVault());
        String sourceId = AttachmentBlobStore.newId();
        String substitutedId = AttachmentBlobStore.newId();
        byte[] plaintext = new byte[1024 * 1024];
        Arrays.fill(plaintext, (byte) 0x2A);
        store.importStream(TASK_A, sourceId, new ByteArrayInputStream(plaintext));

        File original = new File(directory, sourceId + ".enc");
        File substituted = new File(directory, substitutedId + ".enc");
        Files.copy(original.toPath(), substituted.toPath());
        expectAuthenticationFailureWithoutPlaintext(() -> store.openInput(TASK_A, substitutedId),
                "a valid encrypted payload copied under another attachment ID fails authentication");
        expectAuthenticationFailureWithoutPlaintext(() -> store.openInput(TASK_B, sourceId),
                "a valid encrypted payload opened under another task ID fails authentication");
    }

    private static void expectAuthenticationFailureWithoutPlaintext(InputOperation operation, String message)
            throws Exception {
        AtomicInteger plaintextReleased = new AtomicInteger();
        boolean rejected = false;
        try (InputStream input = operation.open()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0) plaintextReleased.addAndGet(count);
            }
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, message);
        check(plaintextReleased.get() == 0, "failed associated-data authentication releases no plaintext");
    }

    private static boolean emptyDirectory(File directory) {
        File[] files = directory.listFiles();
        return files != null && files.length == 0;
    }

    private static final class RepeatingInputStream extends InputStream {
        private long remaining;
        RepeatingInputStream(long count) { remaining = count; }
        @Override public int read() {
            if (remaining <= 0) return -1;
            remaining--;
            return 65;
        }
        @Override public int read(byte[] buffer, int offset, int length) {
            if (remaining <= 0) return -1;
            int count = (int) Math.min(remaining, length);
            Arrays.fill(buffer, offset, offset + count, (byte) 65);
            remaining -= count;
            return count;
        }
    }

    private static final class KeyVault implements AttachmentBlobStore.KeyAccess {
        private SecretKey key;
        @Override public SecretKey loadExistingKey() throws IOException {
            if (key == null) throw new IOException("simulated missing attachment key");
            return key;
        }
        @Override public SecretKey createKeyForNewStore() throws IOException {
            if (key != null) return key;
            try {
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(256);
                key = generator.generateKey();
                return key;
            } catch (Exception exception) {
                throw new IOException("test key generation failed", exception);
            }
        }
    }

    private interface IoOperation { void run() throws Exception; }

    private interface InputOperation { InputStream open() throws IOException; }

    private static void expectIOException(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (IOException expected) { }
    }

    private static void expectFileLimit(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (AttachmentBlobStore.FileLimitException expected) { }
    }

    private static void expectStorageLimit(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (AttachmentBlobStore.StorageLimitException expected) { }
    }

    private static void expectCancelled(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (AttachmentBlobStore.CancelledException expected) { }
    }

    private static void expectStorageSpace(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (AttachmentBlobStore.StorageSpaceException expected) { }
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

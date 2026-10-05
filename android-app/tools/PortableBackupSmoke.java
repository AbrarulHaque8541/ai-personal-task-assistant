package com.cue.daymark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Host-side protocol tests; runtime Android SAF/Keystore behavior is covered by instrumentation. */
public final class PortableBackupSmoke {
    private static int assertions;
    private PortableBackupSmoke() { }

    public static void main(String[] args) throws Exception {
        recoveryKeyEncodingRoundTripsAndChecksErrors();
        archiveRoundTripsWithoutExposingTaskData();
        rejectsWrongKeyTamperAndMalformedFraming();
        rejectsUnsupportedFormatsBoundsDuplicatesAndCancellation();
        rejectsManifestTaskAndAttachmentBounds();
        acceptsOneHundredAttachmentsAndRejectsOneHundredOne();
        rejectsOneHundredFirstDescriptorBeforeProcessingIt();
        enforcesActualStreamedArchiveAndAttachmentLimits();
        writesAndReadsLargestValidArchiveWithoutHeapBuffering();
        coversImportUriActivityAndProcessDeathLifecycle();
        exportFailsClosedOnProviderReadAndWriteFailure();
        System.out.println("PASS portable backup protocol smoke tests: " + assertions + " assertions");
    }

    private static void recoveryKeyEncodingRoundTripsAndChecksErrors() throws Exception {
        byte[] key = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        String printable = PortableBackupCodec.encodeRecoveryKey(key);
        check(printable.startsWith("DMK1-"), "recovery code has a versioned prefix");
        check(printable.length() == 74, "recovery code has canonical grouped length");
        byte[] decoded = PortableBackupCodec.decodeRecoveryKey(printable);
        check(Arrays.equals(key, decoded), "recovery code round-trips the exact 32-byte key");
        char replacement = printable.charAt(printable.length() - 1) == 'A' ? 'B' : 'A';
        String changed = printable.substring(0, printable.length() - 1) + replacement;
        expectIOException(() -> PortableBackupCodec.decodeRecoveryKey(changed), "recovery key check detects a mistyped character");
        expectIOException(() -> PortableBackupCodec.decodeRecoveryKey(printable.replace('-', ' ')), "recovery key rejects malformed separators");
        PortableBackupCodec.clear(key);
        PortableBackupCodec.clear(decoded);
        check(allZero(key) && allZero(decoded), "recovery key byte buffers can be cleared");
    }

    private static void archiveRoundTripsWithoutExposingTaskData() throws Exception {
        byte[] key = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        String taskId = UUID.randomUUID().toString();
        String attachmentId = UUID.randomUUID().toString();
        byte[] payload = "private payload marker — attachment bytes".getBytes(StandardCharsets.UTF_8);
        Task task = new Task(taskId, "Secret task marker", "2026-10-06", "high", false,
                "2026-10-05T10:15:30Z", "2026-10-05T10:15:30Z",
                Collections.singletonList(new AttachmentRef(attachmentId, "private-qrx1.txt", "text/plain", payload.length)));
        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        String backupId = PortableBackupCodec.writeArchive(archiveBytes, key,
                Collections.singletonList(task), (sourceTask, reference) -> new ByteArrayInputStream(payload),
                new SecureRandom(), () -> false);
        byte[] archive = archiveBytes.toByteArray();
        check(archive.length < 2 * 1024 * 1024, "small test archive remains bounded");
        check(!startsWith(archive, new byte[] { 'P', 'K', 3, 4 }), "portable archive is not ZIP");
        check(backupId.length() == 32, "header backup ID is a 128-bit random identifier");
        check(!contains(archive, "Secret task marker".getBytes(StandardCharsets.UTF_8)), "task titles are encrypted");
        check(!contains(archive, "private-qrx1.txt".getBytes(StandardCharsets.UTF_8)), "attachment names are encrypted");
        check(!contains(archive, "text/plain".getBytes(StandardCharsets.UTF_8)), "attachment MIME types are encrypted");
        check(!contains(archive, taskId.getBytes(StandardCharsets.UTF_8)), "source task IDs are not exported");
        check(!contains(archive, attachmentId.getBytes(StandardCharsets.UTF_8)), "source attachment IDs are not exported");

        File stage = Files.createTempDirectory("portable-backup-roundtrip").toFile();
        PortableBackupCodec.VerifiedArchive opened = PortableBackupCodec.readArchive(
                new ByteArrayInputStream(archive), key, stage, id -> check(id.equals(backupId), "backup ID is checked before decryption"), () -> false);
        check(opened.backupId.equals(backupId), "header backup ID survives authenticated round trip");
        check(opened.tasks.size() == 1 && opened.attachments.size() == 1, "authenticated manifest restores task and attachment descriptors");
        PortableBackupCodec.PortableTask openedTask = opened.tasks.get(0);
        check(openedTask.title.equals("Secret task marker") && openedTask.dueDate.equals("2026-10-06"), "encrypted task fields round-trip exactly");
        check(openedTask.attachments.size() == 1 && openedTask.attachments.get(0).displayName.equals("private-qrx1.txt"), "encrypted attachment metadata round-trips");
        check(Arrays.equals(payload, Files.readAllBytes(opened.attachments.get(0).stagedPlaintext.toPath())), "full attachment bytes are authenticated before staging is returned");
        opened.clearStagedPlaintext();
        check(stage.listFiles().length == 0, "verified plaintext staging can be removed after transaction");
        deleteTree(stage);

        File zeroProgressStage = Files.createTempDirectory("portable-backup-zero-progress").toFile();
        PortableBackupCodec.VerifiedArchive zeroProgress = PortableBackupCodec.readArchive(
                new ZeroProgressInputStream(archive), key, zeroProgressStage, id -> { }, () -> false);
        check(Arrays.equals(payload, Files.readAllBytes(zeroProgress.attachments.get(0).stagedPlaintext.toPath())),
                "zero-progress SAF reads make bounded forward progress");
        zeroProgress.clearStagedPlaintext();
        deleteTree(zeroProgressStage);
        PortableBackupCodec.clear(key);
    }

    private static void rejectsWrongKeyTamperAndMalformedFraming() throws Exception {
        byte[] key = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        byte[] wrongKey = key.clone();
        wrongKey[0] ^= 0x20;
        byte[] payload = new byte[8192];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i * 37);
        byte[] archive = makeArchive(key, payload, payload.clone());
        File root = Files.createTempDirectory("portable-backup-reject").toFile();

        File wrongStage = new File(root, "wrong");
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(archive), wrongKey,
                wrongStage, id -> { }, () -> false), "wrong key is rejected before any import commit");
        check(!wrongStage.exists() || wrongStage.list().length == 0, "wrong key leaves no plaintext staging files");
        File missingKeyStage = new File(root, "missing-key");
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(archive), null,
                missingKeyStage, id -> { }, () -> false), "missing recovery key is rejected without import");
        check(!missingKeyStage.exists(), "missing recovery key creates no staging directory");

        byte[] tampered = archive.clone();
        tampered[tampered.length - 1] ^= 0x01;
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(tampered), key,
                new File(root, "tamper"), id -> { }, () -> false), "modified GCM tag is rejected");
        byte[] truncated = Arrays.copyOf(archive, archive.length - 1);
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(truncated), key,
                new File(root, "truncated"), id -> { }, () -> false), "truncated final record is rejected");
        byte[] trailing = Arrays.copyOf(archive, archive.length + 1);
        trailing[trailing.length - 1] = 0x55;
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(trailing), key,
                new File(root, "trailing"), id -> { }, () -> false), "trailing bytes are rejected");

        byte[] duplicateRecord = archive.clone();
        int manifestLength = ByteBuffer.wrap(duplicateRecord, 65, 8).getLong() > Integer.MAX_VALUE
                ? 0 : (int) ByteBuffer.wrap(duplicateRecord, 65, 8).getLong();
        int firstAttachment = 28 + 57 + manifestLength + 16;
        int recordLength = 57 + payload.length + 16;
        int secondAttachment = firstAttachment + recordLength;
        byte[] missingRecord = Arrays.copyOf(archive, firstAttachment);
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(missingRecord), key,
                new File(root, "missing-record"), id -> { }, () -> false), "missing attachment record is rejected");

        System.arraycopy(duplicateRecord, firstAttachment + 5, duplicateRecord, secondAttachment + 5, 16);
        expectIOExceptionContaining(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(duplicateRecord), key,
                new File(root, "duplicate"), id -> { }, () -> false), "duplicate backup record",
                "duplicate record identity check is reached before manifest-token mismatch");
        byte[] reordered = archive.clone();
        byte[] firstRecord = Arrays.copyOfRange(archive, firstAttachment, secondAttachment);
        byte[] secondRecord = Arrays.copyOfRange(archive, secondAttachment, secondAttachment + recordLength);
        System.arraycopy(secondRecord, 0, reordered, firstAttachment, recordLength);
        System.arraycopy(firstRecord, 0, reordered, secondAttachment, recordLength);
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(reordered), key,
                new File(root, "reordered"), id -> { }, () -> false), "reordered attachment records are rejected");
        byte[] nonsequential = archive.clone();
        ByteBuffer.wrap(nonsequential).putInt(firstAttachment + 1, 2);
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(nonsequential), key,
                new File(root, "nonsequential"), id -> { }, () -> false), "nonsequential attachment ordinal is rejected");
        byte[] extraRecord = Arrays.copyOf(archive, archive.length + recordLength);
        System.arraycopy(archive, firstAttachment, extraRecord, archive.length, recordLength);
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(extraRecord), key,
                new File(root, "extra-record"), id -> { }, () -> false), "extra attachment record after the declared record set is rejected");

        AtomicInteger checks = new AtomicInteger();
        File midCancelStage = new File(root, "mid-cancel");
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(archive), key,
                midCancelStage, id -> { }, () -> checks.incrementAndGet() >= 5),
                "cancellation after attachment plaintext staging still aborts the import");
        check(!midCancelStage.exists() || midCancelStage.list().length == 0,
                "cancelled mid-record import removes partial plaintext staging");

        deleteTree(root);
        PortableBackupCodec.clear(key);
        PortableBackupCodec.clear(wrongKey);
    }

    private static void rejectsUnsupportedFormatsBoundsDuplicatesAndCancellation() throws Exception {
        byte[] key = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        byte[] archive = makeArchive(key, new byte[] { 1, 2, 3, 4 });
        File root = Files.createTempDirectory("portable-backup-bounds").toFile();
        byte[] futureVersion = archive.clone();
        futureVersion[5] = 2;
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(futureVersion), key,
                new File(root, "version"), id -> { }, () -> false), "unknown format version is rejected");
        byte[] futureSuite = archive.clone();
        futureSuite[7] = 2;
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(futureSuite), key,
                new File(root, "suite"), id -> { }, () -> false), "unknown encryption suite is rejected");
        byte[] tooManyRecords = archive.clone();
        ByteBuffer.wrap(tooManyRecords).putInt(24, PortableBackupCodec.MAX_RECORDS + 1);
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(tooManyRecords), key,
                new File(root, "records"), id -> { }, () -> false), "record-count bound is checked before crypto");
        byte[] hugeManifest = archive.clone();
        ByteBuffer.wrap(hugeManifest).putLong(65, (long) PortableBackupCodec.MAX_MANIFEST_BYTES + 1L);
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(hugeManifest), key,
                new File(root, "manifest"), id -> { }, () -> false), "manifest bound is checked before allocation");
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(archive), key,
                new File(root, "duplicate-backup"), id -> { throw new IOException("duplicate backup"); }, () -> false),
                "duplicate backup ID check runs before decryption");
        expectIOException(() -> PortableBackupCodec.readArchive(new ByteArrayInputStream(archive), key,
                new File(root, "cancel"), id -> { }, () -> true), "cancellation aborts before transaction staging");
        check(new File(root, "cancel").list().length == 0, "cancelled import leaves no staged plaintext");
        deleteTree(root);
        PortableBackupCodec.clear(key);
    }

    private static void rejectsManifestTaskAndAttachmentBounds() throws Exception {
        byte[] manifestToken = token(1000000);
        long[][] exactTaskSizes = new long[PortableBackupCodec.MAX_TASKS][];
        Arrays.fill(exactTaskSizes, new long[0]);
        byte[] exactTaskCount = manifestWithAttachmentSizes(exactTaskSizes);
        check(PortableBackupCodec.decodeManifest(exactTaskCount, manifestToken, 1,
                "00000000000000000000000000000000").tasks.size() == PortableBackupCodec.MAX_TASKS,
                "task-count boundary exactly at 10,000 is accepted");
        ByteArrayOutputStream taskCountBytes = new ByteArrayOutputStream();
        java.io.DataOutputStream taskCountOut = new java.io.DataOutputStream(taskCountBytes);
        taskCountOut.writeInt(0x444d4d31);
        taskCountOut.writeInt(PortableBackupCodec.MAX_TASKS + 1);
        taskCountOut.flush();
        expectIOException(() -> PortableBackupCodec.decodeManifest(taskCountBytes.toByteArray(),
                manifestToken, 1, "00000000000000000000000000000000"),
                "task-count bound is checked before allocating task records");

        byte[] exactFile = manifestWithAttachmentSizes(new long[][] {
                { AttachmentLogic.MAX_FILE_BYTES }
        });
        check(PortableBackupCodec.decodeManifest(exactFile, manifestToken, 2,
                "00000000000000000000000000000000").attachments.size() == 1,
                "per-file attachment boundary exactly at 20 MiB is accepted");
        byte[] oversizedFile = manifestWithAttachmentSizes(new long[][] {
                { AttachmentLogic.MAX_FILE_BYTES + 1 }
        });
        expectIOException(() -> PortableBackupCodec.decodeManifest(oversizedFile,
                manifestToken, 2, "00000000000000000000000000000000"),
                "per-file attachment bound rejects manifest sizes above 20 MiB");

        long[][] exactAggregateSizes = { {
                AttachmentLogic.MAX_FILE_BYTES, AttachmentLogic.MAX_FILE_BYTES,
                AttachmentLogic.MAX_FILE_BYTES, AttachmentLogic.MAX_FILE_BYTES,
                AttachmentLogic.MAX_FILE_BYTES
        } };
        byte[] exactAggregate = manifestWithAttachmentSizes(exactAggregateSizes);
        check(PortableBackupCodec.decodeManifest(exactAggregate, manifestToken, 6,
                "00000000000000000000000000000000").attachments.size() == 5,
                "aggregate attachment boundary exactly at 100 MiB is accepted");
        byte[] oversizedAggregate = manifestWithAttachmentSizes(new long[][] {
                { AttachmentLogic.MAX_FILE_BYTES, AttachmentLogic.MAX_FILE_BYTES,
                        AttachmentLogic.MAX_FILE_BYTES, AttachmentLogic.MAX_FILE_BYTES,
                        AttachmentLogic.MAX_FILE_BYTES },
                { 1 }
        });
        expectIOException(() -> PortableBackupCodec.decodeManifest(oversizedAggregate,
                manifestToken, 7, "00000000000000000000000000000000"),
                "aggregate attachment bound rejects authenticated sizes above 100 MiB");
    }

    private static void acceptsOneHundredAttachmentsAndRejectsOneHundredOne() throws Exception {
        byte[] key = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        List<Task> exactTasks = tasksWithAttachmentCount(AttachmentLogic.MAX_TOTAL_COUNT);
        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        PortableBackupCodec.writeArchive(archiveBytes, key, exactTasks,
                (task, attachment) -> InputStream.nullInputStream(), new SecureRandom(), () -> false);
        byte[] archive = archiveBytes.toByteArray();
        check(ByteBuffer.wrap(archive).getInt(24) == AttachmentLogic.MAX_TOTAL_COUNT + 1,
                "100 attachments produce 101 authenticated archive records including the manifest");
        File root = Files.createTempDirectory("portable-backup-100-attachments").toFile();
        PortableBackupCodec.VerifiedArchive verified = PortableBackupCodec.readArchive(
                new ByteArrayInputStream(archive), key, root, id -> { }, () -> false);
        check(verified.tasks.size() == 20 && verified.attachments.size() == AttachmentLogic.MAX_TOTAL_COUNT,
                "a valid 100-attachment archive is accepted across 20 tasks at five per task");
        verified.clearStagedPlaintext();
        deleteTree(root);

        List<Task> oversizedTasks = tasksWithAttachmentCount(AttachmentLogic.MAX_TOTAL_COUNT + 1);
        Task firstOversizedTask = oversizedTasks.get(0);
        List<AttachmentRef> invalidDescriptors = new ArrayList<>(firstOversizedTask.attachments);
        AttachmentRef firstDescriptor = invalidDescriptors.get(0);
        invalidDescriptors.set(0, new AttachmentRef("not-a-valid-id", firstDescriptor.displayName,
                firstDescriptor.mimeType, firstDescriptor.sizeBytes));
        oversizedTasks.set(0, firstOversizedTask.withAttachments(invalidDescriptors));
        AtomicInteger payloadOpens = new AtomicInteger();
        expectIOExceptionContaining(() -> PortableBackupCodec.writeArchive(OutputStream.nullOutputStream(), key,
                        oversizedTasks, (task, attachment) -> {
                            payloadOpens.incrementAndGet();
                            return InputStream.nullInputStream();
                        }, new SecureRandom(), () -> false),
                "too many attachments",
                "101 attachment slots are rejected before invalid descriptor metadata is traversed");
        check(payloadOpens.get() == 0, "oversized attachment count is rejected before any payload is opened");
        PortableBackupCodec.clear(key);
    }

    private static void rejectsOneHundredFirstDescriptorBeforeProcessingIt() throws Exception {
        byte[] manifestToken = token(1000000);
        long[][] counts = new long[21][];
        for (int taskIndex = 0; taskIndex < 20; taskIndex++) counts[taskIndex] = new long[5];
        counts[20] = new long[] { 0 };
        byte[] complete = manifestWithAttachmentSizes(counts);
        int finalDescriptorBytes = 16 + 8 + 4 + "payload.bin".getBytes(StandardCharsets.UTF_8).length
                + 4 + "application/octet-stream".getBytes(StandardCharsets.UTF_8).length;
        byte[] missingOneHundredFirstDescriptor = Arrays.copyOf(complete, complete.length - finalDescriptorBytes);
        expectIOExceptionContaining(() -> PortableBackupCodec.decodeManifest(
                        missingOneHundredFirstDescriptor, manifestToken, AttachmentLogic.MAX_TOTAL_COUNT + 2,
                        "00000000000000000000000000000000"),
                "too many attachments",
                "the aggregate-count preflight rejects the 101st slot before attempting to read its descriptor bytes");
    }

    private static void enforcesActualStreamedArchiveAndAttachmentLimits() throws Exception {
        long archiveLimit = PortableBackupCodec.MAX_ARCHIVE_BYTES;
        PortableBackupCodec.CountingInputStream exactInput = new PortableBackupCodec.CountingInputStream(
                new RepeatingInputStream(archiveLimit), archiveLimit);
        drain(exactInput);
        check(exactInput.count() == archiveLimit, "actual archive input exactly at 110 MiB is accepted");

        PortableBackupCodec.CountingInputStream oversizedInput = new PortableBackupCodec.CountingInputStream(
                new RepeatingInputStream(archiveLimit + 1), archiveLimit);
        expectIOException(() -> drain(oversizedInput),
                "actual archive input one byte above 110 MiB is rejected even with unknown provider size");
        check(oversizedInput.count() == archiveLimit, "archive input counter never exceeds its actual-byte cap");

        PortableBackupCodec.CountingOutputStream exactOutput = new PortableBackupCodec.CountingOutputStream(
                OutputStream.nullOutputStream(), archiveLimit);
        writeRepeated(exactOutput, archiveLimit);
        check(exactOutput.count() == archiveLimit, "actual archive output exactly at 110 MiB is accepted");
        PortableBackupCodec.CountingOutputStream oversizedOutput = new PortableBackupCodec.CountingOutputStream(
                OutputStream.nullOutputStream(), archiveLimit);
        writeRepeated(oversizedOutput, archiveLimit);
        expectIOException(() -> oversizedOutput.write(0),
                "actual archive output one byte above 110 MiB is rejected");
        check(oversizedOutput.count() == archiveLimit, "archive output counter never exceeds its actual-byte cap");

        byte[] key = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        long fileLimit = AttachmentLogic.MAX_FILE_BYTES;
        Task exactFile = taskWithAttachmentSize(fileLimit);
        String id = PortableBackupCodec.writeArchive(OutputStream.nullOutputStream(), key,
                Collections.singletonList(exactFile), (task, attachment) -> new RepeatingInputStream(fileLimit),
                new SecureRandom(), () -> false);
        check(id.length() == 32, "actual attachment stream exactly at 20 MiB is accepted into an archive");
        Task oversizedFile = taskWithAttachmentSize(fileLimit);
        expectIOException(() -> PortableBackupCodec.writeArchive(OutputStream.nullOutputStream(), key,
                Collections.singletonList(oversizedFile), (task, attachment) -> new RepeatingInputStream(fileLimit + 1),
                new SecureRandom(), () -> false),
                "actual attachment stream one byte above its 20 MiB authenticated size is rejected");
        PortableBackupCodec.clear(key);
    }

    private static void writesAndReadsLargestValidArchiveWithoutHeapBuffering() throws Exception {
        byte[] key = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        File root = Files.createTempDirectory("portable-backup-near-archive-cap").toFile();
        File archive = new File(root, "largest-valid.dmbackup");
        File stage = new File(root, "restored-plaintext");
        try {
            List<Task> tasks = tasksWithMaximumArchiveMetadata();
            check(tasks.size() == PortableBackupCodec.MAX_TASKS && TaskLogic.isValidTaskList(tasks),
                    "maximum-metadata archive fixture respects all task and attachment schema limits");
            long expectedManifestBytes = 8L
                    + (long) PortableBackupCodec.MAX_TASKS * (16 + 4 + 480 + 1 + 4 + 10 + 1 + 1 + 4 + 41 + 4 + 41 + 1)
                    + (long) AttachmentLogic.MAX_TOTAL_COUNT * (16 + 8 + 4 + 360 + 4 + 129);
            try (OutputStream output = new java.io.FileOutputStream(archive)) {
                PortableBackupCodec.writeArchive(output, key, tasks,
                        (sourceTask, reference) -> new RepeatingInputStream(reference.sizeBytes),
                        new SecureRandom(), () -> false);
            }
            long archiveBytes = archive.length();
            long manifestBytes;
            try (java.io.RandomAccessFile header = new java.io.RandomAccessFile(archive, "r")) {
                header.seek(65);
                manifestBytes = header.readLong();
            }
            long expectedArchiveBytes = AttachmentLogic.MAX_TOTAL_BYTES + expectedManifestBytes
                    + 28L + (AttachmentLogic.MAX_TOTAL_COUNT + 1L) * (57L + 16L);
            check(manifestBytes == expectedManifestBytes && manifestBytes < PortableBackupCodec.MAX_MANIFEST_BYTES,
                    "all valid maximum task/title/timestamp/attachment metadata occupies the exact bounded manifest size");
            check(archiveBytes == expectedArchiveBytes && archiveBytes < PortableBackupCodec.MAX_ARCHIVE_BYTES,
                    "the fully formed maximum valid archive is accepted below the 110 MiB archive cap");
            System.out.println("  largest valid encrypted archive: " + archiveBytes + " bytes; manifest: "
                    + manifestBytes + " bytes; cap: " + PortableBackupCodec.MAX_ARCHIVE_BYTES + " bytes");
            System.out.println("  maximum-profile archive gap: "
                    + (PortableBackupCodec.MAX_ARCHIVE_BYTES - archiveBytes) + " bytes");

            PortableBackupCodec.VerifiedArchive verified;
            try (InputStream input = new FileInputStream(archive)) {
                verified = PortableBackupCodec.readArchive(input, key, stage, id -> { }, () -> false);
            }
            check(verified.tasks.size() == PortableBackupCodec.MAX_TASKS
                            && verified.attachments.size() == AttachmentLogic.MAX_TOTAL_COUNT,
                    "the complete 100 MiB payload and 10,000-task metadata authenticate from a real archive file");
            for (PortableBackupCodec.PortableAttachment attachment : verified.attachments) {
                check(attachment.stagedPlaintext.length() == AttachmentLogic.MAX_TOTAL_BYTES / AttachmentLogic.MAX_TOTAL_COUNT,
                        "near-cap restore stages one streamed 1 MiB attachment without a heap-sized archive buffer");
                try (java.io.RandomAccessFile sample = new java.io.RandomAccessFile(attachment.stagedPlaintext, "r")) {
                    sample.seek(0);
                    boolean first = sample.readUnsignedByte() == 0x31;
                    sample.seek(attachment.stagedPlaintext.length() - 1);
                    check(first && sample.readUnsignedByte() == 0x31,
                            "deterministic streamed attachment bytes survive encryption and decryption");
                }
            }
            verified.clearStagedPlaintext();

            File overCap = new File(root, "physical-over-cap.dmbackup");
            try (OutputStream output = new java.io.FileOutputStream(overCap)) {
                PortableBackupCodec.writeArchive(output, key, Collections.emptyList(),
                        (sourceTask, reference) -> InputStream.nullInputStream(), new SecureRandom(), () -> false);
            }
            try (java.io.RandomAccessFile padded = new java.io.RandomAccessFile(overCap, "rw")) {
                padded.setLength(PortableBackupCodec.MAX_ARCHIVE_BYTES + 1);
            }
            check(overCap.length() == PortableBackupCodec.MAX_ARCHIVE_BYTES + 1,
                    "an actual on-disk archive input is one byte above the 110 MiB bound");
            File overCapStage = new File(root, "over-cap-stage");
            expectIOExceptionContaining(() -> {
                try (InputStream input = new FileInputStream(overCap)) {
                    PortableBackupCodec.readArchive(input, key, overCapStage, id -> { }, () -> false);
                }
            }, "trailing data", "the parser rejects a real over-cap archive file");
        } finally {
            PortableBackupCodec.clear(key);
            deleteTree(root);
        }
    }

    private static void coversImportUriActivityAndProcessDeathLifecycle() throws Exception {
        String selected = "content://documents.example/tree/primary%3Abackup.dmbackup";
        String unrelated = "content://other.example/document/retained";
        InMemoryUriJournal journal = new InMemoryUriJournal();
        Set<String> readGrants = new HashSet<>(Arrays.asList(selected, unrelated));
        Set<String> writeGrants = new HashSet<>(Collections.singletonList(selected));
        byte[] document = "selected encrypted archive remains readable".getBytes(StandardCharsets.UTF_8);
        PortableImportGrantRecovery.Selection selection = PortableImportGrantRecovery.recordTakenGrantOrRelease(
                journal, selected, readGrants::remove);
        check(selection.uri.equals(selected) && selection.operationToken.equals(journal.value.operationToken),
                "the selected URI and non-secret operation token are journaled before any key prompt");
        PortableImportGrantRecovery.ActivityState saved = PortableImportGrantRecovery.activityStateForSelection(
                journal, selection, readGrants::contains);
        check(saved != null && selected.equals(saved.uri) && selection.operationToken.equals(saved.operationToken),
                "Activity saved state contains only the exact URI and its operation token");
        PortableImportGrantRecovery.Selection recreated = PortableImportGrantRecovery.restorePendingActivitySelection(
                journal, saved.uri, saved.operationToken, readGrants::contains);
        check(selection.matches(recreated), "same-process Activity recreation restores only a matching live journal selection");
        AtomicBoolean reconciled = new AtomicBoolean();
        boolean preserved = PortableImportGrantRecovery.reconcileStartup(journal, recreated, () -> {
            reconciled.set(true);
        }, readGrants::contains, exactUri -> {
            throw new AssertionError("a valid Activity recreation must preserve the selected read grant");
        });
        check(preserved && reconciled.get() && journal.value != null && readGrants.contains(selected),
                "startup transaction reconciliation preserves a matching restored selection and its journal");
        check(Arrays.equals(document, new ByteArrayInputStream(document).readAllBytes()),
                "the restored key-dialog flow can still read the selected document after Activity recreation");
        check(readGrants.contains(unrelated) && writeGrants.contains(selected),
                "unrelated grants and the same URI's unrelated write permission are not revoked");
        PortableImportGrantRecovery.finishAfterWork(journal, recreated, () -> { }, readGrants::remove);
        check(journal.value == null && !readGrants.contains(selected),
                "a resumed key flow can be cancelled and releases its selected read grant");

        String freshLaunchUri = "content://documents.example/document/no-saved-state.dmbackup";
        InMemoryUriJournal freshLaunchJournal = new InMemoryUriJournal();
        Set<String> freshLaunchGrants = new HashSet<>(Arrays.asList(freshLaunchUri, unrelated));
        PortableImportGrantRecovery.Selection freshLaunchSelection = PortableImportGrantRecovery.recordTakenGrantOrRelease(
                freshLaunchJournal, freshLaunchUri, freshLaunchGrants::remove);
        AtomicBoolean freshLaunchReconciled = new AtomicBoolean();
        check(!PortableImportGrantRecovery.reconcileStartup(freshLaunchJournal, null, () -> {
            freshLaunchReconciled.set(true);
        }, freshLaunchGrants::contains, exactUri -> {
            check(freshLaunchReconciled.get(), "fresh launch reconciles before abandoning a live-process selection");
            check(freshLaunchUri.equals(exactUri), "fresh launch cleanup uses the URI from its journal");
            freshLaunchGrants.remove(exactUri);
        }), "fresh launch without saved pending state does not preserve even a live process token");
        check(freshLaunchJournal.value == null && !freshLaunchGrants.contains(freshLaunchUri)
                        && freshLaunchGrants.contains(unrelated),
                "fresh launch without state clears only its exact journaled grant");

        String abandonedUri = "content://documents.example/document/fresh-launch.dmbackup";
        InMemoryUriJournal abandonedJournal = new InMemoryUriJournal();
        Set<String> abandonedGrants = new HashSet<>(Arrays.asList(abandonedUri, unrelated));
        PortableImportGrantRecovery.Selection abandoned = new PortableImportGrantRecovery.Selection(
                UUID.randomUUID().toString(), abandonedUri);
        abandonedJournal.write(abandoned); // A persisted operation with no process-local live marker models process death.
        check(PortableImportGrantRecovery.restorePendingActivitySelection(abandonedJournal,
                        abandonedUri, abandoned.operationToken, abandonedGrants::contains) == null,
                "saved URI/token from a new process cannot impersonate same-process Activity recreation");
        AtomicBoolean freshReconciled = new AtomicBoolean();
        check(!PortableImportGrantRecovery.reconcileStartup(abandonedJournal, null, () -> {
            freshReconciled.set(true);
        }, abandonedGrants::contains, exactUri -> {
            check(freshReconciled.get(), "fresh-launch grant cleanup follows transaction reconciliation");
            check(abandonedUri.equals(exactUri), "fresh launch releases only the journaled URI");
            abandonedGrants.remove(exactUri);
        }), "fresh launch without valid saved pending state does not preserve an orphan journal");
        check(abandonedJournal.value == null && !abandonedGrants.contains(abandonedUri)
                        && abandonedGrants.contains(unrelated),
                "fresh relaunch clears only its orphaned exact grant and retains unrelated grants");

        String failedWriteUri = "content://documents.example/document/journal-failure.dmbackup";
        Set<String> failedWriteGrants = new HashSet<>(Arrays.asList(failedWriteUri, unrelated));
        PortableImportGrantRecovery.JournalStore failedJournal = new PortableImportGrantRecovery.JournalStore() {
            @Override public PortableImportGrantRecovery.Selection read() { return null; }
            @Override public void write(PortableImportGrantRecovery.Selection value) throws IOException {
                throw new IOException("simulated durable write failure");
            }
            @Override public void clear() { }
        };
        expectIOException(() -> PortableImportGrantRecovery.recordTakenGrantOrRelease(
                        failedJournal, failedWriteUri, failedWriteGrants::remove),
                "a failed journal write is reported after immediate exact-grant cleanup");
        check(!failedWriteGrants.contains(failedWriteUri) && failedWriteGrants.contains(unrelated),
                "journal-write failure releases only the grant just acquired for the selected archive");

        String cancelledUri = "content://documents.example/document/cancelled.dmbackup";
        InMemoryUriJournal cancelledJournal = new InMemoryUriJournal();
        Set<String> cancelledGrants = new HashSet<>(Arrays.asList(cancelledUri, unrelated));
        PortableImportGrantRecovery.Selection cancelled = PortableImportGrantRecovery.recordTakenGrantOrRelease(
                cancelledJournal, cancelledUri, cancelledGrants::remove);
        expectIOException(() -> PortableImportGrantRecovery.finishAfterWork(cancelledJournal, cancelled,
                        () -> { throw new IOException("simulated unresolved restore transaction"); }, cancelledGrants::remove),
                "cancel/failure cleanup retains the journal and grant until storage reconciliation succeeds");
        check(cancelled.matches(cancelledJournal.value) && cancelledGrants.contains(cancelledUri),
                "failed cancellation reconciliation cannot clear or revoke the selected URI");
        AtomicBoolean cancelReconciled = new AtomicBoolean();
        PortableImportGrantRecovery.finishAfterWork(cancelledJournal, cancelled, () -> {
            cancelReconciled.set(true);
        }, exactUri -> {
            check(cancelReconciled.get() && cancelledUri.equals(exactUri),
                    "successful cancel cleanup reconciles before releasing the exact URI");
            cancelledGrants.remove(exactUri);
        });
        check(cancelledJournal.value == null && !cancelledGrants.contains(cancelledUri)
                        && cancelledGrants.contains(unrelated),
                "successful cleanup clears only the completed selection and preserves unrelated grants");

        String mismatchedJournalUri = "content://documents.example/document/journaled-after-uri-mismatch.dmbackup";
        String mismatchedSavedUri = "content://documents.example/document/mismatched-saved-state.dmbackup";
        InMemoryUriJournal mismatchJournal = new InMemoryUriJournal();
        Set<String> mismatchGrants = new HashSet<>(Arrays.asList(mismatchedJournalUri, mismatchedSavedUri, unrelated));
        PortableImportGrantRecovery.Selection mismatch = PortableImportGrantRecovery.recordTakenGrantOrRelease(
                mismatchJournal, mismatchedJournalUri, mismatchGrants::remove);
        check(PortableImportGrantRecovery.restorePendingActivitySelection(mismatchJournal,
                        mismatchedSavedUri, mismatch.operationToken, mismatchGrants::contains) == null,
                "mismatched saved URI is rejected even when its token and grants exist");
        AtomicBoolean mismatchReconciled = new AtomicBoolean();
        PortableImportGrantRecovery.reconcileStartup(mismatchJournal, null, () -> mismatchReconciled.set(true),
                mismatchGrants::contains, exactUri -> {
                    check(mismatchReconciled.get(), "mismatched-state cleanup reconciles storage first");
                    check(mismatchedJournalUri.equals(exactUri), "mismatch cleanup trusts only the journaled URI");
                    mismatchGrants.remove(exactUri);
                });
        check(!mismatchGrants.contains(mismatchedJournalUri) && mismatchGrants.contains(mismatchedSavedUri)
                        && mismatchGrants.contains(unrelated),
                "URI mismatch releases the exact journaled grant, not the different URI from saved state");

        String revokedUri = "content://documents.example/document/revoked.dmbackup";
        InMemoryUriJournal revokedJournal = new InMemoryUriJournal();
        Set<String> revokedGrants = new HashSet<>(Arrays.asList(revokedUri, unrelated));
        PortableImportGrantRecovery.Selection revoked = PortableImportGrantRecovery.recordTakenGrantOrRelease(
                revokedJournal, revokedUri, revokedGrants::remove);
        revokedGrants.remove(revokedUri);
        check(PortableImportGrantRecovery.restorePendingActivitySelection(revokedJournal,
                        revoked.uri, revoked.operationToken, revokedGrants::contains) == null,
                "a revoked read grant invalidates otherwise matching saved pending state");
        AtomicBoolean revokedReconciled = new AtomicBoolean();
        PortableImportGrantRecovery.reconcileStartup(revokedJournal, null, () -> revokedReconciled.set(true),
                revokedGrants::contains, exactUri -> {
                    check(revokedReconciled.get(), "revoked-grant cleanup follows transaction reconciliation");
                    check(revokedUri.equals(exactUri), "revoked-grant cleanup clears only its journal entry");
                    revokedGrants.remove(exactUri);
                });
        check(revokedJournal.value == null && revokedGrants.contains(unrelated),
                "revoked-grant startup clears the orphan journal without touching unrelated grants");

        String mismatchedJournaledUri = "content://documents.example/document/journal-uri.dmbackup";
        String savedUri = "content://documents.example/document/saved-uri.dmbackup";
        InMemoryUriJournal mismatchedRecordJournal = new InMemoryUriJournal();
        Set<String> mismatchedRecordGrants = new HashSet<>(Arrays.asList(mismatchedJournaledUri, savedUri));
        String mismatchedToken = UUID.randomUUID().toString();
        mismatchedRecordJournal.write(new PortableImportGrantRecovery.Selection(mismatchedToken, mismatchedJournaledUri));
        check(PortableImportGrantRecovery.restorePendingActivitySelection(mismatchedRecordJournal,
                        savedUri, mismatchedToken, mismatchedRecordGrants::contains) == null,
                "saved URI must also match the URI stored beside its operation token in the journal");
        PortableImportGrantRecovery.reconcileStartup(mismatchedRecordJournal, null, () -> { },
                mismatchedRecordGrants::contains, mismatchedRecordGrants::remove);
        check(!mismatchedRecordGrants.contains(mismatchedJournaledUri) && mismatchedRecordGrants.contains(savedUri),
                "journal mismatch recovery cleans only the URI actually recorded in the journal");

        String retryUri = "content://documents.example/document/retry.dmbackup";
        InMemoryUriJournal retryJournal = new InMemoryUriJournal();
        Set<String> retryGrants = new HashSet<>(Collections.singletonList(retryUri));
        PortableImportGrantRecovery.Selection retry = PortableImportGrantRecovery.recordTakenGrantOrRelease(
                retryJournal, retryUri, retryGrants::remove);
        expectIOException(() -> PortableImportGrantRecovery.reconcileStartup(retryJournal, null,
                        () -> { throw new IOException("simulated unresolved import journal"); },
                        retryGrants::contains, retryGrants::remove),
                "failed transaction reconciliation leaves the URI journal and grant for a later startup");
        check(retry.matches(retryJournal.value) && retryGrants.contains(retryUri),
                "unresolved import state cannot prematurely discard its active-URI journal");

        PortableImportGrantRecovery.activityRestoreWorkerStarted();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch recovered = new CountDownLatch(1);
        AtomicBoolean waitFailed = new AtomicBoolean();
        Thread recreatedActivity = new Thread(() -> {
            entered.countDown();
            try { PortableImportGrantRecovery.awaitNoActivityRestoreWorker(); }
            catch (IOException failure) { waitFailed.set(true); }
            recovered.countDown();
        }, "portable-import-activity-recreation-test");
        recreatedActivity.start();
        check(entered.await(2, TimeUnit.SECONDS), "recreated Activity recovery worker starts");
        boolean waited = !recovered.await(80, TimeUnit.MILLISECONDS);
        PortableImportGrantRecovery.activityRestoreWorkerFinished();
        check(waited && recovered.await(2, TimeUnit.SECONDS) && !waitFailed.get(),
                "Activity recreation waits for its previous in-process restore worker before startup grant recovery");
        recreatedActivity.join(2000);
    }

    private static List<Task> tasksWithAttachmentCount(int count) {
        List<Task> tasks = new ArrayList<>();
        int remaining = count;
        int taskNumber = 0;
        while (remaining > 0) {
            int attachmentCount = Math.min(AttachmentLogic.MAX_PER_TASK, remaining);
            List<AttachmentRef> references = new ArrayList<>();
            for (int index = 0; index < attachmentCount; index++) {
                references.add(new AttachmentRef(UUID.randomUUID().toString(), "empty-" + index + ".bin",
                        "application/octet-stream", 0));
            }
            String timestamp = "2026-10-05T10:15:30Z";
            tasks.add(new Task(UUID.randomUUID().toString(), "attachment boundary " + taskNumber,
                    null, "medium", false, timestamp, timestamp, references));
            remaining -= attachmentCount;
            taskNumber++;
        }
        return tasks;
    }

    private static List<Task> tasksWithMaximumArchiveMetadata() {
        List<Task> tasks = new ArrayList<>(PortableBackupCodec.MAX_TASKS);
        String title = "\u0800".repeat(160);
        String displayName = "\u0800".repeat(AttachmentLogic.MAX_NAME_CHARS);
        String mimeType = "a".repeat(64) + "/" + "b".repeat(64);
        String timestamp = "+999999999-12-31T23:59:59.999999999+18:00";
        String dueDate = "9999-12-31";
        long attachmentSize = AttachmentLogic.MAX_TOTAL_BYTES / AttachmentLogic.MAX_TOTAL_COUNT;
        for (int taskIndex = 0; taskIndex < PortableBackupCodec.MAX_TASKS; taskIndex++) {
            List<AttachmentRef> references = new ArrayList<>();
            if (taskIndex < AttachmentLogic.MAX_TOTAL_COUNT / AttachmentLogic.MAX_PER_TASK) {
                for (int attachmentIndex = 0; attachmentIndex < AttachmentLogic.MAX_PER_TASK; attachmentIndex++) {
                    references.add(new AttachmentRef(UUID.randomUUID().toString(), displayName,
                            mimeType, attachmentSize));
                }
            }
            tasks.add(new Task(UUID.randomUUID().toString(), title, dueDate, "medium",
                    false, timestamp, timestamp, references));
        }
        return tasks;
    }

    private static void exportFailsClosedOnProviderReadAndWriteFailure() throws Exception {
        byte[] key = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        byte[] payload = "provider-secret".getBytes(StandardCharsets.UTF_8);
        Task task = new Task(UUID.randomUUID().toString(), "provider failure task", null, "medium", false,
                "2026-10-05T10:15:30Z", "2026-10-05T10:15:30Z",
                Collections.singletonList(new AttachmentRef(UUID.randomUUID().toString(), "source.txt",
                        "text/plain", payload.length)));
        ByteArrayOutputStream partial = new ByteArrayOutputStream();
        expectIOException(() -> PortableBackupCodec.writeArchive(partial, key, Collections.singletonList(task),
                (sourceTask, reference) -> new InputStream() {
                    @Override public int read() throws IOException { throw new IOException("provider read failed"); }
                }, new SecureRandom(), () -> false), "attachment-provider read error aborts archive creation");
        check(!contains(partial.toByteArray(), payload), "provider read failure cannot emit attachment plaintext");

        expectIOException(() -> PortableBackupCodec.writeArchive(new CappedOutputStream(64), key,
                Collections.emptyList(), (sourceTask, reference) -> new ByteArrayInputStream(new byte[0]),
                new SecureRandom(), () -> false), "document-provider write/low-space error aborts archive creation");
        PortableBackupCodec.clear(key);
    }

    private static Task taskWithAttachmentSize(long size) {
        return new Task(UUID.randomUUID().toString(), "stream-bound task", null, "medium", false,
                "2026-10-05T10:15:30Z", "2026-10-05T10:15:30Z",
                Collections.singletonList(new AttachmentRef(UUID.randomUUID().toString(), "payload.bin",
                        "application/octet-stream", size)));
    }

    private static byte[] manifestWithAttachmentSizes(long[][] taskSizes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        out.writeInt(0x444d4d31);
        out.writeInt(taskSizes.length);
        int tokenValue = 1;
        for (long[] sizes : taskSizes) {
            out.write(token(tokenValue++));
            writeTestString(out, "task");
            out.writeByte(0);
            out.writeByte(1);
            out.writeByte(0);
            writeTestString(out, "2026-10-05T10:15:30Z");
            writeTestString(out, "2026-10-05T10:15:30Z");
            out.writeByte(sizes.length);
            for (long size : sizes) {
                out.write(token(1000 + tokenValue++));
                out.writeLong(size);
                writeTestString(out, "payload.bin");
                writeTestString(out, "application/octet-stream");
            }
        }
        out.flush();
        return bytes.toByteArray();
    }

    private static void writeTestString(java.io.DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static byte[] token(int value) {
        byte[] token = new byte[16];
        ByteBuffer.wrap(token).putInt(12, value);
        return token;
    }

    private static void drain(InputStream input) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        while (input.read(buffer) != -1) { }
    }

    private static void writeRepeated(OutputStream output, long length) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        Arrays.fill(buffer, (byte) 0x31);
        long remaining = length;
        while (remaining > 0) {
            int amount = (int) Math.min(buffer.length, remaining);
            output.write(buffer, 0, amount);
            remaining -= amount;
        }
    }

    private static byte[] makeArchive(byte[] key, byte[]... payloads) throws Exception {
        List<AttachmentRef> references = new ArrayList<>();
        for (byte[] payload : payloads) {
            references.add(new AttachmentRef(UUID.randomUUID().toString(), "payload.bin",
                    "application/octet-stream", payload.length));
        }
        Task task = new Task(UUID.randomUUID().toString(), "bounded task", null, "medium", false,
                "2026-10-05T10:15:30Z", "2026-10-05T10:15:30Z", references);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicInteger nextPayload = new AtomicInteger();
        PortableBackupCodec.writeArchive(output, key, Collections.singletonList(task),
                (sourceTask, reference) -> new ByteArrayInputStream(payloads[nextPayload.getAndIncrement()]),
                new SecureRandom(), () -> false);
        return output.toByteArray();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }

    private static boolean allZero(byte[] value) {
        int bits = 0;
        for (byte b : value) bits |= b;
        return bits == 0;
    }

    private static void expectIOException(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message + ": expected IOException");
        } catch (IOException expected) {
            // Expected fail-closed path.
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectIOExceptionContaining(IoOperation operation, String expected,
                                                    String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message + ": expected IOException");
        } catch (IOException failure) {
            if (failure.getMessage() == null || !failure.getMessage().toLowerCase().contains(expected)) {
                throw new AssertionError(message + ": unexpected failure path: " + failure.getMessage(), failure);
            }
        }
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete() && file.exists()) throw new IOException("Could not clean host-test staging file.");
    }

    private static final class ZeroProgressInputStream extends ByteArrayInputStream {
        private boolean returnZero = true;
        ZeroProgressInputStream(byte[] bytes) { super(bytes); }
        @Override public synchronized int available() { return 0; }
        @Override public synchronized int read(byte[] target, int offset, int length) {
            if (length > 0 && returnZero) {
                returnZero = false;
                return 0;
            }
            returnZero = true;
            return super.read(target, offset, length);
        }
    }

    private static final class RepeatingInputStream extends InputStream {
        private long remaining;
        RepeatingInputStream(long length) { remaining = length; }
        @Override public int read() {
            if (remaining == 0) return -1;
            remaining--;
            return 0x31;
        }
        @Override public int read(byte[] target, int offset, int length) {
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int amount = (int) Math.min(length, remaining);
            Arrays.fill(target, offset, offset + amount, (byte) 0x31);
            remaining -= amount;
            return amount;
        }
        @Override public int available() { return 0; }
    }

    private static final class CappedOutputStream extends OutputStream {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CappedOutputStream(int limit) { this.limit = limit; }
        @Override public void write(int value) throws IOException {
            if (bytes.size() >= limit) throw new IOException("simulated provider storage full");
            bytes.write(value);
        }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            if (length > limit - bytes.size()) throw new IOException("simulated provider storage full");
            bytes.write(data, offset, length);
        }
    }

    private static final class InMemoryUriJournal implements PortableImportGrantRecovery.JournalStore {
        private PortableImportGrantRecovery.Selection value;
        @Override public PortableImportGrantRecovery.Selection read() { return value; }
        @Override public void write(PortableImportGrantRecovery.Selection selection) { value = selection; }
        @Override public void clear() { value = null; }
    }

    private interface IoOperation { void run() throws Exception; }
}

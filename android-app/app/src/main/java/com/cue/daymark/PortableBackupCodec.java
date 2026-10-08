package com.cue.daymark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Pathless, bounded, authenticated portable task archive; no ZIP or provider paths are accepted. */
final class PortableBackupCodec {
    static final int RECOVERY_KEY_BYTES = 32;
    static final long MAX_ARCHIVE_BYTES = 110L * 1024L * 1024L;
    static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;
    static final int MAX_RECORDS = AttachmentLogic.MAX_TOTAL_COUNT + 1;
    static final int MAX_TASKS = 10_000;
    private static final int MAGIC = 0x444d424b; // DMBK
    private static final int MANIFEST_MAGIC_V1 = 0x444d4d31; // DMM1
    private static final int MANIFEST_MAGIC_V2 = 0x444d4d32; // DMM2 (adds notes, due time, reminder, subtasks)
    private static final int MANIFEST_MAGIC = MANIFEST_MAGIC_V2;
    private static final int VERSION = 1;
    private static final int SUITE_AES_256_GCM_HKDF_SHA256 = 1;
    private static final byte RECORD_MANIFEST = 1;
    private static final byte RECORD_ATTACHMENT = 2;
    private static final int BACKUP_ID_BYTES = 16;
    private static final int TOKEN_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int HEADER_BYTES = 4 + 2 + 2 + BACKUP_ID_BYTES + 4;
    private static final int RECORD_HEADER_BYTES = 1 + 4 + TOKEN_BYTES + TOKEN_BYTES + 8 + NONCE_BYTES;
    private static final byte[] ZERO_TOKEN = new byte[TOKEN_BYTES];
    private static final byte[] HKDF_DOMAIN = "daymark.dmbackup.v1.record-key".getBytes(StandardCharsets.US_ASCII);
    private static final char[] BASE32 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int RECOVERY_CHECK_BYTES = 3;

    private PortableBackupCodec() { }

    interface PayloadSource {
        InputStream open(Task task, AttachmentRef attachment) throws IOException;
    }

    interface CancellationCheck {
        boolean isCancelled();
    }

    interface BackupIdCheck {
        void check(String backupId) throws IOException;
    }

    static byte[] newRecoveryKey(SecureRandom random) {
        if (random == null) throw new IllegalArgumentException("A secure random source is required.");
        byte[] key = new byte[RECOVERY_KEY_BYTES];
        random.nextBytes(key);
        return key;
    }

    /** Human-enterable base32 with a three-byte SHA-256 check value; canonical separators are required. */
    static String encodeRecoveryKey(byte[] key) throws IOException {
        requireKey(key);
        byte[] checked = Arrays.copyOf(key, key.length + RECOVERY_CHECK_BYTES);
        byte[] digest = sha256(key);
        System.arraycopy(digest, 0, checked, key.length, RECOVERY_CHECK_BYTES);
        String raw = base32Encode(checked);
        Arrays.fill(checked, (byte) 0);
        Arrays.fill(digest, (byte) 0);
        StringBuilder formatted = new StringBuilder("DMK1-");
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && i % 4 == 0) formatted.append('-');
            formatted.append(raw.charAt(i));
        }
        return formatted.toString();
    }

    static byte[] decodeRecoveryKey(String printable) throws IOException {
        if (printable == null || printable.length() > 100 || !printable.startsWith("DMK1-")) {
            throw new IOException("Enter a valid Daymark recovery key.");
        }
        String body = printable.substring(5);
        StringBuilder raw = new StringBuilder(56);
        int groupLength = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '-') {
                if (groupLength != 4 || i == body.length() - 1) throw new IOException("The recovery key is malformed.");
                groupLength = 0;
            } else {
                if (indexOfBase32(ch) < 0 || ++groupLength > 4) throw new IOException("The recovery key is malformed.");
                raw.append(ch);
            }
        }
        if (groupLength != 4 || raw.length() != 56) throw new IOException("The recovery key is malformed.");
        byte[] decoded = base32Decode(raw.toString());
        if (decoded.length != RECOVERY_KEY_BYTES + RECOVERY_CHECK_BYTES) {
            Arrays.fill(decoded, (byte) 0);
            throw new IOException("The recovery key is malformed.");
        }
        byte[] key = Arrays.copyOf(decoded, RECOVERY_KEY_BYTES);
        byte[] digest = sha256(key);
        boolean matches = true;
        for (int i = 0; i < RECOVERY_CHECK_BYTES; i++) {
            matches &= digest[i] == decoded[RECOVERY_KEY_BYTES + i];
        }
        Arrays.fill(decoded, (byte) 0);
        Arrays.fill(digest, (byte) 0);
        if (!matches) {
            Arrays.fill(key, (byte) 0);
            throw new IOException("The recovery key check did not match. Check each character and try again.");
        }
        return key;
    }

    static String writeArchive(OutputStream destination, byte[] recoveryKey, List<Task> tasks,
                               PayloadSource payloads, SecureRandom random,
                               CancellationCheck cancellation) throws IOException {
        requireKey(recoveryKey);
        if (destination == null || payloads == null || random == null) throw new IOException("The backup could not be prepared.");
        if (tasks == null || tasks.size() > MAX_TASKS) {
            throw new IOException("Saved tasks contain unsupported or unsafe fields and cannot be exported.");
        }
        int attachmentCount = countAttachmentDescriptors(tasks);
        if (!TaskLogic.isValidTaskList(tasks)) {
            throw new IOException("Saved tasks contain unsupported or unsafe fields and cannot be exported.");
        }
        List<TaskRecord> taskRecords = new ArrayList<>(tasks.size());
        List<AttachmentRecord> attachmentRecords = new ArrayList<>();
        Set<String> tokens = new HashSet<>();
        Set<String> nonces = new HashSet<>();
        long aggregatePayloadBytes = 0;
        for (Task task : tasks) {
            requireSafeTask(task);
            byte[] taskToken = uniqueToken(random, tokens);
            TaskRecord taskRecord = new TaskRecord(task, taskToken);
            taskRecords.add(taskRecord);
            for (AttachmentRef attachment : task.attachments) {
                if (!AttachmentLogic.isValid(attachment) || attachment.sizeBytes > AttachmentLogic.MAX_FILE_BYTES) {
                    throw new IOException("An attachment contains unsupported or unsafe metadata.");
                }
                if (aggregatePayloadBytes > AttachmentLogic.MAX_TOTAL_BYTES - attachment.sizeBytes) {
                    throw new IOException("The backup exceeds the attachment size limit.");
                }
                aggregatePayloadBytes += attachment.sizeBytes;
                attachmentRecords.add(new AttachmentRecord(task, attachment,
                        uniqueToken(random, tokens), taskToken));
            }
        }
        if (attachmentRecords.size() != attachmentCount) throw new IOException("The backup attachment count changed during export.");
        int recordCount = 1 + attachmentRecords.size();
        byte[] backupId = randomNonZero(random, BACKUP_ID_BYTES);
        byte[] manifestToken = uniqueToken(random, tokens);
        byte[] manifest = encodeManifest(taskRecords, attachmentRecords);
        if (manifest.length > MAX_MANIFEST_BYTES) {
            Arrays.fill(manifest, (byte) 0);
            throw new IOException("Task data exceeds the portable backup manifest limit.");
        }
        long predictedBytes = HEADER_BYTES + (long) recordCount * (RECORD_HEADER_BYTES + TAG_BYTES)
                + manifest.length + aggregatePayloadBytes;
        if (predictedBytes > MAX_ARCHIVE_BYTES) {
            Arrays.fill(manifest, (byte) 0);
            throw new IOException("The backup exceeds the archive size limit.");
        }
        byte[] header = headerBytes(backupId, recordCount);
        CountingOutputStream bounded = new CountingOutputStream(destination, MAX_ARCHIVE_BYTES);
        try {
            DataOutputStream out = new DataOutputStream(bounded);
            out.write(header);
            byte[] manifestNonce = uniqueNonce(random, nonces);
            byte[] manifestRecordHeader = recordHeader(RECORD_MANIFEST, 0, manifestToken,
                    ZERO_TOKEN, manifest.length, manifestNonce);
            out.write(manifestRecordHeader);
            encryptBytes(out, recoveryKey, backupId, header, manifestRecordHeader, manifest,
                    cancellation);
            Arrays.fill(manifest, (byte) 0);
            long streamedAttachmentBytes = 0;
            int ordinal = 1;
            for (AttachmentRecord attachmentRecord : attachmentRecords) {
                checkCancelled(cancellation);
                byte[] nonce = uniqueNonce(random, nonces);
                byte[] recordHeader = recordHeader(RECORD_ATTACHMENT, ordinal,
                        attachmentRecord.recordToken, attachmentRecord.taskToken,
                        attachmentRecord.reference.sizeBytes, nonce);
                out.write(recordHeader);
                try (InputStream source = payloads.open(attachmentRecord.task, attachmentRecord.reference)) {
                    if (source == null) throw new IOException("An attachment could not be opened for export.");
                    long streamed = encryptStream(out, recoveryKey, backupId, header, recordHeader,
                            source, attachmentRecord.reference.sizeBytes, cancellation);
                    if (streamed > AttachmentLogic.MAX_FILE_BYTES
                            || streamedAttachmentBytes > AttachmentLogic.MAX_TOTAL_BYTES - streamed) {
                        throw new IOException("The backup exceeds an attachment size limit.");
                    }
                    streamedAttachmentBytes += streamed;
                }
                ordinal++;
            }
            out.flush();
            if (streamedAttachmentBytes != aggregatePayloadBytes || bounded.count() != predictedBytes) {
                throw new IOException("The backup length did not match its authenticated records.");
            }
            return hex(backupId);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Portable backup encryption is unavailable.", exception);
        } finally {
            Arrays.fill(manifest, (byte) 0);
            Arrays.fill(backupId, (byte) 0);
        }
    }

    static VerifiedArchive readArchive(InputStream source, byte[] recoveryKey, File privateStage,
                                       BackupIdCheck backupIdCheck,
                                       CancellationCheck cancellation) throws IOException {
        requireKey(recoveryKey);
        if (source == null || privateStage == null || backupIdCheck == null) throw new IOException("The selected backup could not be opened.");
        if (!privateStage.exists() && !privateStage.mkdirs()) throw new IOException("Private backup staging is unavailable.");
        if (!privateStage.isDirectory()) throw new IOException("Private backup staging is unavailable.");
        List<File> createdPlaintext = new ArrayList<>();
        byte[] manifestPlaintext = null;
        byte[] backupId = null;
        boolean success = false;
        try {
            CountingInputStream bounded = new CountingInputStream(source, MAX_ARCHIVE_BYTES);
            DataInputStream in = new DataInputStream(bounded);
            byte[] magicAndVersion = new byte[8];
            readExactly(in, magicAndVersion, 0, magicAndVersion.length);
            int magic = ByteBuffer.wrap(magicAndVersion, 0, 4).getInt();
            int version = ((magicAndVersion[4] & 0xff) << 8) | (magicAndVersion[5] & 0xff);
            int suite = ((magicAndVersion[6] & 0xff) << 8) | (magicAndVersion[7] & 0xff);
            if (magic != MAGIC || version != VERSION || suite != SUITE_AES_256_GCM_HKDF_SHA256) {
                throw new IOException("This backup format or encryption suite is not supported.");
            }
            backupId = new byte[BACKUP_ID_BYTES];
            readExactly(in, backupId, 0, backupId.length);
            int recordCount = in.readInt();
            if (recordCount < 1 || recordCount > MAX_RECORDS) throw new IOException("This backup declares an invalid record count.");
            backupIdCheck.check(hex(backupId));
            byte[] header = headerBytes(backupId, recordCount);
            Set<String> usedTokens = new HashSet<>();
            Set<String> usedNonces = new HashSet<>();
            RecordHeader first = readRecordHeader(in);
            validateRecordHeader(first, RECORD_MANIFEST, 0, null, ZERO_TOKEN,
                    first.plaintextLength, MAX_MANIFEST_BYTES, usedTokens, usedNonces);
            manifestPlaintext = decryptToMemory(in, recoveryKey, backupId, header, first,
                    first.plaintextLength, cancellation);
            Manifest manifest = decodeManifest(manifestPlaintext, first.recordToken,
                    recordCount, hex(backupId));
            Arrays.fill(manifestPlaintext, (byte) 0);
            manifestPlaintext = null;
            if (manifest.attachments.size() + 1 != recordCount) {
                throw new IOException("The backup record set does not match its authenticated task manifest.");
            }
            for (int index = 0; index < manifest.attachments.size(); index++) {
                checkCancelled(cancellation);
                PortableAttachment expected = manifest.attachments.get(index);
                RecordHeader record = readRecordHeader(in);
                validateRecordHeader(record, RECORD_ATTACHMENT, index + 1,
                        expected.recordToken, expected.taskToken, expected.sizeBytes,
                        AttachmentLogic.MAX_FILE_BYTES, usedTokens, usedNonces);
                File plaintext = File.createTempFile("payload-", ".private", privateStage);
                createdPlaintext.add(plaintext);
                decryptToFile(in, recoveryKey, backupId, header, record, plaintext,
                        expected.sizeBytes, cancellation);
                if (plaintext.length() != expected.sizeBytes) {
                    throw new IOException("An attachment length did not match its authenticated manifest.");
                }
                expected.stagedPlaintext = plaintext;
            }
            checkCancelled(cancellation);
            if (in.read() != -1) throw new IOException("The backup contains trailing data.");
            VerifiedArchive verified = new VerifiedArchive(manifest.backupId, manifest.tasks,
                    manifest.attachments, createdPlaintext);
            success = true;
            return verified;
        } catch (GeneralSecurityException exception) {
            throw new IOException("The recovery key is wrong, or the backup is damaged or unauthenticated.", exception);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("The backup contains malformed or unsafe data.", exception);
        } finally {
            if (manifestPlaintext != null) Arrays.fill(manifestPlaintext, (byte) 0);
            if (backupId != null) Arrays.fill(backupId, (byte) 0);
            if (!success) {
                IOException cleanupFailure = null;
                for (File file : createdPlaintext) {
                    if (file.exists() && !file.delete() && file.exists()) {
                        cleanupFailure = new IOException("Private plaintext staging could not be removed.");
                    }
                }
                if (cleanupFailure != null) throw cleanupFailure;
            }
        }
    }

    private static byte[] encodeManifest(List<TaskRecord> tasks, List<AttachmentRecord> attachments) throws IOException {
        LimitedByteArrayOutputStream bytes = new LimitedByteArrayOutputStream(MAX_MANIFEST_BYTES);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MANIFEST_MAGIC);
        out.writeInt(tasks.size());
        for (TaskRecord record : tasks) {
            Task task = record.task;
            out.write(record.taskToken);
            writeString(out, task.title, 160, 640);
            out.writeByte(task.dueDate == null ? 0 : 1);
            if (task.dueDate != null) writeString(out, task.dueDate, 10, 40);
            out.writeByte(priorityCode(task.priority));
            out.writeByte(task.completed ? 1 : 0);
            writeString(out, task.createdAt, 64, 256);
            writeString(out, task.updatedAt, 64, 256);
            out.writeByte(task.attachments.size());
            for (AttachmentRecord attachment : attachments) {
                if (!attachment.task.id.equals(task.id)) continue;
                out.write(attachment.recordToken);
                out.writeLong(attachment.reference.sizeBytes);
                writeString(out, attachment.reference.displayName, AttachmentLogic.MAX_NAME_CHARS, 480);
                writeString(out, attachment.reference.mimeType, 129, 516);
            }
            // Manifest v2 extension: notes, due time, reminder, and subtasks travel with the task.
            writeString(out, task.notes == null ? "" : task.notes, TaskLogic.MAX_NOTES_CHARS,
                    TaskLogic.MAX_NOTES_CHARS * 4);
            out.writeByte(task.dueTime == null ? 0 : 1);
            if (task.dueTime != null) writeString(out, task.dueTime, 5, 8);
            out.writeByte(reminderLeadCode(task.reminderLeadMinutes));
            out.writeByte(task.reminderShownFire == null ? 0 : 1);
            if (task.reminderShownFire != null) writeString(out, task.reminderShownFire, 64, 256);
            out.writeByte(task.subtasks.size());
            for (Subtask subtask : task.subtasks) {
                writeString(out, subtask.title, TaskLogic.MAX_SUBTASK_TITLE, 480);
                out.writeByte(subtask.done ? 1 : 0);
            }
        }
        out.flush();
        return bytes.toByteArray();
    }

    private static int reminderLeadCode(Integer leadMinutes) {
        if (leadMinutes == null) return 0;
        for (int index = 0; index < TaskLogic.REMINDER_LEADS.length; index++) {
            if (TaskLogic.REMINDER_LEADS[index] == leadMinutes) return index + 1;
        }
        return 0;
    }

    private static Integer reminderLeadFromCode(int code) throws IOException {
        if (code == 0) return null;
        if (code < 1 || code > TaskLogic.REMINDER_LEADS.length) {
            throw new IOException("The manifest contains an invalid reminder setting.");
        }
        return TaskLogic.REMINDER_LEADS[code - 1];
    }

    static Manifest decodeManifest(byte[] plaintext, byte[] manifestRecordToken,
                                   int recordCount, String backupId) throws IOException {
        preflightManifestAttachmentCount(plaintext);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(plaintext));
        int manifestMagic = in.readInt();
        if (manifestMagic != MANIFEST_MAGIC_V1 && manifestMagic != MANIFEST_MAGIC_V2) {
            throw new IOException("The task manifest format is not recognized.");
        }
        boolean manifestV2 = manifestMagic == MANIFEST_MAGIC_V2;
        int taskCount = in.readInt();
        if (taskCount < 0 || taskCount > MAX_TASKS) throw new IOException("The task manifest has an invalid task count.");
        List<PortableTask> tasks = new ArrayList<>(taskCount);
        List<PortableAttachment> attachments = new ArrayList<>();
        Set<String> taskTokens = new HashSet<>();
        Set<String> expectedRecordTokens = new HashSet<>();
        expectedRecordTokens.add(hex(manifestRecordToken));
        long totalBytes = 0;
        for (int taskIndex = 0; taskIndex < taskCount; taskIndex++) {
            byte[] taskToken = new byte[TOKEN_BYTES];
            readExactly(in, taskToken, 0, taskToken.length);
            if (isZero(taskToken) || !taskTokens.add(hex(taskToken))) throw new IOException("The manifest contains a duplicate task association.");
            String title = readString(in, 160, 640);
            requireSafeText(title, 160, false);
            int dueFlag = in.readUnsignedByte();
            if (dueFlag > 1) throw new IOException("The manifest due-date flag is invalid.");
            String dueDate = dueFlag == 0 ? null : readString(in, 10, 40);
            if (dueDate != null && !TaskLogic.isDateOnly(dueDate)) throw new IOException("The manifest contains an invalid due date.");
            int priority = in.readUnsignedByte();
            if (priority > 2) throw new IOException("The manifest priority is invalid.");
            int completed = in.readUnsignedByte();
            if (completed > 1) throw new IOException("The manifest completion flag is invalid.");
            String createdAt = readString(in, 64, 256);
            String updatedAt = readString(in, 64, 256);
            requireTimestamp(createdAt);
            requireTimestamp(updatedAt);
            int attachmentCount = in.readUnsignedByte();
            if (attachmentCount > AttachmentLogic.MAX_PER_TASK) throw new IOException("The manifest exceeds the per-task attachment limit.");
            PortableTask task = new PortableTask(taskToken, title, dueDate,
                    priorityName(priority), completed == 1, createdAt, updatedAt);
            for (int attachmentIndex = 0; attachmentIndex < attachmentCount; attachmentIndex++) {
                byte[] recordToken = new byte[TOKEN_BYTES];
                readExactly(in, recordToken, 0, recordToken.length);
                String tokenHex = hex(recordToken);
                if (isZero(recordToken) || Arrays.equals(recordToken, manifestRecordToken)
                        || !expectedRecordTokens.add(tokenHex)) {
                    throw new IOException("The manifest contains a duplicate attachment record identifier.");
                }
                long size = in.readLong();
                if (size < 0 || size > AttachmentLogic.MAX_FILE_BYTES
                        || totalBytes > AttachmentLogic.MAX_TOTAL_BYTES - size) {
                    throw new IOException("The manifest exceeds an attachment size limit.");
                }
                totalBytes += size;
                String name = readString(in, AttachmentLogic.MAX_NAME_CHARS,
                        AttachmentLogic.MAX_NAME_CHARS * 4);
                String mime = readString(in, 129, 516);
                if (!name.equals(AttachmentLogic.sanitizeDisplayName(name))
                        || !mime.equals(AttachmentLogic.normalizeMimeType(mime))) {
                    throw new IOException("The manifest contains unsafe attachment metadata.");
                }
                PortableAttachment attachment = new PortableAttachment(recordToken, taskToken,
                        name, mime, size, task);
                task.attachments.add(attachment);
                attachments.add(attachment);
            }
            if (manifestV2) {
                String notes = readString(in, TaskLogic.MAX_NOTES_CHARS, TaskLogic.MAX_NOTES_CHARS * 4);
                int dueTimeFlag = in.readUnsignedByte();
                if (dueTimeFlag > 1) throw new IOException("The manifest due-time flag is invalid.");
                String dueTime = dueTimeFlag == 0 ? null : readString(in, 5, 8);
                if (dueTime != null && !TaskDuePresets.isTimeOnly(dueTime)) {
                    throw new IOException("The manifest contains an invalid due time.");
                }
                Integer reminderLead = reminderLeadFromCode(in.readUnsignedByte());
                int reminderShownFlag = in.readUnsignedByte();
                if (reminderShownFlag > 1) throw new IOException("The manifest reminder-state flag is invalid.");
                String reminderShownFire = reminderShownFlag == 0 ? null : readString(in, 64, 256);
                if (reminderShownFire != null) requireTimestamp(reminderShownFire);
                int subtaskCount = in.readUnsignedByte();
                if (subtaskCount > TaskLogic.MAX_SUBTASKS) {
                    throw new IOException("The manifest exceeds the per-task subtask limit.");
                }
                for (int subtaskIndex = 0; subtaskIndex < subtaskCount; subtaskIndex++) {
                    String subtaskTitle = readString(in, TaskLogic.MAX_SUBTASK_TITLE, 480);
                    requireSafeText(subtaskTitle, TaskLogic.MAX_SUBTASK_TITLE, false);
                    int doneFlag = in.readUnsignedByte();
                    if (doneFlag > 1) throw new IOException("The manifest subtask state is invalid.");
                    task.subtasks.add(new PortableSubtask(subtaskTitle, doneFlag == 1));
                }
                task.setExtendedFields(notes, dueTime, reminderLead, reminderShownFire);
            }
            tasks.add(task);
        }
        if (in.read() != -1) throw new IOException("The task manifest contains trailing data.");
        if (attachments.size() + 1 != recordCount) {
            throw new IOException("The manifest does not describe the complete archive record set.");
        }
        for (PortableTask task : tasks) {
            if (expectedRecordTokens.contains(hex(task.taskToken))) {
                throw new IOException("A task association token collides with a record identifier.");
            }
        }
        if (!TaskLogic.isValidTaskList(toValidationTasks(tasks))) {
            throw new IOException("The task manifest failed task-field validation.");
        }
        return new Manifest(backupId, tasks, attachments);
    }

    /** Count descriptor slots without decoding tokens/strings or constructing per-record models. */
    private static void preflightManifestAttachmentCount(byte[] plaintext) throws IOException {
        if (plaintext == null || plaintext.length > MAX_MANIFEST_BYTES) {
            throw new IOException("The task manifest is missing or oversized.");
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(plaintext));
        int manifestMagic = in.readInt();
        if (manifestMagic != MANIFEST_MAGIC_V1 && manifestMagic != MANIFEST_MAGIC_V2) {
            throw new IOException("The task manifest format is not recognized.");
        }
        boolean manifestV2 = manifestMagic == MANIFEST_MAGIC_V2;
        int taskCount = in.readInt();
        if (taskCount < 0 || taskCount > MAX_TASKS) throw new IOException("The task manifest has an invalid task count.");
        int totalAttachments = 0;
        for (int taskIndex = 0; taskIndex < taskCount; taskIndex++) {
            skipManifestBytes(in, TOKEN_BYTES);
            skipManifestString(in, 640);
            int dueFlag = in.readUnsignedByte();
            if (dueFlag > 1) throw new IOException("The manifest due-date flag is invalid.");
            if (dueFlag == 1) skipManifestString(in, 40);
            skipManifestBytes(in, 2); // priority and completion flags; fully validated in the decoding pass.
            skipManifestString(in, 256);
            skipManifestString(in, 256);
            int attachmentCount = in.readUnsignedByte();
            if (attachmentCount > AttachmentLogic.MAX_PER_TASK) {
                throw new IOException("The manifest exceeds the per-task attachment limit.");
            }
            if (totalAttachments > AttachmentLogic.MAX_TOTAL_COUNT - attachmentCount) {
                throw new IOException("The manifest contains too many attachments.");
            }
            totalAttachments += attachmentCount;
            for (int attachmentIndex = 0; attachmentIndex < attachmentCount; attachmentIndex++) {
                skipManifestBytes(in, TOKEN_BYTES + 8L);
                skipManifestString(in, AttachmentLogic.MAX_NAME_CHARS * 4);
                skipManifestString(in, 516);
            }
            if (manifestV2) {
                skipManifestString(in, TaskLogic.MAX_NOTES_CHARS * 4);
                int dueTimeFlag = in.readUnsignedByte();
                if (dueTimeFlag > 1) throw new IOException("The manifest due-time flag is invalid.");
                if (dueTimeFlag == 1) skipManifestString(in, 8);
                int reminderLeadCode = in.readUnsignedByte();
                if (reminderLeadCode > TaskLogic.REMINDER_LEADS.length) {
                    throw new IOException("The manifest contains an invalid reminder setting.");
                }
                int reminderShownFlag = in.readUnsignedByte();
                if (reminderShownFlag > 1) throw new IOException("The manifest reminder-state flag is invalid.");
                if (reminderShownFlag == 1) skipManifestString(in, 256);
                int subtaskCount = in.readUnsignedByte();
                if (subtaskCount > TaskLogic.MAX_SUBTASKS) {
                    throw new IOException("The manifest exceeds the per-task subtask limit.");
                }
                for (int subtaskIndex = 0; subtaskIndex < subtaskCount; subtaskIndex++) {
                    skipManifestString(in, 480);
                    skipManifestBytes(in, 1);
                }
            }
        }
        if (in.read() != -1) throw new IOException("The task manifest contains trailing data.");
    }

    private static void skipManifestString(DataInputStream in, int maxBytes) throws IOException {
        int byteCount = in.readInt();
        if (byteCount < 0 || byteCount > maxBytes) {
            throw new IOException("The task manifest contains an oversized string.");
        }
        skipManifestBytes(in, byteCount);
    }

    private static void skipManifestBytes(DataInputStream in, long byteCount) throws IOException {
        long remaining = byteCount;
        while (remaining > 0) {
            int skipped = in.skipBytes((int) Math.min(remaining, 8192));
            if (skipped > 0) {
                remaining -= skipped;
            } else if (in.read() == -1) {
                throw new IOException("The task manifest is truncated.");
            } else {
                remaining--;
            }
        }
    }

    private static int countAttachmentDescriptors(List<Task> tasks) throws IOException {
        int total = 0;
        for (Task task : tasks) {
            if (task == null || task.attachments == null) continue;
            int taskCount = task.attachments.size();
            if (taskCount > AttachmentLogic.MAX_TOTAL_COUNT - total) {
                throw new IOException("The backup contains too many attachments.");
            }
            total += taskCount;
        }
        return total;
    }

    /* Task validation without exposing portable identifiers; temporary safe IDs are discarded. */
    private static List<Task> toValidationTasks(List<PortableTask> source) throws IOException {
        List<Task> tasks = new ArrayList<>(source.size());
        for (PortableTask task : source) {
            List<AttachmentRef> refs = new ArrayList<>();
            for (PortableAttachment attachment : task.attachments) {
                refs.add(new AttachmentRef(randomUuidForValidation(), attachment.displayName,
                        attachment.mimeType, attachment.sizeBytes));
            }
            List<Subtask> subtasks = new ArrayList<>(task.subtasks.size());
            for (PortableSubtask subtask : task.subtasks) {
                subtasks.add(new Subtask(randomUuidForValidation(), subtask.title, subtask.done));
            }
            tasks.add(new Task(randomUuidForValidation(), task.title, task.dueDate, task.priority,
                    task.completed, task.createdAt, task.updatedAt, refs,
                    task.notes, task.dueTime, task.reminderLeadMinutes, task.reminderShownFire, subtasks));
        }
        return tasks;
    }

    private static String randomUuidForValidation() {
        return java.util.UUID.randomUUID().toString();
    }

    private static void validateRecordHeader(RecordHeader record, byte expectedType, int expectedOrdinal,
                                             byte[] expectedToken, byte[] expectedTaskToken,
                                             long expectedLength, long maxLength,
                                             Set<String> tokens, Set<String> nonces) throws IOException {
        if (record.type != expectedType || record.ordinal != expectedOrdinal
                || record.plaintextLength < 0 || record.plaintextLength > maxLength
                || !Arrays.equals(record.taskToken, expectedTaskToken)
                || (expectedLength >= 0 && record.plaintextLength != expectedLength)) {
            throw new IOException("A backup record header does not match the authenticated manifest.");
        }
        String recordToken = hex(record.recordToken);
        if (isZero(record.recordToken) || (expectedOrdinal == 0 && !tokens.add(recordToken))) {
            throw new IOException("A backup record identifier is invalid or duplicated.");
        }
        if (expectedOrdinal > 0 && !tokens.add(recordToken)) throw new IOException("A duplicate backup record was found.");
        if (expectedToken != null && !Arrays.equals(record.recordToken, expectedToken)) {
            throw new IOException("A backup record header does not match the authenticated manifest.");
        }
        if (!nonces.add(hex(record.nonce))) throw new IOException("A repeated encryption nonce was found.");
    }

    private static RecordHeader readRecordHeader(DataInputStream in) throws IOException {
        byte type = in.readByte();
        int ordinal = in.readInt();
        byte[] recordToken = new byte[TOKEN_BYTES];
        byte[] taskToken = new byte[TOKEN_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        readExactly(in, recordToken, 0, recordToken.length);
        readExactly(in, taskToken, 0, taskToken.length);
        long length = in.readLong();
        readExactly(in, nonce, 0, nonce.length);
        return new RecordHeader(type, ordinal, recordToken, taskToken, length, nonce);
    }

    private static byte[] decryptToMemory(DataInputStream in, byte[] recoveryKey, byte[] backupId,
                                          byte[] header, RecordHeader record, long length,
                                          CancellationCheck cancellation)
            throws IOException, GeneralSecurityException {
        if (length > MAX_MANIFEST_BYTES || length > Integer.MAX_VALUE - TAG_BYTES) {
            throw new IOException("The task manifest exceeds its size limit.");
        }
        ByteArrayOutputStream clear = new ByteArrayOutputStream((int) Math.min(length, 64 * 1024));
        decryptRecord(in, recoveryKey, backupId, header, record, clear, length, cancellation);
        byte[] result = clear.toByteArray();
        if (result.length != length) throw new IOException("The task manifest length is invalid.");
        return result;
    }

    private static void decryptToFile(DataInputStream in, byte[] recoveryKey, byte[] backupId,
                                      byte[] header, RecordHeader record, File destination,
                                      long length, CancellationCheck cancellation)
            throws IOException, GeneralSecurityException {
        try (OutputStream clear = new FileOutputStream(destination)) {
            decryptRecord(in, recoveryKey, backupId, header, record, clear, length, cancellation);
            clear.flush();
        }
    }

    private static void decryptRecord(DataInputStream in, byte[] recoveryKey, byte[] backupId,
                                      byte[] header, RecordHeader record, OutputStream clear,
                                      long plaintextLength, CancellationCheck cancellation)
            throws IOException, GeneralSecurityException {
        byte[] recordHeader = recordHeader(record.type, record.ordinal, record.recordToken,
                record.taskToken, record.plaintextLength, record.nonce);
        byte[] key = deriveRecordKey(recoveryKey, backupId, header, recordHeader);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BYTES * 8, record.nonce));
            cipher.updateAAD(header);
            cipher.updateAAD(recordHeader);
            long ciphertextRemaining = plaintextLength + TAG_BYTES;
            long clearBytes = 0;
            byte[] buffer = new byte[32 * 1024];
            while (ciphertextRemaining > 0) {
                checkCancelled(cancellation);
                int wanted = (int) Math.min(buffer.length, ciphertextRemaining);
                int read = in.read(buffer, 0, wanted);
                if (read < 0) throw new IOException("The backup is truncated inside an encrypted record.");
                if (read == 0) {
                    int single = in.read();
                    if (single < 0) throw new IOException("The backup is truncated inside an encrypted record.");
                    buffer[0] = (byte) single;
                    read = 1;
                }
                ciphertextRemaining -= read;
                byte[] output = cipher.update(buffer, 0, read);
                if (output != null && output.length > 0) {
                    if (clearBytes > plaintextLength - output.length) throw new IOException("An encrypted record exceeds its declared size.");
                    clear.write(output);
                    clearBytes += output.length;
                    Arrays.fill(output, (byte) 0);
                }
            }
            checkCancelled(cancellation);
            byte[] finalBytes = cipher.doFinal();
            if (finalBytes != null && finalBytes.length > 0) {
                if (clearBytes > plaintextLength - finalBytes.length) throw new IOException("An encrypted record exceeds its declared size.");
                clear.write(finalBytes);
                clearBytes += finalBytes.length;
                Arrays.fill(finalBytes, (byte) 0);
            }
            if (clearBytes != plaintextLength) throw new IOException("The decrypted record length is invalid.");
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(recordHeader, (byte) 0);
        }
    }

    private static void encryptBytes(OutputStream out, byte[] recoveryKey, byte[] backupId,
                                     byte[] header, byte[] recordHeader, byte[] plaintext,
                                     CancellationCheck cancellation)
            throws IOException, GeneralSecurityException {
        try (InputStream source = new ByteArrayInputStream(plaintext)) {
            encryptStream(out, recoveryKey, backupId, header, recordHeader, source, plaintext.length, cancellation);
        }
    }

    private static long encryptStream(OutputStream out, byte[] recoveryKey, byte[] backupId,
                                      byte[] header, byte[] recordHeader, InputStream source,
                                      long plaintextLength, CancellationCheck cancellation)
            throws IOException, GeneralSecurityException {
        byte[] metadata = Arrays.copyOfRange(recordHeader, 0, recordHeader.length);
        byte[] recordToken = Arrays.copyOfRange(metadata, 5, 5 + TOKEN_BYTES);
        byte[] taskToken = Arrays.copyOfRange(metadata, 5 + TOKEN_BYTES, 5 + 2 * TOKEN_BYTES);
        byte[] nonce = Arrays.copyOfRange(metadata, metadata.length - NONCE_BYTES, metadata.length);
        byte[] derived = deriveRecordKey(recoveryKey, backupId, header, metadata);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derived, "AES"),
                    new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(header);
            cipher.updateAAD(metadata);
            byte[] buffer = new byte[32 * 1024];
            long remaining = plaintextLength;
            long streamedPlaintext = 0;
            long written = 0;
            while (remaining > 0) {
                checkCancelled(cancellation);
                int wanted = (int) Math.min(buffer.length, remaining);
                int read = source.read(buffer, 0, wanted);
                if (read < 0) throw new IOException("An attachment is shorter than its authenticated size.");
                if (read == 0) {
                    int single = source.read();
                    if (single < 0) throw new IOException("An attachment is shorter than its authenticated size.");
                    buffer[0] = (byte) single;
                    read = 1;
                }
                byte[] encrypted = cipher.update(buffer, 0, read);
                if (encrypted != null && encrypted.length > 0) {
                    out.write(encrypted);
                    written += encrypted.length;
                    Arrays.fill(encrypted, (byte) 0);
                }
                remaining -= read;
                streamedPlaintext += read;
            }
            checkCancelled(cancellation);
            if (source.read() != -1) throw new IOException("An attachment is longer than its authenticated size.");
            byte[] tail = cipher.doFinal();
            if (tail != null && tail.length > 0) {
                out.write(tail);
                written += tail.length;
                Arrays.fill(tail, (byte) 0);
            }
            if (written != plaintextLength + TAG_BYTES) throw new IOException("The encrypted attachment record has an unexpected size.");
            return streamedPlaintext;
        } finally {
            Arrays.fill(derived, (byte) 0);
            Arrays.fill(metadata, (byte) 0);
            Arrays.fill(recordToken, (byte) 0);
            Arrays.fill(taskToken, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
        }
    }

    private static byte[] deriveRecordKey(byte[] recoveryKey, byte[] backupId,
                                          byte[] header, byte[] recordHeader)
            throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(backupId, "HmacSHA256"));
        byte[] prk = hmac.doFinal(recoveryKey);
        ByteArrayOutputStream infoBuffer = new ByteArrayOutputStream(HKDF_DOMAIN.length + header.length + recordHeader.length + 1);
        infoBuffer.write(HKDF_DOMAIN, 0, HKDF_DOMAIN.length);
        infoBuffer.write(header, 0, header.length);
        infoBuffer.write(recordHeader, 0, recordHeader.length);
        infoBuffer.write(1);
        byte[] info = infoBuffer.toByteArray();
        hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
        hmac.update(info);
        byte[] okm = hmac.doFinal();
        byte[] result = Arrays.copyOf(okm, 32);
        Arrays.fill(prk, (byte) 0);
        Arrays.fill(info, (byte) 0);
        Arrays.fill(okm, (byte) 0);
        return result;
    }

    private static byte[] headerBytes(byte[] backupId, int recordCount) throws IOException {
        if (backupId == null || backupId.length != BACKUP_ID_BYTES || recordCount < 1 || recordCount > MAX_RECORDS) {
            throw new IOException("The backup header is invalid.");
        }
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
        header.putInt(MAGIC).putShort((short) VERSION).putShort((short) SUITE_AES_256_GCM_HKDF_SHA256)
                .put(backupId).putInt(recordCount);
        return header.array();
    }

    private static byte[] recordHeader(byte type, int ordinal, byte[] recordToken,
                                       byte[] taskToken, long length, byte[] nonce) throws IOException {
        if (recordToken == null || recordToken.length != TOKEN_BYTES || taskToken == null
                || taskToken.length != TOKEN_BYTES || nonce == null || nonce.length != NONCE_BYTES
                || ordinal < 0 || length < 0) throw new IOException("The backup record header is invalid.");
        ByteBuffer header = ByteBuffer.allocate(RECORD_HEADER_BYTES);
        header.put(type).putInt(ordinal).put(recordToken).put(taskToken).putLong(length).put(nonce);
        return header.array();
    }

    private static byte[] uniqueToken(SecureRandom random, Set<String> used) {
        byte[] token;
        do {
            token = new byte[TOKEN_BYTES];
            random.nextBytes(token);
        } while (isZero(token) || !used.add(hex(token)));
        return token;
    }

    private static byte[] uniqueNonce(SecureRandom random, Set<String> used) {
        byte[] nonce;
        do {
            nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
        } while (isZero(nonce) || !used.add(hex(nonce)));
        return nonce;
    }

    private static byte[] randomNonZero(SecureRandom random, int length) {
        byte[] bytes = new byte[length];
        do { random.nextBytes(bytes); } while (isZero(bytes));
        return bytes;
    }

    private static void writeString(DataOutputStream out, String text, int maxChars, int maxBytes) throws IOException {
        requireSafeText(text, maxChars, true);
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maxBytes) throw new IOException("A task field exceeds the portable format limit.");
        out.writeInt(encoded.length);
        out.write(encoded);
        Arrays.fill(encoded, (byte) 0);
    }

    private static String readString(DataInputStream in, int maxChars, int maxBytes) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > maxBytes) throw new IOException("A manifest text field exceeds its size limit.");
        byte[] encoded = new byte[size];
        readExactly(in, encoded, 0, size);
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
            requireSafeText(decoded, maxChars, true);
            return decoded;
        } catch (CharacterCodingException exception) {
            throw new IOException("The manifest contains invalid UTF-8.", exception);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static void requireSafeText(String text, int maxChars, boolean allowEmpty) throws IOException {
        if (text == null || (!allowEmpty && text.trim().isEmpty()) || text.length() > maxChars) {
            throw new IOException("A task field is empty or exceeds its length limit.");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) || Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(i + 1))) {
                    i++;
                    continue;
                }
                throw new IOException("A task field contains unsafe or malformed text.");
            }
        }
    }

    private static void requireSafeTask(Task task) throws IOException {
        requireSafeText(task.title, 160, false);
        if (task.dueDate != null && !TaskLogic.isDateOnly(task.dueDate)) throw new IOException("A task has an invalid due date.");
        if (!TaskLogic.isPriority(task.priority)) throw new IOException("A task has an invalid priority.");
        requireTimestamp(task.createdAt);
        requireTimestamp(task.updatedAt);
        if (task.attachments == null || task.attachments.size() > AttachmentLogic.MAX_PER_TASK) {
            throw new IOException("A task has an invalid attachment list.");
        }
        requireSafeText(task.notes == null ? "" : task.notes, TaskLogic.MAX_NOTES_CHARS, true);
        if (task.dueTime != null && (task.dueDate == null || !TaskDuePresets.isTimeOnly(task.dueTime))) {
            throw new IOException("A task has an invalid due time.");
        }
        if (task.reminderLeadMinutes != null && !TaskLogic.isReminderLead(task.reminderLeadMinutes)) {
            throw new IOException("A task has an invalid reminder setting.");
        }
        if (task.reminderShownFire != null) requireTimestamp(task.reminderShownFire);
        if (task.subtasks == null || task.subtasks.size() > TaskLogic.MAX_SUBTASKS) {
            throw new IOException("A task has an invalid subtask list.");
        }
        for (Subtask subtask : task.subtasks) {
            requireSafeText(subtask.title, TaskLogic.MAX_SUBTASK_TITLE, false);
        }
    }

    private static void requireTimestamp(String timestamp) throws IOException {
        if (timestamp == null || timestamp.length() > 64) throw new IOException("A task timestamp is invalid.");
        try { Instant.parse(timestamp); }
        catch (RuntimeException exception) { throw new IOException("A task timestamp is invalid.", exception); }
    }

    private static int priorityCode(String priority) {
        if ("low".equals(priority)) return 0;
        if ("medium".equals(priority)) return 1;
        return 2;
    }

    private static String priorityName(int code) {
        return code == 0 ? "low" : code == 1 ? "medium" : "high";
    }

    private static void readExactly(InputStream in, byte[] data, int offset, int length) throws IOException {
        int done = 0;
        while (done < length) {
            int read = in.read(data, offset + done, length - done);
            if (read < 0) throw new IOException("The backup is truncated.");
            if (read == 0) {
                int single = in.read();
                if (single < 0) throw new IOException("The backup is truncated.");
                data[offset + done] = (byte) single;
                read = 1;
            }
            done += read;
        }
    }

    private static void requireKey(byte[] key) throws IOException {
        if (key == null || key.length != RECOVERY_KEY_BYTES) throw new IOException("The recovery key must contain exactly 32 bytes.");
    }

    private static void checkCancelled(CancellationCheck cancellation) throws IOException {
        if (cancellation != null && cancellation.isCancelled()) throw new IOException("Portable backup cancelled.");
    }

    private static boolean isZero(byte[] value) {
        int aggregate = 0;
        for (byte b : value) aggregate |= b;
        return aggregate == 0;
    }

    private static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = alphabet[value >>> 4];
            result[i * 2 + 1] = alphabet[value & 15];
        }
        return new String(result);
    }

    private static byte[] base32Decode(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length() * 5 / 8);
        int accumulator = 0;
        int bits = 0;
        for (int i = 0; i < text.length(); i++) {
            int value = indexOfBase32(text.charAt(i));
            if (value < 0) throw new IOException("The recovery key is malformed.");
            accumulator = (accumulator << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((accumulator >>> bits) & 0xff);
            }
        }
        if (bits > 0 && (accumulator & ((1 << bits) - 1)) != 0) throw new IOException("The recovery key is not canonical.");
        byte[] decoded = out.toByteArray();
        if (!base32Encode(decoded).equals(text)) {
            Arrays.fill(decoded, (byte) 0);
            throw new IOException("The recovery key is not canonical.");
        }
        return decoded;
    }

    private static String base32Encode(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int accumulator = 0;
        int bits = 0;
        for (byte item : bytes) {
            accumulator = (accumulator << 8) | (item & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.append(BASE32[(accumulator >>> bits) & 31]);
            }
        }
        if (bits > 0) result.append(BASE32[(accumulator << (5 - bits)) & 31]);
        return result.toString();
    }

    private static int indexOfBase32(char value) {
        for (int i = 0; i < BASE32.length; i++) if (BASE32[i] == value) return i;
        return -1;
    }

    private static byte[] sha256(byte[] bytes) throws IOException {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (GeneralSecurityException exception) { throw new IOException("SHA-256 is unavailable.", exception); }
    }

    static void clear(byte[] bytes) {
        if (bytes != null) Arrays.fill(bytes, (byte) 0);
    }

    static final class VerifiedArchive {
        final String backupId;
        final List<PortableTask> tasks;
        final List<PortableAttachment> attachments;
        private final List<File> stagedPlaintext;

        VerifiedArchive(String backupId, List<PortableTask> tasks,
                        List<PortableAttachment> attachments, List<File> stagedPlaintext) {
            this.backupId = backupId;
            this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
            this.attachments = Collections.unmodifiableList(new ArrayList<>(attachments));
            this.stagedPlaintext = new ArrayList<>(stagedPlaintext);
            stagedPlaintext.clear();
        }

        void clearStagedPlaintext() throws IOException {
            IOException failure = null;
            for (File file : stagedPlaintext) {
                if (file.exists() && !file.delete() && file.exists()) {
                    failure = new IOException("Private plaintext staging could not be removed.");
                }
            }
            stagedPlaintext.clear();
            if (failure != null) throw failure;
        }
    }

    static final class PortableTask {
        final byte[] taskToken;
        final String title;
        final String dueDate;
        final String priority;
        final boolean completed;
        final String createdAt;
        final String updatedAt;
        String notes;
        String dueTime;
        Integer reminderLeadMinutes;
        String reminderShownFire;
        final List<PortableSubtask> subtasks = new ArrayList<>();
        final List<PortableAttachment> attachments = new ArrayList<>();

        PortableTask(byte[] taskToken, String title, String dueDate, String priority,
                     boolean completed, String createdAt, String updatedAt) {
            this(taskToken, title, dueDate, priority, completed, createdAt, updatedAt,
                    "", null, null, null);
        }

        PortableTask(byte[] taskToken, String title, String dueDate, String priority,
                     boolean completed, String createdAt, String updatedAt,
                     String notes, String dueTime, Integer reminderLeadMinutes, String reminderShownFire) {
            this.taskToken = taskToken;
            this.title = title;
            this.dueDate = dueDate;
            this.priority = priority;
            this.completed = completed;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            setExtendedFields(notes, dueTime, reminderLeadMinutes, reminderShownFire);
        }

        void setExtendedFields(String notes, String dueTime, Integer reminderLeadMinutes,
                               String reminderShownFire) {
            this.notes = notes == null ? "" : notes;
            this.dueTime = dueTime == null || dueTime.isEmpty() ? null : dueTime;
            this.reminderLeadMinutes = reminderLeadMinutes;
            this.reminderShownFire = reminderShownFire == null || reminderShownFire.isEmpty()
                    ? null : reminderShownFire;
        }
    }

    static final class PortableSubtask {
        final String title;
        final boolean done;

        PortableSubtask(String title, boolean done) {
            this.title = title;
            this.done = done;
        }
    }

    static final class PortableAttachment {
        final byte[] recordToken;
        final byte[] taskToken;
        final String displayName;
        final String mimeType;
        final long sizeBytes;
        final PortableTask task;
        File stagedPlaintext;

        PortableAttachment(byte[] recordToken, byte[] taskToken, String displayName,
                           String mimeType, long sizeBytes, PortableTask task) {
            this.recordToken = recordToken;
            this.taskToken = taskToken;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.task = task;
        }
    }

    private static final class TaskRecord {
        final Task task;
        final byte[] taskToken;
        TaskRecord(Task task, byte[] taskToken) { this.task = task; this.taskToken = taskToken; }
    }

    private static final class AttachmentRecord {
        final Task task;
        final AttachmentRef reference;
        final byte[] recordToken;
        final byte[] taskToken;
        AttachmentRecord(Task task, AttachmentRef reference, byte[] recordToken, byte[] taskToken) {
            this.task = task;
            this.reference = reference;
            this.recordToken = recordToken;
            this.taskToken = taskToken;
        }
    }

    static final class Manifest {
        final String backupId;
        final List<PortableTask> tasks;
        final List<PortableAttachment> attachments;
        Manifest(String backupId, List<PortableTask> tasks, List<PortableAttachment> attachments) {
            this.backupId = backupId;
            this.tasks = tasks;
            this.attachments = attachments;
        }
    }

    private static final class RecordHeader {
        final byte type;
        final int ordinal;
        final byte[] recordToken;
        final byte[] taskToken;
        final long plaintextLength;
        final byte[] nonce;
        RecordHeader(byte type, int ordinal, byte[] recordToken, byte[] taskToken,
                     long plaintextLength, byte[] nonce) {
            this.type = type;
            this.ordinal = ordinal;
            this.recordToken = recordToken;
            this.taskToken = taskToken;
            this.plaintextLength = plaintextLength;
            this.nonce = nonce;
        }
    }

    static final class CountingInputStream extends InputStream {
        private final InputStream source;
        private final long max;
        private long count;
        CountingInputStream(InputStream source, long max) { this.source = source; this.max = max; }
        long count() { return count; }
        @Override public int read() throws IOException {
            if (count == max) {
                int extra = source.read();
                if (extra < 0) return -1;
                throw new IOException("The backup exceeds its maximum input size.");
            }
            int value = source.read();
            if (value >= 0) count++;
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (count == max) return read() < 0 ? -1 : failOverLimit();
            int allowed = (int) Math.min(length, max - count);
            int read = source.read(bytes, offset, allowed);
            if (read > 0) count += read;
            return read;
        }
        private int failOverLimit() throws IOException { throw new IOException("The backup exceeds its maximum input size."); }
        @Override public void close() throws IOException { source.close(); }
    }

    static final class CountingOutputStream extends OutputStream {
        private final OutputStream destination;
        private final long max;
        private long count;
        CountingOutputStream(OutputStream destination, long max) { this.destination = destination; this.max = max; }
        long count() { return count; }
        @Override public void write(int value) throws IOException {
            if (count >= max) throw new IOException("The backup exceeds its maximum output size.");
            destination.write(value);
            count++;
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length < 0 || count > max - length) throw new IOException("The backup exceeds its maximum output size.");
            destination.write(bytes, offset, length);
            count += length;
        }
        @Override public void flush() throws IOException { destination.flush(); }
    }

    private static final class LimitedByteArrayOutputStream extends OutputStream {
        private final int max;
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        LimitedByteArrayOutputStream(int max) { this.max = max; }
        @Override public void write(int value) throws IOException {
            if (delegate.size() >= max) throw new IOException("The task manifest exceeds its size limit.");
            delegate.write(value);
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length < 0 || delegate.size() > max - length) throw new IOException("The task manifest exceeds its size limit.");
            delegate.write(bytes, offset, length);
        }
        byte[] toByteArray() { return delegate.toByteArray(); }
    }
}

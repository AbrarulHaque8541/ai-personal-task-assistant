package com.cue.daymark;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.util.AtomicFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Coordinates portable archive staging, add-only restore, and process-death reconciliation. */
final class PortableBackupManager {
    private static final int STATE_MAGIC = 0x444d4953; // DMIS
    private static final int STATE_VERSION = 1;
    private static final int MAX_SEEN_BACKUPS = 10_000;
    private static final int MAX_STATE_BYTES = 512 * 1024;
    private static final int UUID_BYTES = 16;

    private final File stagingRoot;
    private final File stateFile;
    private final AtomicFile atomicState;
    private final ActiveImportUriJournal activeImportUriJournal;
    private final Context context;
    private final RestoreCheckpoint checkpoint;

    PortableBackupManager(Context context) {
        this(context, null);
    }

    PortableBackupManager(Context context, RestoreCheckpoint checkpoint) {
        this.context = context.getApplicationContext();
        File noBackup = context.getNoBackupFilesDir();
        stagingRoot = new File(noBackup, "portable-backup-staging");
        stateFile = new File(noBackup, "portable-backup-imports.bin");
        atomicState = new AtomicFile(stateFile);
        activeImportUriJournal = new ActiveImportUriJournal(new AtomicFile(
                new File(noBackup, "portable-import-uri.bin")));
        this.checkpoint = checkpoint == null ? RestoreCheckpoint.NONE : checkpoint;
        // Sweep abandoned private staging during construction so a failed startup cannot leave
        // decrypted backup plaintext behind until a later successful launch.
        try {
            cleanupTransientFiles();
        } catch (IOException ignored) {
            // Non-fatal; the next launch retries the sweep.
        }
    }

    File createExportStageFile() throws IOException {
        ensureStagingRoot();
        return File.createTempFile("export-", ".dmbackup", stagingRoot);
    }

    File createRestoreStageDirectory() throws IOException {
        ensureStagingRoot();
        File directory = new File(stagingRoot, "restore-" + UUID.randomUUID().toString());
        if (!directory.mkdir()) throw new IOException("Private restore staging could not be created.");
        return directory;
    }

    void cleanupTransientFiles() throws IOException {
        cleanupTransientFiles(null);
    }

    /**
     * Sweep abandoned private staging, optionally preserving one artifact the caller still needs.
     *
     * <p>Restore staging under this root holds decrypted archive plaintext until the archive is
     * cleared and the directory deleted. If a load or reconcile failure previously skipped the
     * sweep, that plaintext survived on disk until the next successful launch.
     *
     * @param preserve a staging path to keep (an in-process export the user has not saved yet),
     *                 or null to remove every entry
     */
    void cleanupTransientFiles(File preserve) throws IOException {
        ensureStagingRoot();
        File[] children = stagingRoot.listFiles();
        if (children == null) throw new IOException("Private backup staging could not be enumerated.");
        File preserved = preserve;
        if (preserved != null) {
            try {
                preserved = preserve.getCanonicalFile();
            } catch (IOException unreadable) {
                preserved = null;
            }
            if (preserved != null && !preserved.exists()) preserved = null;
        }
        for (File child : children) {
            if (preserved != null) {
                File candidate;
                try {
                    candidate = child.getCanonicalFile();
                } catch (IOException unreadable) {
                    candidate = child;
                }
                if (preserved.equals(candidate)) continue;
            }
            deleteTree(child);
        }
    }

    /** Resolve a pending snapshot-last import before normal orphan cleanup or future edits. */
    void reconcile(List<Task> loadedTasks, AndroidAttachmentStore attachments) throws IOException {
        State state = readState();
        if (state.pending == null) return;
        Pending pending = state.pending;
        Set<String> loadedTaskIds = new HashSet<>();
        Set<String> referencedAttachmentIds = new HashSet<>();
        for (Task task : loadedTasks) {
            loadedTaskIds.add(task.id);
            for (AttachmentRef reference : task.attachments) referencedAttachmentIds.add(reference.id);
        }
        int foundTasks = 0;
        for (String taskId : pending.taskIds) if (loadedTaskIds.contains(taskId)) foundTasks++;
        if (foundTasks != 0 && foundTasks != pending.taskIds.size()) {
            throw new IOException("A pending restore snapshot is only partially present; storage was left untouched for safety.");
        }
        if (foundTasks == pending.taskIds.size() && !pending.taskIds.isEmpty()) {
            Set<String> importedAttachmentRefs = new HashSet<>();
            Map<String, String> importedAttachmentOwners = new HashMap<>();
            for (Task task : loadedTasks) {
                if (!pending.taskIds.contains(task.id)) continue;
                for (AttachmentRef reference : task.attachments) {
                    importedAttachmentRefs.add(reference.id);
                    importedAttachmentOwners.putIfAbsent(reference.id, task.id);
                }
            }
            if (!importedAttachmentRefs.equals(new HashSet<>(pending.attachmentIds))) {
                throw new IOException("The committed restore snapshot does not match its journal; storage was left untouched.");
            }
            for (String attachmentId : pending.attachmentIds) {
                String ownerTaskId = importedAttachmentOwners.get(attachmentId);
                if (ownerTaskId == null) {
                    throw new IOException("A restored attachment is not owned by the committed snapshot; storage was left untouched for safety.");
                }
                try {
                    // Existence is not integrity. A present-but-corrupt payload must fail closed here,
                    // before the backup is recorded as imported and its SAF read grant is released,
                    // otherwise the user loses both the attachment and the ability to re-import the backup.
                    attachments.verifyReadable(ownerTaskId, attachmentId);
                } catch (IOException unreadable) {
                    throw new IOException("A restored attachment failed authenticated decryption; the restore is left uncommitted so it can be retried.", unreadable);
                }
            }
            completePending(state, pending.backupId);
            return;
        }
        if (pending.taskIds.isEmpty() && pending.attachmentIds.isEmpty()) {
            completePending(state, pending.backupId);
            return;
        }
        for (String attachmentId : pending.attachmentIds) {
            if (referencedAttachmentIds.contains(attachmentId)) {
                throw new IOException("A pending restore attachment is referenced by existing data; storage was left untouched.");
            }
        }
        for (String attachmentId : pending.attachmentIds) attachments.discardPortableImport(attachmentId);
        clearPending(state);
    }

    /**
     * Authenticate every payload referenced by the loaded snapshot before that snapshot is exposed
     * as editable or backed up.
     *
     * <p>An existence-only check cannot tell an intact payload from a present-but-corrupt or
     * truncated one, so a damaged blob would otherwise stay invisible until the user happened to
     * open that attachment - or worse, until it was exported into a new backup as if it were sound.
     * The scan is bounded by the app's own attachment quota (at most {@code MAX_TOTAL_COUNT}
     * payloads totalling {@code MAX_TOTAL_BYTES}) and runs on the storage worker thread, so it stays
     * inside the 100 MiB / 100 file envelope the store already enforces at import time.
     *
     * @throws IOException when any referenced payload fails authenticated decryption; the caller
     *                     must fail storage closed rather than expose or export unverified data
     */
    void verifyReferencedPayloads(List<Task> loadedTasks, AndroidAttachmentStore attachments)
            throws IOException {
        if (loadedTasks == null) return;
        for (Task task : loadedTasks) {
            for (AttachmentRef reference : task.attachments) {
                try {
                    attachments.verifyReadable(task.id, reference.id);
                } catch (IOException unreadable) {
                    throw new IOException("A stored attachment failed authenticated decryption; "
                            + "storage was left untouched so the data can be recovered.", unreadable);
                }
            }
        }
    }

    /** Durably record the exact selected archive and return its non-secret operation token. */
    PortableImportGrantRecovery.Selection recordActivePortableImportUri(Uri uri) throws IOException {
        if (uri == null) throw new IOException("The selected backup could not be opened.");
        return PortableImportGrantRecovery.recordTakenGrantOrRelease(activeImportUriJournal,
                uri.toString(), this::releaseExactPortableReadGrant);
    }

    PortableImportGrantRecovery.Selection restorePendingPortableImportSelection(String savedUri,
                                                                                 String savedOperationToken)
            throws IOException {
        return PortableImportGrantRecovery.restorePendingActivitySelection(activeImportUriJournal,
                savedUri, savedOperationToken, this::hasExactPortableReadGrant);
    }

    PortableImportGrantRecovery.ActivityState activityStateForSelection(
            PortableImportGrantRecovery.Selection selection) throws IOException {
        return PortableImportGrantRecovery.activityStateForSelection(activeImportUriJournal, selection,
                this::hasExactPortableReadGrant);
    }

    /** Reconcile first; preserve only a matching saved selection still live in this process with a read grant. */
    boolean reconcileStartupImportUri(List<Task> loadedTasks, AndroidAttachmentStore attachments,
                                      PortableImportGrantRecovery.Selection restoredSelection) throws IOException {
        // Authenticate every referenced payload before startup treats the snapshot as usable.
        verifyReferencedPayloads(loadedTasks, attachments);
        return PortableImportGrantRecovery.reconcileStartup(activeImportUriJournal, restoredSelection,
                () -> reconcile(loadedTasks, attachments), this::hasExactPortableReadGrant,
                this::releaseExactPortableReadGrant);
    }

    /** Normal completion and cancellation reconcile before releasing the exact operation's URI read grant. */
    void finishActivePortableImportUri(PortableImportGrantRecovery.Selection expectedSelection,
                                       List<Task> loadedTasks,
                                       AndroidAttachmentStore attachments) throws IOException {
        PortableImportGrantRecovery.finishAfterWork(activeImportUriJournal, expectedSelection,
                () -> reconcile(loadedTasks, attachments), this::releaseExactPortableReadGrant);
    }

    void finishPortableImportSelection(PortableImportGrantRecovery.Selection expectedSelection,
                                       List<Task> loadedTasks,
                                       AndroidAttachmentStore attachments) throws IOException {
        finishActivePortableImportUri(expectedSelection, loadedTasks, attachments);
    }

    List<Task> restore(InputStream source, byte[] recoveryKey, EncryptedTaskStore tasks,
                       AndroidAttachmentStore attachments,
                       PortableBackupCodec.CancellationCheck cancellation) throws Exception {
        return restore(source, recoveryKey, tasks, attachments, cancellation, () -> { });
    }

    List<Task> restore(InputStream source, byte[] recoveryKey, EncryptedTaskStore tasks,
                       AndroidAttachmentStore attachments,
                       PortableBackupCodec.CancellationCheck cancellation,
                       RestoreProgress progress) throws Exception {
        try {
            synchronized (AndroidAttachmentStore.transactionLock()) {
                List<Task> latest = tasks.load();
                reconcile(latest, attachments);
                State state = readState();
                if (state.pending != null) throw new IOException("A previous restore is still being reconciled. Reopen the app before retrying.");
                if (state.seenBackupIds.size() >= MAX_SEEN_BACKUPS) {
                    throw new IOException("The local duplicate-backup history is full; restore is paused rather than forgetting earlier imports.");
                }
                File restoreDirectory = createRestoreStageDirectory();
                PortableBackupCodec.VerifiedArchive verified = null;
                List<String> createdAttachmentIds = new ArrayList<>();
                boolean journalWritten = false;
                boolean snapshotAttempted = false;
                try {
                    verified = PortableBackupCodec.readArchive(source, recoveryKey, restoreDirectory,
                            backupId -> checkBackupIdUnused(backupId), cancellation);
                    latest = tasks.load();
                    Set<String> usedIds = new HashSet<>();
                    for (Task task : latest) {
                        usedIds.add(task.id);
                        for (AttachmentRef ref : task.attachments) usedIds.add(ref.id);
                    }
                    List<Task> additions = new ArrayList<>(verified.tasks.size());
                    List<String> importedTaskIds = new ArrayList<>(verified.tasks.size());
                    List<String> importedAttachmentIds = new ArrayList<>(verified.attachments.size());
                    java.util.Map<PortableBackupCodec.PortableAttachment, String> newAttachmentIds = new java.util.IdentityHashMap<>();
                    for (PortableBackupCodec.PortableTask portable : verified.tasks) {
                        String taskId = freshId(usedIds);
                        importedTaskIds.add(taskId);
                        List<AttachmentRef> references = new ArrayList<>(portable.attachments.size());
                        for (PortableBackupCodec.PortableAttachment portableAttachment : portable.attachments) {
                            String attachmentId = freshId(usedIds);
                            importedAttachmentIds.add(attachmentId);
                            newAttachmentIds.put(portableAttachment, attachmentId);
                            references.add(new AttachmentRef(attachmentId, portableAttachment.displayName,
                                    portableAttachment.mimeType, portableAttachment.sizeBytes));
                        }
                        additions.add(new Task(taskId, portable.title, portable.dueDate, portable.priority,
                                portable.completed, portable.createdAt, portable.updatedAt, references));
                    }
                    List<Task> combined = new ArrayList<>(latest.size() + additions.size());
                    combined.addAll(latest);
                    combined.addAll(additions);
                    if (!TaskLogic.isValidTaskList(combined)) {
                        throw new IOException("The restore would exceed task, attachment, or metadata limits.");
                    }

                    long remainingBytes = AttachmentLogic.remainingBytes(latest);
                    for (PortableBackupCodec.PortableAttachment portableAttachment : verified.attachments) {
                        checkCancelled(cancellation);
                        String appTaskId = taskIdFor(additions, newAttachmentIds.get(portableAttachment));
                        if (portableAttachment.sizeBytes > remainingBytes) {
                            throw new IOException("The restore would exceed the local attachment storage limit.");
                        }
                        try (InputStream clear = new FileInputStream(portableAttachment.stagedPlaintext)) {
                            long staged = attachments.stagePortableImport(appTaskId,
                                    newAttachmentIds.get(portableAttachment), clear, remainingBytes,
                                    portableAttachment.sizeBytes,
                                    () -> cancellation != null && cancellation.isCancelled());
                            if (staged != portableAttachment.sizeBytes) {
                                throw new IOException("A verified attachment did not match its staged byte count.");
                            }
                        }
                        remainingBytes -= portableAttachment.sizeBytes;
                        createdAttachmentIds.add(newAttachmentIds.get(portableAttachment));
                    }
                    checkCancelled(cancellation);
                    writePending(verified.backupId, importedTaskIds, importedAttachmentIds);
                    journalWritten = true;
                    for (String attachmentId : importedAttachmentIds) {
                        checkCancelled(cancellation);
                        attachments.commitPortableImport(attachmentId);
                    }
                    checkpoint.afterAttachmentBlobsCommitted();
                    checkCancelled(cancellation);
                    if (progress != null) progress.onTaskSnapshotCommitStarting();
                    snapshotAttempted = !additions.isEmpty();
                    List<Task> committed = tasks.appendAtomically(additions);
                    checkpoint.afterTaskSnapshotCommitted();
                    completePending(readState(), verified.backupId);
                    return committed;
                } catch (Exception failure) {
                    if (journalWritten || snapshotAttempted) {
                        try {
                            List<Task> recovered = tasks.load();
                            reconcile(recovered, attachments);
                            State reconciledState = readState();
                            if (verified != null && reconciledState.pending == null
                                    && reconciledState.seenBackupIds.contains(verified.backupId)) {
                                return recovered;
                            }
                        } catch (Exception reconcileFailure) {
                            failure.addSuppressed(reconcileFailure);
                            throw new RestoreOutcomeUncertainException(
                                    "The restore commit could not be reconciled safely. Reopen the app before retrying.", failure);
                        }
                    } else {
                        for (String attachmentId : createdAttachmentIds) {
                            try { attachments.discardPortableImport(attachmentId); }
                            catch (Exception cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                        }
                    }
                    throw failure;
                } finally {
                    IOException cleanupFailure = null;
                    if (verified != null) {
                        try { verified.clearStagedPlaintext(); }
                        catch (IOException failure) { cleanupFailure = failure; }
                    }
                    try { deleteTree(restoreDirectory); }
                    catch (IOException failure) {
                        if (cleanupFailure == null) cleanupFailure = failure;
                        else cleanupFailure.addSuppressed(failure);
                    }
                    if (cleanupFailure != null && restoreDirectory.exists()) {
                        if (journalWritten) {
                            throw new RestoreOutcomeUncertainException(
                                    "Private restore staging could not be cleared. Reopen the app before retrying.", cleanupFailure);
                        }
                        throw cleanupFailure;
                    }
                }
            }
        } finally {
            PortableBackupCodec.clear(recoveryKey);
        }
    }

    private static String taskIdFor(List<Task> tasks, String attachmentId) throws IOException {
        for (Task task : tasks) {
            for (AttachmentRef attachment : task.attachments) {
                if (attachment.id.equals(attachmentId)) return task.id;
            }
        }
        throw new IOException("The restore task association could not be resolved.");
    }

    private void checkBackupIdUnused(String backupId) throws IOException {
        State state = readState();
        if (state.pending != null) throw new IOException("A previous restore must be reconciled before another import.");
        if (state.seenBackupIds.contains(backupId)) {
            throw new IOException("This backup was already imported on this device. Repeated imports are blocked.");
        }
    }

    private void writePending(String backupId, List<String> taskIds,
                              List<String> attachmentIds) throws IOException {
        State state = readState();
        if (state.pending != null || state.seenBackupIds.contains(backupId)) {
            throw new IOException("This backup was already imported or another restore is pending.");
        }
        if (state.seenBackupIds.size() >= MAX_SEEN_BACKUPS) throw new IOException("The local duplicate-backup history is full.");
        state.pending = new Pending(backupId, taskIds, attachmentIds);
        writeState(state);
    }

    private void completePending(State state, String backupId) throws IOException {
        if (state.pending == null || !state.pending.backupId.equals(backupId)) {
            throw new IOException("The restore journal no longer matches this transaction.");
        }
        if (!state.seenBackupIds.add(backupId)) throw new IOException("This backup ID was already recorded as imported.");
        if (state.seenBackupIds.size() > MAX_SEEN_BACKUPS) throw new IOException("The local duplicate-backup history is full.");
        state.pending = null;
        writeState(state);
    }

    private void clearPending(State state) throws IOException {
        if (state.pending == null) return;
        state.pending = null;
        writeState(state);
    }

    private void releaseExactPortableReadGrant(String exactUri) throws IOException {
        Uri uri = Uri.parse(exactUri);
        if (!hasExactPortableReadGrant(uri)) return;
        try {
            context.getContentResolver().releasePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException failure) {
            if (!hasExactPortableReadGrant(uri)) return;
            throw new IOException("The abandoned portable-import read grant could not be released.", failure);
        }
        if (hasExactPortableReadGrant(uri)) {
            throw new IOException("The abandoned portable-import read grant remains active.");
        }
    }

    private boolean hasExactPortableReadGrant(Uri exactUri) throws IOException {
        try {
            for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                if (exactUri.equals(permission.getUri()) && permission.isReadPermission()) return true;
            }
            return false;
        } catch (RuntimeException failure) {
            throw new IOException("Persisted portable-import permissions could not be checked.", failure);
        }
    }

    private boolean hasExactPortableReadGrant(String exactUri) {
        try {
            return exactUri != null && hasExactPortableReadGrant(Uri.parse(exactUri));
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    static final class ActiveImportUriJournal implements PortableImportGrantRecovery.JournalStore {
        private static final int JOURNAL_MAGIC = 0x4450494a; // DPIJ
        private static final int JOURNAL_VERSION = 1;
        private static final int TOKEN_BYTES = 36;
        private static final int MAX_ENCODED_JOURNAL_BYTES = PortableImportGrantRecovery.MAX_URI_CHARS * 4 + 64;
        private final AtomicFile atomic;

        ActiveImportUriJournal(AtomicFile atomic) { this.atomic = atomic; }

        @Override public PortableImportGrantRecovery.Selection read() throws IOException {
            File base = atomic.getBaseFile();
            File backup = new File(base.getPath() + ".bak");
            File staged = new File(base.getPath() + ".new");
            if (!base.exists() && !backup.exists()) {
                if (staged.exists()) throw new IOException("The portable-import URI journal has an incomplete write.");
                return null;
            }
            byte[] encoded;
            try (InputStream input = atomic.openRead()) {
                encoded = readBounded(input, MAX_ENCODED_JOURNAL_BYTES);
            }
            try {
                if (encoded.length < Integer.BYTES
                        || ByteBuffer.wrap(encoded, 0, Integer.BYTES).getInt() != JOURNAL_MAGIC) {
                    String legacyUri = decodeUtf8(encoded);
                    if (!PortableImportGrantRecovery.isContentUri(legacyUri)) {
                        throw new IOException("The portable-import URI journal is malformed.");
                    }
                    return new PortableImportGrantRecovery.Selection(null, legacyUri);
                }
                DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
                if (input.readInt() != JOURNAL_MAGIC || input.readUnsignedByte() != JOURNAL_VERSION) {
                    throw new IOException("The portable-import URI journal version is unsupported.");
                }
                int tokenLength = input.readUnsignedByte();
                int uriLength = input.readInt();
                if (tokenLength != TOKEN_BYTES || uriLength <= 0
                        || uriLength > PortableImportGrantRecovery.MAX_URI_CHARS * 4
                        || encoded.length != Integer.BYTES + 1 + 1 + Integer.BYTES + tokenLength + uriLength) {
                    throw new IOException("The portable-import URI journal is malformed.");
                }
                byte[] tokenBytes = new byte[tokenLength];
                byte[] uriBytes = new byte[uriLength];
                try {
                    input.readFully(tokenBytes);
                    input.readFully(uriBytes);
                    String token = decodeUtf8(tokenBytes);
                    String uri = decodeUtf8(uriBytes);
                    if (!PortableImportGrantRecovery.isOperationToken(token)
                            || !PortableImportGrantRecovery.isContentUri(uri)) {
                        throw new IOException("The portable-import URI journal is malformed.");
                    }
                    return new PortableImportGrantRecovery.Selection(token, uri);
                } finally {
                    Arrays.fill(tokenBytes, (byte) 0);
                    Arrays.fill(uriBytes, (byte) 0);
                }
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        }

        @Override public void write(PortableImportGrantRecovery.Selection selection) throws IOException {
            if (selection == null || !PortableImportGrantRecovery.isOperationToken(selection.operationToken)
                    || !PortableImportGrantRecovery.isContentUri(selection.uri)) {
                throw new IOException("The portable-import URI journal is malformed.");
            }
            byte[] tokenBytes = selection.operationToken.getBytes(StandardCharsets.UTF_8);
            byte[] uriBytes = selection.uri.getBytes(StandardCharsets.UTF_8);
            if (uriBytes.length > PortableImportGrantRecovery.MAX_URI_CHARS * 4) {
                throw new IOException("The portable-import URI journal is oversized.");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Integer.BYTES + 6 + tokenBytes.length + uriBytes.length);
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                data.writeInt(JOURNAL_MAGIC);
                data.writeByte(JOURNAL_VERSION);
                data.writeByte(tokenBytes.length);
                data.writeInt(uriBytes.length);
                data.write(tokenBytes);
                data.write(uriBytes);
            }
            byte[] encoded = bytes.toByteArray();
            FileOutputStream output = null;
            try {
                output = atomic.startWrite();
                output.write(encoded);
                output.flush();
                output.getFD().sync();
                atomic.finishWrite(output);
                output = null;
            } catch (Exception failure) {
                if (output != null) {
                    try { atomic.failWrite(output); }
                    catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                }
                if (failure instanceof IOException) throw (IOException) failure;
                throw new IOException("The portable-import URI journal could not be committed.", failure);
            } finally {
                Arrays.fill(tokenBytes, (byte) 0);
                Arrays.fill(uriBytes, (byte) 0);
                Arrays.fill(encoded, (byte) 0);
            }
        }

        private static String decodeUtf8(byte[] encoded) throws IOException {
            String value = new String(encoded, StandardCharsets.UTF_8);
            if (!Arrays.equals(encoded, value.getBytes(StandardCharsets.UTF_8))) {
                throw new IOException("The portable-import URI journal is malformed.");
            }
            return value;
        }

        @Override public void clear() throws IOException {
            atomic.delete();
            File base = atomic.getBaseFile();
            if (base.exists() || new File(base.getPath() + ".bak").exists()
                    || new File(base.getPath() + ".new").exists()) {
                throw new IOException("The portable-import URI journal could not be removed.");
            }
        }
    }

    private State readState() throws IOException {
        File backup = new File(stateFile.getPath() + ".bak");
        File staged = new File(stateFile.getPath() + ".new");
        if (!stateFile.exists() && !backup.exists()) {
            if (staged.exists()) throw new IOException("The restore journal has an incomplete write; restore is paused for safety.");
            return new State();
        }
        byte[] encoded;
        try (InputStream input = atomicState.openRead()) {
            encoded = readBounded(input, MAX_STATE_BYTES);
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
            if (in.readInt() != STATE_MAGIC || in.readInt() != STATE_VERSION) {
                throw new IOException("The restore journal format is unsupported.");
            }
            int seenCount = in.readInt();
            if (seenCount < 0 || seenCount > MAX_SEEN_BACKUPS) throw new IOException("The restore journal has an invalid backup count.");
            State state = new State();
            for (int i = 0; i < seenCount; i++) {
                String id = hex(readExactly(in, UUID_BYTES));
                if (!state.seenBackupIds.add(id)) throw new IOException("The restore journal contains duplicate backup IDs.");
            }
            int pendingFlag = in.readUnsignedByte();
            if (pendingFlag > 1) throw new IOException("The restore journal pending flag is invalid.");
            if (pendingFlag == 1) {
                String backupId = hex(readExactly(in, UUID_BYTES));
                int taskCount = in.readInt();
                int attachmentCount = in.readInt();
                if (taskCount < 0 || taskCount > PortableBackupCodec.MAX_TASKS
                        || attachmentCount < 0 || attachmentCount > AttachmentLogic.MAX_TOTAL_COUNT
                        || (long) taskCount * UUID_BYTES + (long) attachmentCount * UUID_BYTES > in.available()) {
                    throw new IOException("The restore journal transaction is oversized.");
                }
                List<String> taskIds = new ArrayList<>(taskCount);
                List<String> attachmentIds = new ArrayList<>(attachmentCount);
                Set<String> allIds = new HashSet<>();
                for (int i = 0; i < taskCount; i++) {
                    String id = uuid(readExactly(in, UUID_BYTES));
                    if (!AttachmentLogic.isValidId(id) || !allIds.add(id)) throw new IOException("The restore journal contains an invalid task ID.");
                    taskIds.add(id);
                }
                for (int i = 0; i < attachmentCount; i++) {
                    String id = uuid(readExactly(in, UUID_BYTES));
                    if (!AttachmentLogic.isValidId(id) || !allIds.add(id)) throw new IOException("The restore journal contains an invalid attachment ID.");
                    attachmentIds.add(id);
                }
                if (state.seenBackupIds.contains(backupId)) throw new IOException("The restore journal repeats an already imported backup.");
                state.pending = new Pending(backupId, taskIds, attachmentIds);
            }
            if (in.read() != -1) throw new IOException("The restore journal contains trailing data.");
            return state;
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private void writeState(State state) throws IOException {
        byte[] encoded = encodeState(state);
        ensureStagingRoot();
        FileOutputStream output = null;
        try {
            output = atomicState.startWrite();
            output.write(encoded);
            output.flush();
            output.getFD().sync();
            atomicState.finishWrite(output);
            output = null;
            try (InputStream check = atomicState.openRead()) {
                byte[] committed = readBounded(check, MAX_STATE_BYTES);
                boolean identical = Arrays.equals(encoded, committed);
                Arrays.fill(committed, (byte) 0);
                if (!identical) throw new IOException("The restore journal commit could not be verified.");
            }
        } catch (Exception failure) {
            if (output != null) {
                try { atomicState.failWrite(output); }
                catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("The restore journal could not be committed.", failure);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private byte[] encodeState(State state) throws IOException {
        if (state.seenBackupIds.size() > MAX_SEEN_BACKUPS) throw new IOException("The restore journal exceeds its size limit.");
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(raw);
        out.writeInt(STATE_MAGIC);
        out.writeInt(STATE_VERSION);
        out.writeInt(state.seenBackupIds.size());
        for (String id : state.seenBackupIds) out.write(hexBytes(id));
        out.writeByte(state.pending == null ? 0 : 1);
        if (state.pending != null) {
            if (state.pending.taskIds.size() > PortableBackupCodec.MAX_TASKS
                    || state.pending.attachmentIds.size() > AttachmentLogic.MAX_TOTAL_COUNT) {
                throw new IOException("The pending restore journal is oversized.");
            }
            out.write(hexBytes(state.pending.backupId));
            out.writeInt(state.pending.taskIds.size());
            out.writeInt(state.pending.attachmentIds.size());
            for (String id : state.pending.taskIds) out.write(uuidBytes(id));
            for (String id : state.pending.attachmentIds) out.write(uuidBytes(id));
        }
        out.flush();
        byte[] encoded = raw.toByteArray();
        if (encoded.length > MAX_STATE_BYTES) throw new IOException("The restore journal exceeds its byte limit.");
        return encoded;
    }

    private static byte[] readBounded(InputStream input, int max) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > max - output.size()) throw new IOException("The restore journal exceeds its size limit.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static byte[] readExactly(DataInputStream input, int length) throws IOException {
        byte[] result = new byte[length];
        input.readFully(result);
        return result;
    }

    private static byte[] hexBytes(String hex) throws IOException {
        if (hex == null || hex.length() != 32) throw new IOException("A backup identifier is malformed.");
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0 || Character.toLowerCase(hex.charAt(i * 2)) != hex.charAt(i * 2)
                    || Character.toLowerCase(hex.charAt(i * 2 + 1)) != hex.charAt(i * 2 + 1)) {
                throw new IOException("A backup identifier is malformed.");
            }
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
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

    private static String uuid(byte[] bytes) {
        ByteBuffer input = ByteBuffer.wrap(bytes);
        return new UUID(input.getLong(), input.getLong()).toString();
    }

    private static byte[] uuidBytes(String id) throws IOException {
        try {
            UUID parsed = UUID.fromString(id);
            if (!parsed.toString().equals(id)) throw new IllegalArgumentException();
            return ByteBuffer.allocate(UUID_BYTES).putLong(parsed.getMostSignificantBits())
                    .putLong(parsed.getLeastSignificantBits()).array();
        } catch (IllegalArgumentException failure) {
            throw new IOException("A task or attachment identifier is malformed.", failure);
        }
    }

    private static String freshId(Set<String> used) {
        String candidate;
        do { candidate = UUID.randomUUID().toString(); } while (!used.add(candidate));
        return candidate;
    }

    private void ensureStagingRoot() throws IOException {
        if (!stagingRoot.exists() && !stagingRoot.mkdirs()) throw new IOException("Private backup staging is unavailable.");
        if (!stagingRoot.isDirectory()) throw new IOException("Private backup staging is unavailable.");
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete() && file.exists()) throw new IOException("Private backup staging could not be removed.");
    }

    private static void checkCancelled(PortableBackupCodec.CancellationCheck cancellation) throws IOException {
        if (cancellation != null && cancellation.isCancelled()) throw new IOException("Portable backup cancelled.");
    }

    static final class RestoreOutcomeUncertainException extends IOException {
        RestoreOutcomeUncertainException(String message, Throwable cause) { super(message, cause); }
    }

    private static final class State {
        final LinkedHashSet<String> seenBackupIds = new LinkedHashSet<>();
        Pending pending;
    }

    private static final class Pending {
        final String backupId;
        final List<String> taskIds;
        final List<String> attachmentIds;
        Pending(String backupId, List<String> taskIds, List<String> attachmentIds) {
            this.backupId = backupId;
            this.taskIds = new ArrayList<>(taskIds);
            this.attachmentIds = new ArrayList<>(attachmentIds);
        }
    }

    interface RestoreCheckpoint {
        RestoreCheckpoint NONE = new RestoreCheckpoint() {
            @Override public void afterAttachmentBlobsCommitted() { }
            @Override public void afterTaskSnapshotCommitted() { }
        };
        void afterAttachmentBlobsCommitted();
        void afterTaskSnapshotCommitted();
    }

    interface RestoreProgress {
        void onTaskSnapshotCommitStarting();
    }
}

package com.cue.daymark;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

/** Platform-only instrumentation; intentionally uses no JUnit/AndroidX test dependency. */
public final class DaymarkPlatformInstrumentation extends Instrumentation {
    private static final String TASK_ID = "instrumentation-task-v1";
    private int assertions;

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            importLegacySnapshotRoundTripAndRollback();
            attachmentProviderPipeAndAndroidCrypto();
            portableImportUriJournalPreservesRecreationAndCleansOrphans();
            portableBackupRekeysAndReconcilesProcessDeath();
            result.putString("result", "passed");
            result.putInt("assertions", assertions);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "failed");
            result.putInt("assertions", assertions);
            result.putString("failure", stackTrace(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void importLegacySnapshotRoundTripAndRollback() throws Exception {
        File root = makeTestDirectory("schema-v1");
        try {
            File snapshotFile = new File(root, "tasks.enc");
            EncryptedBlobStore.KeyAccess keys = new FixedTestKey();
            byte[] legacyJson = readAsset("schema-v1-task-snapshot.json");
            JSONObject fixture = new JSONObject(new String(legacyJson, StandardCharsets.UTF_8));
            check(fixture.optInt("version", -1) == 1, "golden fixture is schema v1");
            check(fixture.optJSONArray("tasks") != null && fixture.optJSONArray("tasks").length() == 2,
                    "golden fixture contains both legacy task records");

            saveRawEncryptedSnapshot(snapshotFile, keys, legacyJson);
            EncryptedTaskStore legacyStore = new EncryptedTaskStore(snapshotFile, keys);
            List<Task> imported = legacyStore.load();
            check(imported.size() == 2, "schema-v1 snapshot imports as two tasks");
            check(imported.get(0).attachments.isEmpty() && imported.get(1).attachments.isEmpty(),
                    "schema-v1 tasks import with no synthesized attachment references");
            assertTask(imported.get(0), "2e8cae89-3dd4-43e3-82d4-51a8df643501",
                    "Replace the scratched desk mat", "2026-10-06", "high", false,
                    "2026-10-05T10:15:30Z", "2026-10-05T12:04:11Z");
            assertTask(imported.get(1), "fa271a20-7586-4fc1-8855-7f8cb9059b73",
                    "Read Crème & save notes", null, "low", true,
                    "2026-10-04T08:00:00Z", "2026-10-05T14:02:59Z");

            legacyStore.save(imported);
            byte[] migratedJson = loadRawEncryptedSnapshot(snapshotFile, keys);
            JSONObject migrated = new JSONObject(new String(migratedJson, StandardCharsets.UTF_8));
            check(migrated.optInt("version", -1) == 2, "the first successful save strictly migrates to schema v2");
            EncryptedTaskStore reloadedStore = new EncryptedTaskStore(snapshotFile, keys);
            List<Task> roundTrip = reloadedStore.load();
            check(roundTrip.size() == imported.size(), "migrated snapshot reopens with the same task count");
            for (int index = 0; index < imported.size(); index++) {
                assertTaskEquals(imported.get(index), roundTrip.get(index));
            }

            File corruptSchemaFile = new File(root, "corrupt-schema.enc");
            byte[] malformedJson = ("{\"version\":1,\"tasks\":[{\"id\":\"2e8cae89-3dd4-43e3-82d4-51a8df643501\","
                    + "\"title\":17,\"dueDate\":null,\"priority\":\"high\",\"completed\":false,"
                    + "\"createdAt\":\"2026-10-05T10:15:30Z\",\"updatedAt\":\"2026-10-05T12:04:11Z\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            saveRawEncryptedSnapshot(corruptSchemaFile, keys, malformedJson);
            byte[] corruptSchemaBefore = readFile(corruptSchemaFile);
            EncryptedTaskStore corruptSchemaStore = new EncryptedTaskStore(corruptSchemaFile, keys);
            expectStorageKind(corruptSchemaStore, EncryptedBlobStore.Kind.CORRUPT_DATA,
                    "wrong-typed legacy fields fail closed as corrupt data");
            check(Arrays.equals(corruptSchemaBefore, readFile(corruptSchemaFile)),
                    "malformed schema-v1 ciphertext remains byte-for-byte unchanged");
            expectSaveKind(corruptSchemaStore, EncryptedBlobStore.Kind.STORE_NOT_VERIFIED,
                    "failed schema import blocks replacement saves");
            check(Arrays.equals(corruptSchemaBefore, readFile(corruptSchemaFile)),
                    "blocked save does not replace the last encrypted bytes");

            byte[] migratedCiphertext = readFile(snapshotFile);
            byte[] tamperedCiphertext = migratedCiphertext.clone();
            tamperedCiphertext[tamperedCiphertext.length - 1] ^= 0x01;
            writeFile(snapshotFile, tamperedCiphertext);
            EncryptedTaskStore tamperedStore = new EncryptedTaskStore(snapshotFile, keys);
            expectStorageKind(tamperedStore, EncryptedBlobStore.Kind.AUTHENTICATION_FAILED,
                    "modified task ciphertext fails authentication");
            check(Arrays.equals(tamperedCiphertext, readFile(snapshotFile)),
                    "authentication failure does not rewrite the damaged snapshot");
            expectSaveKind(tamperedStore, EncryptedBlobStore.Kind.STORE_NOT_VERIFIED,
                    "authentication failure blocks later task saves");
            check(Arrays.equals(tamperedCiphertext, readFile(snapshotFile)),
                    "blocked save preserves the exact damaged snapshot bytes");
        } finally {
            deleteTree(root);
        }
    }

    private void portableImportUriJournalPreservesRecreationAndCleansOrphans() throws Exception {
        File root = makeTestDirectory("portable-uri-journal");
        String selected = "content://documents.example/document/activity-recreation.dmbackup";
        String unrelated = "content://documents.example/document/unrelated";
        Set<String> grants = new HashSet<>(Arrays.asList(selected, unrelated));
        File journalFile = new File(root, "active-import-uri.bin");
        try {
            PortableBackupManager.ActiveImportUriJournal journal =
                    new PortableBackupManager.ActiveImportUriJournal(new AtomicFile(journalFile));
            PortableImportGrantRecovery.Selection selection = PortableImportGrantRecovery.recordTakenGrantOrRelease(
                    journal, selected, exactUri -> {
                if (!grants.remove(exactUri)) throw new IOException("selected URI grant was not held");
            });
            PortableImportGrantRecovery.ActivityState saved = PortableImportGrantRecovery.activityStateForSelection(
                    journal, selection, grants::contains);
            check(saved != null && selected.equals(saved.uri)
                            && selection.operationToken.equals(saved.operationToken),
                    "saved Activity state contains the journaled URI and non-secret operation token");
            PortableBackupManager.ActiveImportUriJournal afterRecreation =
                    new PortableBackupManager.ActiveImportUriJournal(new AtomicFile(journalFile));
            PortableImportGrantRecovery.Selection restored = PortableImportGrantRecovery.restorePendingActivitySelection(
                    afterRecreation, saved.uri, saved.operationToken, grants::contains);
            check(selection.matches(restored), "recreated Activity validates the real AtomicFile journal record");
            boolean retained = PortableImportGrantRecovery.reconcileStartup(afterRecreation, restored,
                    () -> check(grants.contains(selected), "recreation reconciles before keeping the selected URI grant"),
                    grants::contains, exactUri -> { throw new AssertionError("valid recreation must retain its grant"); });
            check(retained && selection.matches(afterRecreation.read()) && grants.contains(selected),
                    "valid Activity recreation preserves the journal and exact selected read grant");

            PortableImportGrantRecovery.finishAfterWork(afterRecreation, selection, () -> { }, exactUri -> {
                check(selected.equals(exactUri), "user cancellation releases only the URI persisted in AtomicFile");
                grants.remove(exactUri);
            });
            check(afterRecreation.read() == null && !grants.contains(selected) && grants.contains(unrelated),
                    "cancelled key flow clears its real AtomicFile journal and preserves unrelated grants");

            String orphanUri = "content://documents.example/document/process-death.dmbackup";
            grants.add(orphanUri);
            PortableImportGrantRecovery.Selection orphan = new PortableImportGrantRecovery.Selection(
                    UUID.randomUUID().toString(), orphanUri);
            afterRecreation.write(orphan); // Deliberately not registered in this process: models process death.
            check(PortableImportGrantRecovery.restorePendingActivitySelection(afterRecreation,
                            orphan.uri, orphan.operationToken, grants::contains) == null,
                    "a saved token from a new process is not accepted as Activity recreation");
            AtomicBoolean reconciled = new AtomicBoolean();
            boolean orphanPreserved = PortableImportGrantRecovery.reconcileStartup(afterRecreation, null, () -> {
                reconciled.set(true);
            }, grants::contains, exactUri -> {
                check(reconciled.get(), "process-death orphan cleanup follows transaction reconciliation");
                check(orphanUri.equals(exactUri), "process-death recovery releases only the journaled URI");
                grants.remove(exactUri);
            });
            check(!orphanPreserved && afterRecreation.read() == null && !grants.contains(orphanUri)
                            && grants.contains(unrelated),
                    "real AtomicFile orphan is cleared after process death without touching other grants");

            String legacyUri = "content://documents.example/document/legacy-uri-only.dmbackup";
            grants.add(legacyUri);
            try (FileOutputStream legacyFile = new FileOutputStream(journalFile)) {
                legacyFile.write(legacyUri.getBytes(StandardCharsets.UTF_8));
            }
            PortableBackupManager.ActiveImportUriJournal legacyJournal =
                    new PortableBackupManager.ActiveImportUriJournal(new AtomicFile(journalFile));
            check(legacyJournal.read().operationToken == null,
                    "pre-token AtomicFile journal is recognized as a cleanup-only legacy record");
            AtomicBoolean legacyReconciled = new AtomicBoolean();
            PortableImportGrantRecovery.recoverAfterProcessDeath(legacyJournal, () -> {
                legacyReconciled.set(true);
            }, exactUri -> {
                check(legacyReconciled.get(), "legacy URI-only cleanup follows transaction reconciliation");
                check(legacyUri.equals(exactUri), "legacy cleanup releases only its exact URI");
                grants.remove(exactUri);
            });
            check(legacyJournal.read() == null && !grants.contains(legacyUri) && grants.contains(unrelated),
                    "legacy journal is removed without disturbing unrelated grants");
        } finally {
            deleteTree(root);
        }
    }

    private void portableBackupRekeysAndReconcilesProcessDeath() throws Exception {
        File root = makeTestDirectory("portable-recovery");
        String sourceAlias = "daymark.test.portable." + UUID.randomUUID();
        byte[] recoveryKey = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        String recoveryCode = PortableBackupCodec.encodeRecoveryKey(recoveryKey);
        try {
            File sourceDirectory = new File(root, "source-blobs");
            AttachmentBlobStore sourceBlobs = new AttachmentBlobStore(sourceDirectory,
                    new AliasKeyAccess(sourceAlias));
            String sourceTaskId = UUID.randomUUID().toString();
            String sourceAttachmentA = UUID.randomUUID().toString();
            String sourceAttachmentB = UUID.randomUUID().toString();
            byte[] payloadA = "first portable attachment from the source key".getBytes(StandardCharsets.UTF_8);
            byte[] payloadB = "second portable attachment from the source key".getBytes(StandardCharsets.UTF_8);
            sourceBlobs.importStream(sourceTaskId, sourceAttachmentA, new ByteArrayInputStream(payloadA),
                    AttachmentLogic.MAX_TOTAL_BYTES, payloadA.length, () -> false);
            sourceBlobs.importStream(sourceTaskId, sourceAttachmentB, new ByteArrayInputStream(payloadB),
                    AttachmentLogic.MAX_TOTAL_BYTES - payloadA.length, payloadB.length, () -> false);
            Task sourceTask = new Task(sourceTaskId, "Portable source marker", "2026-10-07", "high", false,
                    "2026-10-05T10:15:30Z", "2026-10-05T10:15:30Z", Arrays.asList(
                    new AttachmentRef(sourceAttachmentA, "source-a.bin", "application/octet-stream", payloadA.length),
                    new AttachmentRef(sourceAttachmentB, "source-b.bin", "application/octet-stream", payloadB.length)));
            ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
            PortableBackupCodec.writeArchive(archiveOutput, recoveryKey, Collections.singletonList(sourceTask),
                    (task, attachment) -> sourceBlobs.openInput(task.id, attachment.id),
                    new SecureRandom(), () -> false);
            byte[] archive = archiveOutput.toByteArray();
            deleteKeyAlias(sourceAlias);
            check(!hasKeyAlias(sourceAlias), "source attachment Keystore alias is removed before restore");

            File rejectRoot = new File(root, "reject-target");
            check(rejectRoot.mkdirs(), "malformed-archive target root is created");
            IsolatedContext rejectContext = new IsolatedContext(getTargetContext(), rejectRoot);
            AndroidAttachmentStore rejectAttachments = new AndroidAttachmentStore(rejectContext);
            EncryptedTaskStore rejectTasks = new EncryptedTaskStore(rejectContext);
            byte[] damaged = archive.clone();
            damaged[damaged.length - 1] ^= 0x40;
            expectIOException(() -> new PortableBackupManager(rejectContext).restore(
                    new ByteArrayInputStream(damaged), PortableBackupCodec.decodeRecoveryKey(recoveryCode),
                    rejectTasks, rejectAttachments, () -> false),
                    "bad second attachment tag fails before any imported attachment is staged");
            check(rejectTasks.load().isEmpty(), "failed full-archive authentication leaves task snapshot empty");
            File rejectedPayloads = new File(rejectContext.getNoBackupFilesDir(), "attachments");
            check(!rejectedPayloads.exists() || rejectedPayloads.list().length == 0,
                    "all attachments are authenticated before local encrypted blobs are created");

            File targetRoot = new File(root, "target");
            check(targetRoot.mkdirs(), "fresh restore target root is created");
            IsolatedContext targetContext = new IsolatedContext(getTargetContext(), targetRoot);
            AndroidAttachmentStore targetAttachments = new AndroidAttachmentStore(targetContext);
            EncryptedTaskStore targetTasks = new EncryptedTaskStore(targetContext);

            PortableBackupManager diesBeforeSnapshot = new PortableBackupManager(targetContext,
                    new PortableBackupManager.RestoreCheckpoint() {
                        @Override public void afterAttachmentBlobsCommitted() { throw new SimulatedProcessDeath(); }
                        @Override public void afterTaskSnapshotCommitted() { }
                    });
            expectProcessDeath(() -> diesBeforeSnapshot.restore(new ByteArrayInputStream(archive),
                    PortableBackupCodec.decodeRecoveryKey(recoveryCode), targetTasks, targetAttachments,
                    () -> false), "process death after blob commit is injected");
            check(targetTasks.load().isEmpty(), "pre-snapshot interruption leaves the original task snapshot unchanged");
            PortableBackupManager afterRestart = new PortableBackupManager(targetContext);
            afterRestart.reconcile(targetTasks.load(), targetAttachments);
            targetAttachments.cleanupOrphans(Collections.emptySet());
            File targetPayloads = new File(targetContext.getNoBackupFilesDir(), "attachments");
            check(!targetPayloads.exists() || targetPayloads.list().length == 0,
                    "restart reconciliation removes unreferenced committed blobs after pre-snapshot interruption");

            PortableBackupManager diesAfterSnapshot = new PortableBackupManager(targetContext,
                    new PortableBackupManager.RestoreCheckpoint() {
                        @Override public void afterAttachmentBlobsCommitted() { }
                        @Override public void afterTaskSnapshotCommitted() { throw new SimulatedProcessDeath(); }
                    });
            expectProcessDeath(() -> diesAfterSnapshot.restore(new ByteArrayInputStream(archive),
                    PortableBackupCodec.decodeRecoveryKey(recoveryCode), targetTasks, targetAttachments,
                    () -> false), "process death after task snapshot commit is injected");
            List<Task> committedSnapshot = targetTasks.load();
            check(committedSnapshot.size() == 1, "task snapshot commits only after all encrypted blobs are committed");
            afterRestart.reconcile(committedSnapshot, targetAttachments);
            List<Task> recovered = targetTasks.load();
            check(recovered.size() == 1, "restart reconciliation recognizes the complete snapshot and preserves it");
            Task restoredTask = recovered.get(0);
            check(!restoredTask.id.equals(sourceTaskId), "restored task receives a fresh app-owned ID");
            check(restoredTask.attachments.size() == 2
                            && !restoredTask.attachments.get(0).id.equals(sourceAttachmentA)
                            && !restoredTask.attachments.get(1).id.equals(sourceAttachmentB),
                    "restored attachments receive fresh app-owned IDs");
            try (InputStream restoredA = targetAttachments.openDecrypted(restoredTask.id, restoredTask.attachments.get(0).id);
                 InputStream restoredB = targetAttachments.openDecrypted(restoredTask.id, restoredTask.attachments.get(1).id)) {
                check(Arrays.equals(payloadA, readAll(restoredA)) && Arrays.equals(payloadB, readAll(restoredB)),
                        "portable restore re-encrypts authenticated bytes under the destination Android Keystore key");
            }
            check(hasKeyAlias("daymark.attachment-payload.aes-gcm.v1"),
                    "destination Android Keystore attachment key exists independently of the removed source key");
            expectIOException(() -> afterRestart.restore(new ByteArrayInputStream(archive),
                    PortableBackupCodec.decodeRecoveryKey(recoveryCode), targetTasks, targetAttachments,
                    () -> false), "recovered backup ID is blocked from a second import");
            check(targetTasks.load().size() == 1, "duplicate import prevention preserves the one committed task copy");
        } finally {
            deleteKeyAlias(sourceAlias);
            PortableBackupCodec.clear(recoveryKey);
            deleteTree(root);
        }
    }

    private void attachmentProviderPipeAndAndroidCrypto() throws Exception {
        File root = makeTestDirectory("provider-pipe");
        try {
            Context context = new IsolatedContext(getTargetContext(), root);
            File noBackup = context.getNoBackupFilesDir();
            File attachmentsDirectory = new File(noBackup, "attachments");
            String taskId = TASK_ID + "-provider";
            String attachmentId = AttachmentBlobStore.newId();
            byte[] plaintext = new byte[256 * 1024];
            for (int index = 0; index < plaintext.length; index++) {
                plaintext[index] = (byte) (index * 31 + 7);
            }

            AttachmentBlobStore blobs = new AttachmentBlobStore(attachmentsDirectory,
                    new AndroidAttachmentStore.AndroidKeyAccess());
            check(blobs.importStream(taskId, attachmentId, new ByteArrayInputStream(plaintext)) == plaintext.length,
                    "Android Keystore-backed attachment import records measured bytes");
            File ciphertextFile = new File(attachmentsDirectory, attachmentId + ".enc");
            byte[] goodCiphertext = readFile(ciphertextFile);
            check(!Arrays.equals(plaintext, goodCiphertext), "Android Keystore-backed payload is ciphertext at rest");
            check(Arrays.equals(plaintext, readAll(blobs.openInput(taskId, attachmentId))),
                    "Android crypto provider reopens the attachment with its owning task and ID");
            expectNoPlaintext(() -> blobs.openInput(taskId + "-wrong", attachmentId),
                    "Android crypto provider rejects a task-ID substitution without releasing plaintext");

            String displayName = "notes.txt";
            AttachmentRef reference = new AttachmentRef(attachmentId, displayName, "text/plain", plaintext.length);
            Task task = new Task(taskId, "Instrumentation provider transaction", null, "medium", false,
                    "2026-10-05T10:15:30Z", "2026-10-05T10:15:30Z", Collections.singletonList(reference));
            EncryptedTaskStore taskStore = new EncryptedTaskStore(context);
            check(taskStore.load().isEmpty(), "isolated provider test starts with an empty task snapshot");
            taskStore.save(Collections.singletonList(task));

            String authority = context.getPackageName() + ".attachments";
            Uri uri = new Uri.Builder().scheme("content").authority(authority).appendPath(attachmentId).build();
            AttachmentContentProvider provider = new AttachmentContentProvider();
            ProviderInfo providerInfo = new ProviderInfo();
            providerInfo.name = AttachmentContentProvider.class.getName();
            providerInfo.packageName = context.getPackageName();
            providerInfo.authority = authority;
            providerInfo.applicationInfo = context.getApplicationInfo();
            providerInfo.exported = false;
            providerInfo.grantUriPermissions = true;
            provider.attachInfo(context, providerInfo);

            check("text/plain".equals(provider.getType(uri)), "provider MIME type comes from committed task metadata");
            try (Cursor cursor = provider.query(uri, null, null, null, null)) {
                check(cursor != null && cursor.moveToFirst(), "provider query returns committed attachment metadata");
                check(displayName.equals(cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))),
                        "provider display name is sourced from the task snapshot");
                check(cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)) == plaintext.length,
                        "provider size is sourced from the task snapshot");
            }
            ParcelFileDescriptor pipe = provider.openFile(uri, "r");
            byte[] streamed;
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(pipe)) {
                streamed = readAll(input);
            }
            check(Arrays.equals(plaintext, streamed),
                    "actual AttachmentContentProvider pipe streams authenticated task-bound plaintext");
            expectProviderNotFound(() -> provider.openFile(uri, "w"), "provider rejects non-read pipe modes");

            String orphanId = AttachmentBlobStore.newId();
            Uri orphanUri = new Uri.Builder().scheme("content").authority(authority).appendPath(orphanId).build();
            check(provider.getType(orphanUri) == null, "provider does not reveal metadata for an unreferenced ID");
            expectProviderNotFound(() -> provider.openFile(orphanUri, "r"),
                    "provider refuses to open an attachment absent from the task snapshot");

            byte[] modifiedAttachment = goodCiphertext.clone();
            modifiedAttachment[modifiedAttachment.length - 1] ^= 0x01;
            writeFile(ciphertextFile, modifiedAttachment);
            expectNoPlaintext(() -> blobs.openInput(taskId, attachmentId),
                    "Android crypto provider rejects a modified GCM tag before releasing plaintext");

            File taskFile = new File(context.getFilesDir(), "tasks.enc");
            byte[] goodTaskCiphertext = readFile(taskFile);
            byte[] modifiedTaskCiphertext = goodTaskCiphertext.clone();
            modifiedTaskCiphertext[modifiedTaskCiphertext.length - 1] ^= 0x01;
            writeFile(taskFile, modifiedTaskCiphertext);
            check(provider.getType(uri) == null, "corrupt task metadata makes provider lookup fail closed");
            expectProviderNotFound(() -> provider.openFile(uri, "r"),
                    "corrupt task metadata cannot authorize an attachment pipe");
            check(Arrays.equals(modifiedTaskCiphertext, readFile(taskFile)),
                    "provider lookup does not rewrite a corrupt encrypted task snapshot");
        } finally {
            deleteTree(root);
        }
    }

    private void assertTask(Task actual, String id, String title, String dueDate, String priority,
                            boolean completed, String createdAt, String updatedAt) {
        check(id.equals(actual.id), "schema-v1 task ID imports exactly");
        check(title.equals(actual.title), "schema-v1 task title imports as UTF-8 text");
        check((dueDate == null && actual.dueDate == null) || (dueDate != null && dueDate.equals(actual.dueDate)),
                "schema-v1 nullable due date imports exactly");
        check(priority.equals(actual.priority), "schema-v1 priority imports exactly");
        check(completed == actual.completed, "schema-v1 completion flag imports exactly");
        check(createdAt.equals(actual.createdAt), "schema-v1 creation timestamp imports exactly");
        check(updatedAt.equals(actual.updatedAt), "schema-v1 update timestamp imports exactly");
    }

    private void assertTaskEquals(Task expected, Task actual) {
        check(expected.id.equals(actual.id) && expected.title.equals(actual.title)
                        && (expected.dueDate == null ? actual.dueDate == null : expected.dueDate.equals(actual.dueDate))
                        && expected.priority.equals(actual.priority) && expected.completed == actual.completed
                        && expected.createdAt.equals(actual.createdAt) && expected.updatedAt.equals(actual.updatedAt)
                        && actual.attachments.isEmpty(),
                "task fields survive schema-v1 to v2 save and reload");
    }

    private void saveRawEncryptedSnapshot(File file, EncryptedBlobStore.KeyAccess keys, byte[] plaintext)
            throws Exception {
        EncryptedBlobStore store = new EncryptedBlobStore(new PlatformAtomicFileAccess(file), keys);
        check(store.load().isEmpty(), "isolated encrypted snapshot starts empty");
        store.save(plaintext);
    }

    private byte[] loadRawEncryptedSnapshot(File file, EncryptedBlobStore.KeyAccess keys) throws Exception {
        EncryptedBlobStore store = new EncryptedBlobStore(new PlatformAtomicFileAccess(file), keys);
        EncryptedBlobStore.LoadResult loaded = store.load();
        check(!loaded.isEmpty(), "encrypted snapshot is present after save");
        return loaded.plaintext();
    }

    private void expectStorageKind(EncryptedTaskStore store, EncryptedBlobStore.Kind expected, String message)
            throws Exception {
        assertions++;
        try {
            store.load();
            throw new AssertionError(message + ": expected " + expected);
        } catch (EncryptedBlobStore.StorageException failure) {
            if (failure.kind() != expected) {
                throw new AssertionError(message + ": expected " + expected + " but got " + failure.kind(), failure);
            }
        }
    }

    private void expectSaveKind(EncryptedTaskStore store, EncryptedBlobStore.Kind expected, String message)
            throws Exception {
        assertions++;
        try {
            store.save(Collections.emptyList());
            throw new AssertionError(message + ": expected " + expected);
        } catch (EncryptedBlobStore.StorageException failure) {
            if (failure.kind() != expected) {
                throw new AssertionError(message + ": expected " + expected + " but got " + failure.kind(), failure);
            }
        }
    }

    private void expectProviderNotFound(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (FileNotFoundException expected) { }
    }

    private void expectIOException(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message + ": expected IOException");
        } catch (IOException expected) { }
    }

    private void expectProcessDeath(IoOperation operation, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message + ": expected simulated process death");
        } catch (SimulatedProcessDeath expected) { }
    }

    private void expectNoPlaintext(InputOperation operation, String message) throws Exception {
        assertions++;
        int released = 0;
        boolean rejected = false;
        try (InputStream input = operation.open()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0) released += count;
            }
        } catch (IOException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError(message + ": authentication should fail");
        check(released == 0, message + ": observed " + released + " plaintext bytes");
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private byte[] readAsset(String name) throws IOException {
        try (InputStream input = getContext().getAssets().open(name)) {
            return readAll(input);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > 0) output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return readAll(input);
        }
    }

    private static void writeFile(File file, byte[] contents) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(contents);
            output.getFD().sync();
        }
    }

    private static boolean hasKeyAlias(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return keyStore.containsAlias(alias);
    }

    private static void deleteKeyAlias(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias);
    }

    private File makeTestDirectory(String label) throws IOException {
        File root = new File(getTargetContext().getCacheDir(),
                "daymark-instrumentation-" + label + "-" + UUID.randomUUID());
        if (!root.mkdirs()) throw new IOException("Could not create isolated instrumentation directory.");
        return root;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete() && file.exists()) file.deleteOnExit();
    }

    private static String stackTrace(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    private static final class FixedTestKey implements EncryptedBlobStore.KeyAccess {
        private final SecretKey key = new SecretKeySpec(new byte[32], "AES");

        @Override
        public SecretKey loadExistingKey() {
            return key;
        }

        @Override
        public SecretKey createKeyForNewStore() {
            return key;
        }
    }

    private static final class AliasKeyAccess implements AttachmentBlobStore.KeyAccess {
        private final String alias;
        AliasKeyAccess(String alias) { this.alias = alias; }

        @Override
        public SecretKey loadExistingKey() throws IOException {
            try {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                if (!keyStore.containsAlias(alias)) throw new IOException("The source test key is missing.");
                Key key = keyStore.getKey(alias, null);
                if (!(key instanceof SecretKey)) throw new IOException("The source test key is unavailable.");
                return (SecretKey) key;
            } catch (IOException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IOException("The source test key could not be opened.", failure);
            }
        }

        @Override
        public SecretKey createKeyForNewStore() throws IOException {
            try {
                if (hasKeyAlias(alias)) return loadExistingKey();
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build();
                generator.init(spec);
                return generator.generateKey();
            } catch (Exception failure) {
                throw new IOException("A source test Keystore key could not be generated.", failure);
            }
        }
    }

    private static final class SimulatedProcessDeath extends Error { }

    private static final class PlatformAtomicFileAccess implements EncryptedBlobStore.AtomicFileAccess {
        private final AtomicFile atomicFile;
        private final File base;
        private final File backup;
        private final File staged;

        PlatformAtomicFileAccess(File base) {
            this.base = base;
            this.atomicFile = new AtomicFile(base);
            this.backup = new File(base.getPath() + ".bak");
            this.staged = new File(base.getPath() + ".new");
        }

        @Override public boolean hasCommittedSnapshot() { return base.exists() || backup.exists(); }
        @Override public boolean hasAnyArtifacts() { return hasCommittedSnapshot() || staged.exists(); }
        @Override public InputStream openRead() throws IOException { return atomicFile.openRead(); }
        @Override public OutputStream startWrite() throws IOException { return atomicFile.startWrite(); }

        @Override
        public void finishWrite(OutputStream output) throws IOException {
            FileOutputStream fileOutput = (FileOutputStream) output;
            fileOutput.getFD().sync();
            atomicFile.finishWrite(fileOutput);
        }

        @Override
        public void failWrite(OutputStream output) {
            atomicFile.failWrite((FileOutputStream) output);
        }
    }

    private static final class IsolatedContext extends ContextWrapper {
        private final File files;
        private final File noBackup;

        IsolatedContext(Context base, File root) throws IOException {
            super(base);
            files = new File(root, "files");
            noBackup = new File(root, "no-backup");
            if (!files.mkdirs() || !noBackup.mkdirs()) {
                throw new IOException("Could not create isolated application directories.");
            }
        }

        @Override public File getFilesDir() { return files; }
        @Override public File getNoBackupFilesDir() { return noBackup; }
        @Override public Context getApplicationContext() { return this; }
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface InputOperation {
        InputStream open() throws IOException;
    }
}

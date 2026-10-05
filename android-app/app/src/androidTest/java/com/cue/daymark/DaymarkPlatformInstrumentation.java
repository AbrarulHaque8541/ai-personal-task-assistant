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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
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

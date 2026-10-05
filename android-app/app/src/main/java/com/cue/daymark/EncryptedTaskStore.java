package com.cue.daymark;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** App-private authenticated storage; the process lock also serializes separate Activity executors. */
final class EncryptedTaskStore {
    private static final String KEY_ALIAS = "daymark.task-store.aes-gcm.v1";
    private static final Object FILE_ACCESS_LOCK = new Object();
    private final EncryptedBlobStore encryptedStore;
    private boolean loadReady;

    EncryptedTaskStore(Context context) {
        this(new File(context.getFilesDir(), "tasks.enc"), new AndroidKeyAccess());
    }

    /** Uses the production codec and AtomicFile adapter with an isolated path/key adapter. */
    EncryptedTaskStore(File storeFile, EncryptedBlobStore.KeyAccess keyAccess) {
        AtomicFile atomicFile = new AtomicFile(storeFile);
        encryptedStore = new EncryptedBlobStore(
                new AndroidAtomicFileAccess(atomicFile, storeFile), keyAccess);
    }

    List<Task> load() throws Exception {
        synchronized (FILE_ACCESS_LOCK) {
            loadReady = false;
            EncryptedBlobStore.LoadResult loaded = encryptedStore.load();
            if (loaded.isEmpty()) {
                loadReady = true;
                return new ArrayList<>();
            }
            try {
                List<Task> tasks = decodeTasks(loaded.plaintext());
                loadReady = true;
                return tasks;
            } catch (Exception exception) {
                throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.CORRUPT_DATA,
                        "Saved task data is malformed or unsupported.", exception);
            }
        }
    }

    void save(List<Task> tasks) throws Exception {
        synchronized (FILE_ACCESS_LOCK) {
            if (!loadReady) {
                throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.STORE_NOT_VERIFIED,
                        "Task storage must load successfully before it can be changed.");
            }
            byte[] plaintext = encodeTasks(tasks);
            try {
                encryptedStore.save(plaintext);
            } catch (EncryptedBlobStore.StorageException exception) {
                loadReady = false;
                throw exception;
            }
        }
    }

    List<Task> appendAtomically(List<Task> additions) throws Exception {
        synchronized (FILE_ACCESS_LOCK) {
            if (additions == null || !TaskLogic.isValidTaskList(additions)) {
                throw new IOException("The imported task set failed validation.");
            }
            List<Task> latest = load();
            if (additions.isEmpty()) return latest;
            List<Task> combined = new ArrayList<>(latest.size() + additions.size());
            combined.addAll(latest);
            combined.addAll(additions);
            if (!TaskLogic.isValidTaskList(combined)) {
                throw new IOException("The restore would exceed task or attachment limits.");
            }
            save(combined);
            return combined;
        }
    }

    private static KeyStore loadKeyStore() throws EncryptedBlobStore.StorageException {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            return keyStore;
        } catch (Exception exception) {
            throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                    "Android Keystore is unavailable.", exception);
        }
    }

    private static SecretKey existingKey(KeyStore keyStore) throws EncryptedBlobStore.StorageException {
        try {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                        "The saved task encryption key is missing.");
            }
            Key key = keyStore.getKey(KEY_ALIAS, null);
            if (key instanceof SecretKey) return (SecretKey) key;
            throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                    "The saved task encryption key is unusable.");
        } catch (EncryptedBlobStore.StorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                    "The saved task encryption key is unavailable.", exception);
        }
    }

    private static final class AndroidKeyAccess implements EncryptedBlobStore.KeyAccess {
        @Override
        public SecretKey loadExistingKey() throws EncryptedBlobStore.StorageException {
            return existingKey(loadKeyStore());
        }

        @Override
        public SecretKey createKeyForNewStore() throws EncryptedBlobStore.StorageException {
            KeyStore keyStore = loadKeyStore();
            if (keyStoreContainsAlias(keyStore)) return existingKey(keyStore);
            try {
                KeyGenerator generator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                KeyGenParameterSpec specification = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build();
                generator.init(specification);
                return generator.generateKey();
            } catch (Exception exception) {
                throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                        "A new task encryption key could not be created.", exception);
            }
        }

        private boolean keyStoreContainsAlias(KeyStore keyStore) throws EncryptedBlobStore.StorageException {
            try {
                return keyStore.containsAlias(KEY_ALIAS);
            } catch (Exception exception) {
                throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                        "Android Keystore could not be checked.", exception);
            }
        }
    }

    private static final class AndroidAtomicFileAccess implements EncryptedBlobStore.AtomicFileAccess {
        private final AtomicFile atomicFile;
        private final File baseFile;
        private final File legacyBackupFile;
        private final File stagedFile;

        AndroidAtomicFileAccess(AtomicFile atomicFile, File baseFile) {
            this.atomicFile = atomicFile;
            this.baseFile = baseFile;
            this.legacyBackupFile = new File(baseFile.getPath() + ".bak");
            this.stagedFile = new File(baseFile.getPath() + ".new");
        }

        @Override
        public boolean hasCommittedSnapshot() {
            return baseFile.exists() || legacyBackupFile.exists();
        }

        @Override
        public boolean hasAnyArtifacts() {
            return hasCommittedSnapshot() || stagedFile.exists();
        }

        @Override
        public InputStream openRead() throws IOException {
            return atomicFile.openRead();
        }

        @Override
        public OutputStream startWrite() throws IOException {
            return atomicFile.startWrite();
        }

        @Override
        public void finishWrite(OutputStream output) throws IOException {
            FileOutputStream fileOutput = (FileOutputStream) output;
            // AtomicFile.finishWrite logs sync failures on some platform versions instead of
            // throwing them, so surface the sync result before asking it to commit the rename.
            fileOutput.getFD().sync();
            atomicFile.finishWrite(fileOutput);
        }

        @Override
        public void failWrite(OutputStream output) {
            atomicFile.failWrite((FileOutputStream) output);
        }
    }

    private byte[] encodeTasks(List<Task> tasks) throws JSONException {
        if (!TaskLogic.isValidTaskList(tasks)) {
            throw new JSONException("Refusing to save invalid or duplicate task data.");
        }
        JSONArray array = new JSONArray();
        for (Task task : tasks) {
            JSONObject object = new JSONObject();
            object.put("id", task.id);
            object.put("title", task.title);
            object.put("dueDate", task.dueDate == null ? JSONObject.NULL : task.dueDate);
            object.put("priority", task.priority);
            object.put("completed", task.completed);
            object.put("createdAt", task.createdAt);
            object.put("updatedAt", task.updatedAt);
            JSONArray attachments = new JSONArray();
            for (AttachmentRef attachment : task.attachments) {
                JSONObject reference = new JSONObject();
                reference.put("id", attachment.id);
                reference.put("displayName", attachment.displayName);
                reference.put("mimeType", attachment.mimeType);
                reference.put("sizeBytes", attachment.sizeBytes);
                attachments.put(reference);
            }
            object.put("attachments", attachments);
            array.put(object);
        }
        JSONObject document = new JSONObject();
        document.put("version", 2);
        document.put("tasks", array);
        return document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private List<Task> decodeTasks(byte[] plaintext) throws Exception {
        JSONObject document = new JSONObject(new String(plaintext, java.nio.charset.StandardCharsets.UTF_8));
        Object version = document.opt("version");
        if (!TaskSnapshotSchema.isSupportedVersion(version)) {
            throw new IOException("Task data schema version is not supported.");
        }
        boolean hasAttachments = TaskSnapshotSchema.isVersionTwo(version);
        JSONArray array = document.optJSONArray("tasks");
        if (array == null) throw new IOException("Task data is missing its task list.");

        List<Task> result = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null) throw new IOException("Task record is malformed.");
            Object completedValue = object.opt("completed");
            if (!(completedValue instanceof Boolean)) throw new IOException("Task completion value is malformed.");
            Object dueValue = object.opt("dueDate");
            String dueDate = dueValue == JSONObject.NULL ? null : TaskSnapshotSchema.requireString(dueValue);
            List<AttachmentRef> attachments = new ArrayList<>();
            if (hasAttachments) {
                JSONArray references = object.optJSONArray("attachments");
                if (references == null) throw new IOException("Task attachment list is malformed.");
                for (int attachmentIndex = 0; attachmentIndex < references.length(); attachmentIndex++) {
                    JSONObject reference = references.optJSONObject(attachmentIndex);
                    if (reference == null) throw new IOException("Task attachment metadata is malformed.");
                    Object sizeValue = reference.opt("sizeBytes");
                    if (!(sizeValue instanceof Number)) throw new IOException("Task attachment size is malformed.");
                    Number number = (Number) sizeValue;
                    long sizeBytes = number.longValue();
                    if (number.doubleValue() != (double) sizeBytes || sizeBytes < 0) {
                        throw new IOException("Task attachment size is malformed.");
                    }
                    attachments.add(new AttachmentRef(TaskSnapshotSchema.requireString(reference.opt("id")),
                            TaskSnapshotSchema.requireString(reference.opt("displayName")),
                            TaskSnapshotSchema.requireString(reference.opt("mimeType")), sizeBytes));
                }
            }
            Task task = new Task(TaskSnapshotSchema.requireString(object.opt("id")),
                    TaskSnapshotSchema.requireString(object.opt("title")), dueDate,
                    TaskSnapshotSchema.requireString(object.opt("priority")), (Boolean) completedValue,
                    TaskSnapshotSchema.requireString(object.opt("createdAt")),
                    TaskSnapshotSchema.requireString(object.opt("updatedAt")), attachments);
            result.add(task);
        }
        if (!TaskLogic.isValidTaskList(result)) throw new IOException("Task data failed validation.");
        return result;
    }

}

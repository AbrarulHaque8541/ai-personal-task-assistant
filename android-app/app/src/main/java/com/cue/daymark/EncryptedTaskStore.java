package com.cue.daymark;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** App-private, authenticated-encryption storage. All calls run on MainActivity's I/O executor. */
final class EncryptedTaskStore {
    private static final String KEY_ALIAS = "daymark.task-store.aes-gcm.v1";
    private static final byte[] MAGIC = new byte[] { 'D', 'M', 'T', '1' };
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_STORE_BYTES = 10 * 1024 * 1024;
    private final File storeFile;

    EncryptedTaskStore(Context context) {
        storeFile = new File(context.getFilesDir(), "tasks.enc");
    }

    List<Task> load() throws Exception {
        if (!storeFile.exists()) return new ArrayList<>();
        long length = storeFile.length();
        if (length < MAGIC.length + IV_LENGTH_BYTES + 16 || length > MAX_STORE_BYTES) {
            throw new IOException("Encrypted task file has an invalid size.");
        }

        byte[] blob = readAll(storeFile);
        for (int i = 0; i < MAGIC.length; i++) {
            if (blob[i] != MAGIC[i]) throw new IOException("Encrypted task file version is not recognized.");
        }
        byte[] iv = Arrays.copyOfRange(blob, MAGIC.length, MAGIC.length + IV_LENGTH_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(blob, MAGIC.length + IV_LENGTH_BYTES, blob.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(MAGIC);
        byte[] plaintext = cipher.doFinal(ciphertext);
        return decodeTasks(plaintext);
    }

    void save(List<Task> tasks) throws Exception {
        byte[] plaintext = encodeTasks(tasks);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        cipher.updateAAD(MAGIC);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_LENGTH_BYTES) throw new GeneralSecurityException("Unexpected AES-GCM IV.");
        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(MAGIC.length + iv.length + ciphertext.length);
        DataOutputStream data = new DataOutputStream(buffer);
        data.write(MAGIC);
        data.write(iv);
        data.write(ciphertext);
        data.flush();
        byte[] blob = buffer.toByteArray();
        if (blob.length > MAX_STORE_BYTES) throw new IOException("Task storage is full.");

        File temporaryFile = new File(storeFile.getParentFile(), "tasks.enc.tmp");
        try (FileOutputStream output = new FileOutputStream(temporaryFile)) {
            output.write(blob);
            output.getFD().sync();
        }
        try {
            try {
                Files.move(temporaryFile.toPath(), storeFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporaryFile.toPath(), storeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete();
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            Key existing = keyStore.getKey(KEY_ALIAS, null);
            if (existing instanceof SecretKey) return (SecretKey) existing;
            throw new GeneralSecurityException("Stored encryption key is not an AES key.");
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
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
            array.put(object);
        }
        JSONObject document = new JSONObject();
        document.put("version", 1);
        document.put("tasks", array);
        return document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private List<Task> decodeTasks(byte[] plaintext) throws Exception {
        JSONObject document = new JSONObject(new String(plaintext, java.nio.charset.StandardCharsets.UTF_8));
        if (document.optInt("version", -1) != 1) throw new IOException("Task data schema version is not supported.");
        JSONArray array = document.optJSONArray("tasks");
        if (array == null) throw new IOException("Task data is missing its task list.");

        List<Task> result = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null) throw new IOException("Task record is malformed.");
            Object completedValue = object.opt("completed");
            if (!(completedValue instanceof Boolean)) throw new IOException("Task completion value is malformed.");
            Object dueValue = object.opt("dueDate");
            String dueDate = dueValue == null || dueValue == JSONObject.NULL ? null : String.valueOf(dueValue);
            Task task = new Task(object.optString("id", ""), object.optString("title", ""), dueDate,
                    object.optString("priority", ""), (Boolean) completedValue,
                    object.optString("createdAt", ""), object.optString("updatedAt", ""));
            result.add(task);
        }
        if (!TaskLogic.isValidTaskList(result)) throw new IOException("Task data failed validation.");
        return result;
    }

    private static byte[] readAll(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
}

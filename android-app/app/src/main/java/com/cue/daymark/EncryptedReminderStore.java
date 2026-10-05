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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** App-private encrypted reminder records, isolated from the existing task-file schema. */
final class EncryptedReminderStore {
    private static final Object FILE_LOCK = new Object();
    private static final String KEY_ALIAS = "daymark.reminders.aes-gcm.v1";
    private static final byte[] MAGIC = new byte[] { 'D', 'M', 'R', '1' };
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_STORE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REMINDERS = 1000;
    private final File storeFile;

    EncryptedReminderStore(Context context) {
        storeFile = new File(context.getFilesDir(), "reminders.enc");
    }

    List<Reminder> load() throws Exception {
        synchronized (FILE_LOCK) {
            return loadLocked();
        }
    }

    void put(Reminder reminder) throws Exception {
        if (reminder == null || !reminder.isValid()) throw new IOException("Reminder failed validation.");
        synchronized (FILE_LOCK) {
            List<Reminder> reminders = loadLocked();
            boolean replaced = false;
            for (int index = 0; index < reminders.size(); index++) {
                if (reminders.get(index).taskId.equals(reminder.taskId)) {
                    reminders.set(index, reminder);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                if (reminders.size() >= MAX_REMINDERS) throw new IOException("Reminder storage is full.");
                reminders.add(reminder);
            }
            saveLocked(reminders);
        }
    }

    Reminder find(String taskId) throws Exception {
        synchronized (FILE_LOCK) {
            for (Reminder reminder : loadLocked()) {
                if (reminder.taskId.equals(taskId)) return reminder;
            }
            return null;
        }
    }

    boolean remove(String taskId) throws Exception {
        synchronized (FILE_LOCK) {
            List<Reminder> reminders = loadLocked();
            boolean removed = ReminderLogic.removeForTask(reminders, taskId);
            if (removed) saveLocked(reminders);
            return removed;
        }
    }

    Reminder markDelivered(String taskId) throws Exception {
        synchronized (FILE_LOCK) {
            List<Reminder> reminders = loadLocked();
            for (int index = 0; index < reminders.size(); index++) {
                Reminder reminder = reminders.get(index);
                if (reminder.taskId.equals(taskId) && !reminder.delivered) {
                    Reminder delivered = reminder.withDelivered(true);
                    reminders.set(index, delivered);
                    saveLocked(reminders);
                    return delivered;
                }
            }
            return null;
        }
    }

    Reminder snooze(String taskId, long nowMillis) throws Exception {
        synchronized (FILE_LOCK) {
            List<Reminder> reminders = loadLocked();
            for (int index = 0; index < reminders.size(); index++) {
                Reminder reminder = reminders.get(index);
                if (reminder.taskId.equals(taskId) && reminder.delivered) {
                    Reminder next = ReminderLogic.snooze(reminder, nowMillis);
                    reminders.set(index, next);
                    saveLocked(reminders);
                    return next;
                }
            }
            return null;
        }
    }

    boolean updateTaskTitle(String taskId, String title) throws Exception {
        if (title == null || title.trim().isEmpty() || title.length() > 160) {
            throw new IOException("Task title failed reminder validation.");
        }
        synchronized (FILE_LOCK) {
            List<Reminder> reminders = loadLocked();
            for (int index = 0; index < reminders.size(); index++) {
                Reminder reminder = reminders.get(index);
                if (reminder.taskId.equals(taskId)) {
                    reminders.set(index, reminder.withTitle(title));
                    saveLocked(reminders);
                    return true;
                }
            }
            return false;
        }
    }

    List<Reminder> rebaseForCurrentTimezone() throws Exception {
        synchronized (FILE_LOCK) {
            List<Reminder> reminders = loadLocked();
            boolean changed = false;
            java.time.ZoneId zone = TimeZone.getDefault().toZoneId();
            for (int index = 0; index < reminders.size(); index++) {
                Reminder reminder = reminders.get(index);
                Reminder rebased = ReminderLogic.afterTimezoneChange(reminder, zone);
                if (rebased.triggerAtMillis != reminder.triggerAtMillis) {
                    reminders.set(index, rebased);
                    changed = true;
                }
            }
            if (changed) saveLocked(reminders);
            return reminders;
        }
    }

    private List<Reminder> loadLocked() throws Exception {
        if (!storeFile.exists()) return new ArrayList<>();
        long length = storeFile.length();
        if (length < MAGIC.length + IV_LENGTH_BYTES + 16 || length > MAX_STORE_BYTES) {
            throw new IOException("Encrypted reminder file has an invalid size.");
        }
        byte[] blob = readAll(storeFile);
        for (int i = 0; i < MAGIC.length; i++) {
            if (blob[i] != MAGIC[i]) throw new IOException("Encrypted reminder file version is not recognized.");
        }
        byte[] iv = Arrays.copyOfRange(blob, MAGIC.length, MAGIC.length + IV_LENGTH_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(blob, MAGIC.length + IV_LENGTH_BYTES, blob.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(MAGIC);
        return decodeReminders(cipher.doFinal(ciphertext));
    }

    private void saveLocked(List<Reminder> reminders) throws Exception {
        byte[] plaintext = encodeReminders(reminders);
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
        if (blob.length > MAX_STORE_BYTES) throw new IOException("Reminder storage is full.");

        File temporaryFile = new File(storeFile.getParentFile(), "reminders.enc.tmp");
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
            throw new GeneralSecurityException("Stored reminder key is not an AES key.");
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

    private byte[] encodeReminders(List<Reminder> reminders) throws JSONException, IOException {
        if (reminders.size() > MAX_REMINDERS) throw new IOException("Reminder storage is full.");
        JSONArray array = new JSONArray();
        Set<String> taskIds = new HashSet<>();
        for (Reminder reminder : reminders) {
            if (!reminder.isValid() || !taskIds.add(reminder.taskId)) {
                throw new JSONException("Refusing to save invalid or duplicate reminders.");
            }
            JSONObject object = new JSONObject();
            object.put("taskId", reminder.taskId);
            object.put("taskTitle", reminder.taskTitle);
            object.put("mode", reminder.mode);
            object.put("localDateTime", reminder.localDateTime == null ? JSONObject.NULL : reminder.localDateTime);
            object.put("triggerAtMillis", reminder.triggerAtMillis);
            object.put("soundUri", reminder.soundUri == null ? JSONObject.NULL : reminder.soundUri);
            object.put("delivered", reminder.delivered);
            array.put(object);
        }
        JSONObject document = new JSONObject();
        document.put("version", 1);
        document.put("reminders", array);
        return document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private List<Reminder> decodeReminders(byte[] plaintext) throws Exception {
        JSONObject document = new JSONObject(new String(plaintext, java.nio.charset.StandardCharsets.UTF_8));
        if (document.optInt("version", -1) != 1) throw new IOException("Reminder data schema version is not supported.");
        JSONArray array = document.optJSONArray("reminders");
        if (array == null || array.length() > MAX_REMINDERS) throw new IOException("Reminder data is malformed.");
        List<Reminder> result = new ArrayList<>(array.length());
        Set<String> taskIds = new HashSet<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null || !(object.opt("delivered") instanceof Boolean)) {
                throw new IOException("Reminder record is malformed.");
            }
            Object localValue = object.opt("localDateTime");
            Object soundValue = object.opt("soundUri");
            Reminder reminder = new Reminder(
                    object.optString("taskId", ""), object.optString("taskTitle", ""),
                    object.optString("mode", ""),
                    localValue == null || localValue == JSONObject.NULL ? null : String.valueOf(localValue),
                    object.optLong("triggerAtMillis", -1),
                    soundValue == null || soundValue == JSONObject.NULL ? null : String.valueOf(soundValue),
                    (Boolean) object.opt("delivered"));
            if (!reminder.isValid() || !taskIds.add(reminder.taskId)) {
                throw new IOException("Reminder data failed validation.");
            }
            result.add(reminder);
        }
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

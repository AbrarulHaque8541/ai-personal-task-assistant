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
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** App-private encrypted reminder records and cancellation journal, isolated from the task-file schema. */
final class EncryptedReminderStore {
    private static final Object FILE_LOCK = new Object();
    private static final String KEY_ALIAS = "daymark.reminders.aes-gcm.v1";
    private static final byte[] MAGIC = new byte[] { 'D', 'M', 'R', '1' };
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_STORE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REMINDERS = 1000;
    private static final int MAX_TOMBSTONES = 4000;
    private final File storeFile;

    private static final class StoreState {
        final List<Reminder> reminders = new ArrayList<>();
        final List<ReminderTombstone> tombstones = new ArrayList<>();
        int schemaVersion = 3;
        long nextGeneration = 1L;
    }

    EncryptedReminderStore(Context context) {
        storeFile = new File(context.getFilesDir(), "reminders.enc");
    }

    List<Reminder> load() throws Exception {
        synchronized (FILE_LOCK) {
            return new ArrayList<>(loadLocked().reminders);
        }
    }

    List<ReminderTombstone> loadTombstones() throws Exception {
        synchronized (FILE_LOCK) {
            return new ArrayList<>(loadLocked().tombstones);
        }
    }

    /** Persist a new generation; replacing a reminder also leaves a tombstone for the old notification key. */
    Reminder put(Reminder reminder) throws Exception {
        if (reminder == null || !reminder.isValid()) throw new IOException("Reminder failed validation.");
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int existingIndex = indexOf(state.reminders, reminder.taskId);
            Reminder previous = existingIndex < 0 ? null : state.reminders.get(existingIndex);
            if (state.nextGeneration <= 0 || state.nextGeneration == Long.MAX_VALUE
                    || (previous != null && state.nextGeneration >= Long.MAX_VALUE - 1L)) {
                throw new IOException("Reminder generation is outside the supported range.");
            }
            long expectedVersion = state.nextGeneration + (previous == null ? 0L : 1L);
            if (reminder.version > 0 && reminder.version != expectedVersion) {
                throw new IOException("Reminder generation changed before it could be saved.");
            }
            if (previous != null) {
                addTombstone(state, new ReminderTombstone(previous.taskId, previous.version,
                        allocateGeneration(state)));
            }
            Reminder saved = reminder.withVersion(allocateGeneration(state));
            if (previous == null && state.reminders.size() >= MAX_REMINDERS) {
                throw new IOException("Reminder storage is full.");
            }
            if (existingIndex >= 0) state.reminders.set(existingIndex, saved);
            else state.reminders.add(saved);
            saveLocked(state);
            return saved;
        }
    }

    Reminder find(String taskId) throws Exception {
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int index = indexOf(state.reminders, taskId);
            return index < 0 ? null : state.reminders.get(index);
        }
    }

    /** Preview used under ReminderDeliveryLock so a new generation's retry is armed before its commit. */
    long nextVersionForPut(String taskId) throws Exception {
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int activeIndex = indexOf(state.reminders, taskId);
            if (state.nextGeneration <= 0 || state.nextGeneration == Long.MAX_VALUE
                    || (activeIndex >= 0 && state.nextGeneration >= Long.MAX_VALUE - 1L)) {
                throw new IOException("Reminder generation is outside the supported range.");
            }
            long next = state.nextGeneration + (activeIndex >= 0 ? 1L : 0L);
            if (next <= 0 || next == Long.MAX_VALUE) throw new IOException("Reminder generation is outside the supported range.");
            return next;
        }
    }

    ReminderTombstone plannedCancellation(String taskId, long expectedVersion) throws Exception {
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int index = indexOf(state.reminders, taskId);
            if (index < 0) return null;
            Reminder current = state.reminders.get(index);
            if (expectedVersion > 0 && current.version != expectedVersion) return null;
            long revision = state.nextGeneration;
            if (revision <= current.version || revision <= 0 || revision == Long.MAX_VALUE) {
                throw new IOException("Reminder generation is outside the supported range.");
            }
            return new ReminderTombstone(taskId, current.version, revision);
        }
    }

    long nextVersionForSnooze(String taskId, long expectedVersion) throws Exception {
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int index = indexOf(state.reminders, taskId);
            if (index < 0) return -1L;
            Reminder current = state.reminders.get(index);
            if (current.version != expectedVersion || !current.delivered) return -1L;
            if (state.nextGeneration <= 0 || state.nextGeneration >= Long.MAX_VALUE - 1L) {
                throw new IOException("Reminder generation is outside the supported range.");
            }
            return state.nextGeneration + 1L;
        }
    }

    ReminderTombstone findTombstone(String taskId, long cancelledVersion) throws Exception {
        synchronized (FILE_LOCK) {
            for (ReminderTombstone tombstone : loadLocked().tombstones) {
                if (tombstone.taskId.equals(taskId) && tombstone.cancelledVersion == cancelledVersion) {
                    return tombstone;
                }
            }
            return null;
        }
    }

    /** Write the tombstone before callers cancel alarms or the stable notification key. */
    ReminderTombstone cancel(String taskId, long expectedVersion) throws Exception {
        if (taskId == null || taskId.trim().isEmpty()) throw new IOException("Reminder ID is required.");
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int index = indexOf(state.reminders, taskId);
            if (index < 0) return null;
            Reminder current = state.reminders.get(index);
            if (expectedVersion > 0 && current.version != expectedVersion) return null;
            ReminderTombstone tombstone = new ReminderTombstone(taskId, current.version,
                    allocateGeneration(state));
            state.reminders.remove(index);
            addTombstone(state, tombstone);
            saveLocked(state);
            return tombstone;
        }
    }

    boolean removeTombstone(ReminderTombstone expected) throws Exception {
        if (expected == null || !expected.isValid()) return false;
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            boolean changed = state.tombstones.removeIf(existing -> existing.matches(
                    expected.taskId, expected.cancelledVersion, expected.revision));
            if (changed) saveLocked(state);
            return changed;
        }
    }

    Reminder markDeliveryPending(String taskId, long expectedVersion) throws Exception {
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int index = indexOf(state.reminders, taskId);
            if (index < 0) return null;
            Reminder current = state.reminders.get(index);
            if (!ReminderLogic.isCurrentGeneration(current, expectedVersion) || current.delivered) return null;
            if (current.deliveryPending) return current;
            Reminder pending = ReminderLogic.beginDelivery(current);
            if (pending == null) return null;
            state.reminders.set(index, pending);
            saveLocked(state);
            return pending;
        }
    }

    Reminder markDelivered(String taskId, long expectedVersion) throws Exception {
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int index = indexOf(state.reminders, taskId);
            if (index < 0) return null;
            Reminder current = state.reminders.get(index);
            if (!ReminderLogic.isCurrentGeneration(current, expectedVersion)
                    || !current.deliveryPending || current.delivered) return null;
            Reminder delivered = ReminderLogic.completeDelivery(current);
            if (delivered == null) return null;
            state.reminders.set(index, delivered);
            saveLocked(state);
            return delivered;
        }
    }

    Reminder snooze(String taskId, long expectedVersion, long nowMillis) throws Exception {
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int index = indexOf(state.reminders, taskId);
            if (index < 0) return null;
            Reminder current = state.reminders.get(index);
            if (!ReminderLogic.isCurrentGeneration(current, expectedVersion) || !current.delivered) return null;
            long revision = allocateGeneration(state);
            ReminderTombstone tombstone = new ReminderTombstone(taskId, current.version, revision);
            Reminder next = ReminderLogic.snooze(current, nowMillis).withVersion(allocateGeneration(state));
            state.reminders.set(index, next);
            addTombstone(state, tombstone);
            saveLocked(state);
            return next;
        }
    }

    boolean updateTaskTitle(String taskId, String title) throws Exception {
        return updateTaskTitle(taskId, 0L, title);
    }

    boolean updateTaskTitle(String taskId, long expectedVersion, String title) throws Exception {
        if (title == null || title.trim().isEmpty() || title.length() > 160) {
            throw new IOException("Task title failed reminder validation.");
        }
        synchronized (FILE_LOCK) {
            StoreState state = loadLocked();
            int index = indexOf(state.reminders, taskId);
            if (index < 0) return false;
            Reminder current = state.reminders.get(index);
            if (expectedVersion > 0 && current.version != expectedVersion) return false;
            state.reminders.set(index, current.withTitle(title));
            saveLocked(state);
            return true;
        }
    }

    List<Reminder> rebaseForCurrentTimezone() throws Exception {
        synchronized (FILE_LOCK) {
            // Preserve the user's saved instant, zone, and offset; system broadcasts re-arm that same instant.
            return new ArrayList<>(loadLocked().reminders);
        }
    }

    private StoreState loadLocked() throws Exception {
        if (!storeFile.exists()) return new StoreState();
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
        StoreState state = decodeState(cipher.doFinal(ciphertext));
        if (state.schemaVersion != 3) saveLocked(state);
        return state;
    }

    private void saveLocked(StoreState state) throws Exception {
        byte[] plaintext = encodeState(state);
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

    private byte[] encodeState(StoreState state) throws JSONException, IOException {
        if (state.reminders.size() > MAX_REMINDERS || state.tombstones.size() > MAX_TOMBSTONES) {
            throw new IOException("Reminder storage is full.");
        }
        JSONArray reminders = new JSONArray();
        Set<String> taskIds = new HashSet<>();
        for (Reminder reminder : state.reminders) {
            if (!reminder.isValid() || reminder.version <= 0 || !taskIds.add(reminder.taskId)) {
                throw new JSONException("Refusing to save invalid or duplicate reminders.");
            }
            JSONObject object = new JSONObject();
            object.put("taskId", reminder.taskId);
            object.put("taskTitle", reminder.taskTitle);
            object.put("mode", reminder.mode);
            object.put("localDateTime", reminder.localDateTime == null ? JSONObject.NULL : reminder.localDateTime);
            object.put("zoneId", reminder.zoneId == null ? JSONObject.NULL : reminder.zoneId);
            object.put("offsetSeconds", reminder.offsetSeconds == null ? JSONObject.NULL : reminder.offsetSeconds);
            object.put("triggerAtMillis", reminder.triggerAtMillis);
            object.put("soundUri", reminder.soundUri == null ? JSONObject.NULL : reminder.soundUri);
            object.put("deliveryPending", reminder.deliveryPending);
            object.put("delivered", reminder.delivered);
            object.put("generation", reminder.version);
            reminders.put(object);
        }
        JSONArray tombstones = new JSONArray();
        Set<String> tombstoneKeys = new HashSet<>();
        for (ReminderTombstone tombstone : state.tombstones) {
            if (!tombstone.isValid() || !tombstoneKeys.add(tombstone.key())) {
                throw new JSONException("Refusing to save invalid or duplicate reminder tombstones.");
            }
            JSONObject object = new JSONObject();
            object.put("taskId", tombstone.taskId);
            object.put("cancelledGeneration", tombstone.cancelledVersion);
            object.put("revision", tombstone.revision);
            tombstones.put(object);
        }
        JSONObject document = new JSONObject();
        document.put("version", 3);
        document.put("nextGeneration", state.nextGeneration);
        document.put("reminders", reminders);
        document.put("tombstones", tombstones);
        return document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private StoreState decodeState(byte[] plaintext) throws Exception {
        JSONObject document = new JSONObject(new String(plaintext, java.nio.charset.StandardCharsets.UTF_8));
        int schemaVersion = document.optInt("version", -1);
        if (schemaVersion < 1 || schemaVersion > 3) throw new IOException("Reminder data schema version is not supported.");
        JSONArray reminders = document.optJSONArray("reminders");
        if (reminders == null || reminders.length() > MAX_REMINDERS) throw new IOException("Reminder data is malformed.");
        StoreState result = new StoreState();
        result.schemaVersion = schemaVersion;
        if (schemaVersion == 3) {
            Object nextGeneration = document.opt("nextGeneration");
            if (!(nextGeneration instanceof Number)) {
                throw new IOException("Reminder generation counter is malformed.");
            }
            result.nextGeneration = ((Number) nextGeneration).longValue();
        }
        long greatestGeneration = 0L;
        Set<String> taskIds = new HashSet<>();
        for (int index = 0; index < reminders.length(); index++) {
            JSONObject object = reminders.optJSONObject(index);
            if (object == null || !(object.opt("delivered") instanceof Boolean)
                    || (schemaVersion >= 2 && !(object.opt("deliveryPending") instanceof Boolean))) {
                throw new IOException("Reminder record is malformed.");
            }
            Object localValue = object.opt("localDateTime");
            Object soundValue = object.opt("soundUri");
            String taskId = object.optString("taskId", "");
            String taskTitle = object.optString("taskTitle", "");
            String mode = object.optString("mode", "");
            String localDateTime = localValue == null || localValue == JSONObject.NULL
                    ? null : String.valueOf(localValue);
            long triggerAtMillis = object.optLong("triggerAtMillis", -1);
            String soundUri = soundValue == null || soundValue == JSONObject.NULL
                    ? null : String.valueOf(soundValue);
            boolean delivered = (Boolean) object.opt("delivered");
            Reminder reminder;
            if (schemaVersion == 1) {
                reminder = migrateLegacyReminder(taskId, taskTitle, mode, triggerAtMillis, soundUri, delivered)
                        .withVersion(1L);
            } else {
                Object zoneValue = object.opt("zoneId");
                Object offsetValue = object.opt("offsetSeconds");
                String zoneId = zoneValue == null || zoneValue == JSONObject.NULL
                        ? null : String.valueOf(zoneValue);
                Integer offsetSeconds = offsetValue instanceof Number
                        ? ((Number) offsetValue).intValue() : null;
                long generation = schemaVersion >= 3 ? object.optLong("generation", -1L) : 1L;
                reminder = new Reminder(taskId, taskTitle, mode, localDateTime, zoneId,
                        offsetSeconds, triggerAtMillis, soundUri,
                        schemaVersion >= 2 && (Boolean) object.opt("deliveryPending"),
                        delivered, generation);
            }
            if (!reminder.isValid() || reminder.version <= 0 || !taskIds.add(reminder.taskId)) {
                throw new IOException("Reminder data failed validation.");
            }
            result.reminders.add(reminder);
            greatestGeneration = Math.max(greatestGeneration, reminder.version);
        }
        if (schemaVersion == 3) {
            JSONArray tombstones = document.optJSONArray("tombstones");
            if (tombstones == null || tombstones.length() > MAX_TOMBSTONES) {
                throw new IOException("Reminder cancellation journal is malformed.");
            }
            Set<String> tombstoneKeys = new HashSet<>();
            for (int index = 0; index < tombstones.length(); index++) {
                JSONObject object = tombstones.optJSONObject(index);
                if (object == null) throw new IOException("Reminder cancellation record is malformed.");
                ReminderTombstone tombstone = new ReminderTombstone(
                        object.optString("taskId", ""),
                        object.optLong("cancelledGeneration", -1L),
                        object.optLong("revision", -1L));
                if (!tombstone.isValid() || !tombstoneKeys.add(tombstone.key())) {
                    throw new IOException("Reminder cancellation record failed validation.");
                }
                result.tombstones.add(tombstone);
                greatestGeneration = Math.max(greatestGeneration, tombstone.revision);
            }
        }
        if (schemaVersion < 3) {
            if (greatestGeneration == Long.MAX_VALUE) throw new IOException("Reminder generation is outside the supported range.");
            result.nextGeneration = greatestGeneration + 1L;
        } else if (result.nextGeneration <= greatestGeneration || result.nextGeneration <= 0) {
            throw new IOException("Reminder generation counter is outside the supported range.");
        }
        return result;
    }

    private int indexOf(List<Reminder> reminders, String taskId) {
        if (taskId == null) return -1;
        for (int index = 0; index < reminders.size(); index++) {
            if (taskId.equals(reminders.get(index).taskId)) return index;
        }
        return -1;
    }

    private void addTombstone(StoreState state, ReminderTombstone tombstone) throws IOException {
        for (ReminderTombstone existing : state.tombstones) {
            if (existing.key().equals(tombstone.key())) return;
        }
        if (state.tombstones.size() >= MAX_TOMBSTONES) {
            throw new IOException("Reminder cancellation journal is full; reconcile reminders before changing them.");
        }
        state.tombstones.add(tombstone);
    }

    private long allocateGeneration(StoreState state) throws IOException {
        if (state.nextGeneration <= 0 || state.nextGeneration == Long.MAX_VALUE) {
            throw new IOException("Reminder generation is outside the supported range.");
        }
        return state.nextGeneration++;
    }

    private Reminder migrateLegacyReminder(String taskId, String taskTitle, String mode,
                                           long triggerAtMillis, String soundUri, boolean delivered) throws IOException {
        if (Reminder.MODE_TIMER.equals(mode)) {
            return new Reminder(taskId, taskTitle, mode, null, null, null,
                    triggerAtMillis, soundUri, false, delivered);
        }
        if (!Reminder.MODE_LOCAL_DATE_TIME.equals(mode) || triggerAtMillis <= 0) {
            throw new IOException("Legacy reminder data failed validation.");
        }
        try {
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime actual = Instant.ofEpochMilli(triggerAtMillis).atZone(zone);
            // Older releases shifted a DST-gap entry implicitly; migrate its actual scheduled display time.
            String migratedLocal = actual.toLocalDateTime().toString();
            return new Reminder(taskId, taskTitle, mode, migratedLocal, zone.getId(),
                    actual.getOffset().getTotalSeconds(), triggerAtMillis, soundUri, false, delivered);
        } catch (DateTimeException exception) {
            throw new IOException("Legacy reminder time is outside the supported range.", exception);
        }
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

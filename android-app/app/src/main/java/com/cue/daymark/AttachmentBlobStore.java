package com.cue.daymark;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Streaming encrypted payload store. Provider URIs and provider paths never enter this class. */
final class AttachmentBlobStore {
    private static final byte[] MAGIC = new byte[] { 'D', 'M', 'A', '2' };
    private static final byte[] AAD_DOMAIN = "daymark.attachment.payload.v2".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int HEADER_BYTES = MAGIC.length + IV_BYTES;
    private static final int OVERHEAD_BYTES = HEADER_BYTES + TAG_BYTES;
    private static final long SPACE_RESERVE_BYTES = 256L * 1024L;
    private static final long MAX_TOTAL_STORAGE_BYTES = AttachmentLogic.MAX_TOTAL_BYTES
            + (long) AttachmentLogic.MAX_TOTAL_COUNT * OVERHEAD_BYTES;
    private final File directory;
    private final KeyAccess keys;
    private final SpaceAccess space;

    AttachmentBlobStore(File directory, KeyAccess keys) {
        this(directory, keys, File::getUsableSpace);
    }

    AttachmentBlobStore(File directory, KeyAccess keys, SpaceAccess space) {
        this.directory = directory;
        this.keys = keys;
        this.space = space;
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    long importStream(String taskId, String id, InputStream source) throws IOException {
        return importStream(taskId, id, source, AttachmentLogic.MAX_TOTAL_BYTES, -1,
                () -> false);
    }

    long importStream(String taskId, String id, InputStream source, long remainingTotalBytes,
                      long expectedBytes, CancellationCheck cancellation) throws IOException {
        requireTaskId(taskId);
        requireId(id);
        if (source == null) throw new IOException("The selected file could not be opened.");
        if (remainingTotalBytes < 0 || remainingTotalBytes > AttachmentLogic.MAX_TOTAL_BYTES) {
            throw new StorageLimitException();
        }
        if (expectedBytes < -1) throw new IOException("The selected file size is invalid.");
        if (expectedBytes > AttachmentLogic.MAX_FILE_BYTES) throw new FileLimitException();
        if (expectedBytes >= 0 && expectedBytes > remainingTotalBytes) throw new StorageLimitException();
        if (cancellation != null && cancellation.isCancelled()) throw new CancelledException();
        ensureDirectory();
        File destination = payloadFile(id);
        if (destination.exists()) throw new IOException("The attachment identifier is already in use.");
        int count = storedCount();
        if (count >= AttachmentLogic.MAX_TOTAL_COUNT) throw new StorageLimitException();
        long existingBytes = storedBytes();
        if (existingBytes > MAX_TOTAL_STORAGE_BYTES - OVERHEAD_BYTES) throw new StorageLimitException();
        long requiredFree = (expectedBytes >= 0 ? expectedBytes : 32L * 1024L)
                + OVERHEAD_BYTES + SPACE_RESERVE_BYTES;
        checkAvailableSpace(requiredFree);

        File staging = new File(directory, id + ".pending");
        CipherOutputStream encrypted = null;
        try {
            SecretKey key = hasStoredPayload() ? keys.loadExistingKey() : keys.createKeyForNewStore();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(associatedData(taskId, id));
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != IV_BYTES) throw new IOException("The attachment cipher is unavailable.");

            long copied = 0;
            try (FileOutputStream raw = new FileOutputStream(staging)) {
                raw.write(MAGIC);
                raw.write(iv);
                encrypted = new CipherOutputStream(raw, cipher);
                byte[] buffer = new byte[32 * 1024];
                while (true) {
                    if (cancellation != null && cancellation.isCancelled()) throw new CancelledException();
                    int read = source.read(buffer);
                    if (read == -1) break;
                    if (cancellation != null && cancellation.isCancelled()) throw new CancelledException();
                    if (read == 0) continue;
                    if (copied > AttachmentLogic.MAX_FILE_BYTES - read) throw new FileLimitException();
                    if (copied > remainingTotalBytes - read) throw new StorageLimitException();
                    if (existingBytes + OVERHEAD_BYTES + copied + read > MAX_TOTAL_STORAGE_BYTES) {
                        throw new StorageLimitException();
                    }
                    checkAvailableSpace(read + OVERHEAD_BYTES + SPACE_RESERVE_BYTES);
                    encrypted.write(buffer, 0, read);
                    copied += read;
                }
                if (cancellation != null && cancellation.isCancelled()) throw new CancelledException();
                encrypted.close();
                encrypted = null;
            }
            try (RandomAccessFile sync = new RandomAccessFile(staging, "rw")) {
                sync.getFD().sync();
            }
            if (cancellation != null && cancellation.isCancelled()) throw new CancelledException();
            if (!staging.renameTo(destination)) throw new IOException("The attachment could not be committed.");
            if (destination.length() != copied + OVERHEAD_BYTES) {
                destination.delete();
                throw new IOException("The committed attachment size could not be verified.");
            }
            return copied;
        } catch (FileLimitException | StorageLimitException | StorageSpaceException | CancelledException exception) {
            throw exception;
        } catch (IOException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw new IOException("Attachment encryption is unavailable.", exception);
        } finally {
            if (encrypted != null) {
                try { encrypted.close(); } catch (IOException ignored) { }
            }
            if (staging.exists() && !staging.delete()) staging.deleteOnExit();
        }
    }

    InputStream openInput(String taskId, String id) throws IOException {
        requireTaskId(taskId);
        requireId(id);
        File file = payloadFile(id);
        if (!file.isFile()) throw new IOException("The attachment file is missing.");
        if (file.length() < OVERHEAD_BYTES
                || file.length() > AttachmentLogic.MAX_FILE_BYTES + OVERHEAD_BYTES) {
            throw new IOException("The attachment file has an invalid size.");
        }
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] header = readExactly(input, HEADER_BYTES);
            for (int index = 0; index < MAGIC.length; index++) {
                if (header[index] != MAGIC[index]) throw new IOException("The attachment format is not recognized.");
            }
            byte[] iv = Arrays.copyOfRange(header, MAGIC.length, HEADER_BYTES);
            SecretKey key = keys.loadExistingKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * 8, iv));
            cipher.updateAAD(associatedData(taskId, id));
            return new CipherInputStream(input, cipher);
        } catch (IOException exception) {
            input.close();
            throw exception;
        } catch (GeneralSecurityException exception) {
            input.close();
            throw new IOException("Attachment decryption is unavailable.", exception);
        }
    }

    boolean exists(String id) {
        try {
            requireId(id);
            return payloadFile(id).isFile();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    void delete(String id) throws IOException {
        requireId(id);
        File file = payloadFile(id);
        if (file.exists() && !file.delete()) throw new IOException("The attachment could not be removed.");
        File staging = new File(directory, id + ".pending");
        if (staging.exists() && !staging.delete()) throw new IOException("The incomplete attachment could not be removed.");
    }

    int cleanupOrphans(Set<String> referencedIds) throws IOException {
        ensureDirectory();
        int removed = 0;
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("The attachment folder could not be read.");
        for (File file : files) {
            String name = file.getName();
            boolean pending = name.endsWith(".pending");
            boolean payload = name.endsWith(".enc");
            if (!pending && !payload) continue;
            String id = name.substring(0, name.lastIndexOf('.'));
            boolean validId = AttachmentLogic.isValidId(id);
            boolean keep = !pending && payload && validId && referencedIds != null && referencedIds.contains(id);
            if (!keep) {
                if (!file.delete() && file.exists()) throw new IOException("An unused attachment could not be cleaned up.");
                removed++;
            }
        }
        return removed;
    }

    private boolean hasStoredPayload() throws IOException {
        return storedCount() > 0;
    }

    private int storedCount() throws IOException {
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".enc"));
        if (files == null) throw new IOException("The attachment folder could not be enumerated.");
        return files.length;
    }

    private long storedBytes() throws IOException {
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".enc"));
        if (files == null) throw new IOException("The attachment folder could not be enumerated.");
        long total = 0;
        for (File file : files) {
            long length = file.length();
            if (length < 0 || total > MAX_TOTAL_STORAGE_BYTES - length) throw new StorageLimitException();
            total += length;
        }
        return total;
    }

    private void checkAvailableSpace(long required) throws StorageSpaceException {
        long available = space.availableBytes(directory);
        if (available <= 0 || available < required) throw new StorageSpaceException();
    }

    private File payloadFile(String id) {
        return new File(directory, id + ".enc");
    }

    private void ensureDirectory() throws IOException {
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("The attachment folder is unavailable.");
        if (!directory.isDirectory()) throw new IOException("The attachment folder is unavailable.");
    }

    private static byte[] readExactly(InputStream input, int size) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        byte[] buffer = new byte[size];
        while (output.size() < size) {
            int read = input.read(buffer, 0, Math.min(buffer.length, size - output.size()));
            if (read < 0) throw new IOException("The attachment file is truncated.");
            if (read > 0) output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static byte[] associatedData(String taskId, String id) {
        byte[] taskBytes = taskId.getBytes(StandardCharsets.UTF_8);
        byte[] idBytes = id.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer data = ByteBuffer.allocate(AAD_DOMAIN.length + Integer.BYTES + MAGIC.length
                + Integer.BYTES + taskBytes.length + Integer.BYTES + idBytes.length);
        data.put(AAD_DOMAIN);
        data.putInt(MAGIC.length).put(MAGIC);
        data.putInt(taskBytes.length).put(taskBytes);
        data.putInt(idBytes.length).put(idBytes);
        return data.array();
    }

    private static void requireTaskId(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid owning task identifier.");
        }
    }

    private static void requireId(String id) {
        if (!AttachmentLogic.isValidId(id)) throw new IllegalArgumentException("Invalid app-owned attachment identifier.");
    }

    interface KeyAccess {
        SecretKey loadExistingKey() throws IOException;
        SecretKey createKeyForNewStore() throws IOException;
    }

    interface SpaceAccess {
        long availableBytes(File directory);
    }

    interface CancellationCheck {
        boolean isCancelled();
    }

    static final class FileLimitException extends IOException {
        FileLimitException() { super("An attachment exceeds the per-file limit."); }
    }

    static final class StorageLimitException extends IOException {
        StorageLimitException() { super("The attachment storage limit has been reached."); }
    }

    static final class StorageSpaceException extends IOException {
        StorageSpaceException() { super("There is not enough free space to import this attachment safely."); }
    }

    static final class CancelledException extends IOException {
        CancelledException() { super("Attachment import cancelled."); }
    }
}

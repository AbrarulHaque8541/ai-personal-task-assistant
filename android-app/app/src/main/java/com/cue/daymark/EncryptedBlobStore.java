package com.cue.daymark;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Authenticated encrypted-blob protocol; platform file/key access is injected. */
final class EncryptedBlobStore {
    private static final byte[] MAGIC = new byte[] { 'D', 'M', 'T', '1' };
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BYTES = 16;
    private static final int HEADER_LENGTH = MAGIC.length + IV_LENGTH_BYTES;
    private static final int MIN_BLOB_LENGTH = HEADER_LENGTH + TAG_LENGTH_BYTES;
    private static final int GCM_TAG_BITS = TAG_LENGTH_BYTES * 8;
    private static final int MAX_STORE_BYTES = 10 * 1024 * 1024;

    private final AtomicFileAccess file;
    private final KeyAccess keys;
    private boolean loadedSuccessfully;
    private boolean loadedExistingSnapshot;

    EncryptedBlobStore(AtomicFileAccess file, KeyAccess keys) {
        this.file = file;
        this.keys = keys;
    }

    LoadResult load() throws StorageException {
        loadedSuccessfully = false;
        loadedExistingSnapshot = false;

        if (!file.hasCommittedSnapshot()) {
            if (file.hasAnyArtifacts()) {
                throw new StorageException(Kind.CORRUPT_DATA,
                        "An incomplete encrypted task write was found.");
            }
            loadedSuccessfully = true;
            return LoadResult.empty();
        }

        SecretKey key = keys.loadExistingKey();
        byte[] blob;
        try (InputStream input = file.openRead()) {
            blob = readBounded(input);
        } catch (StorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new StorageException(Kind.STORAGE_READ_FAILED,
                    "The encrypted task file could not be read.", exception);
        }
        if (blob.length < MIN_BLOB_LENGTH || blob.length > MAX_STORE_BYTES) {
            throw new StorageException(Kind.CORRUPT_DATA,
                    "The encrypted task file has an invalid size.");
        }
        for (int index = 0; index < MAGIC.length; index++) {
            if (blob[index] != MAGIC[index]) {
                throw new StorageException(Kind.CORRUPT_DATA,
                        "The encrypted task file format is not recognized.");
            }
        }

        byte[] iv = Arrays.copyOfRange(blob, MAGIC.length, HEADER_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(blob, HEADER_LENGTH, blob.length);
        byte[] plaintext = decrypt(key, iv, ciphertext);
        loadedSuccessfully = true;
        loadedExistingSnapshot = true;
        return LoadResult.present(plaintext);
    }

    void save(byte[] plaintext) throws StorageException {
        if (!loadedSuccessfully) {
            throw new StorageException(Kind.STORE_NOT_VERIFIED,
                    "Task storage must load successfully before it can be changed.");
        }

        SecretKey key;
        if (loadedExistingSnapshot) {
            key = keys.loadExistingKey();
        } else {
            if (file.hasAnyArtifacts()) {
                loadedSuccessfully = false;
                throw new StorageException(Kind.CORRUPT_DATA,
                        "An encrypted task file appeared after the empty store was loaded.");
            }
            key = keys.createKeyForNewStore();
        }

        byte[] blob = encrypt(key, plaintext);
        writeAtomically(blob);
        loadedSuccessfully = true;
        loadedExistingSnapshot = true;
    }

    private byte[] decrypt(SecretKey key, byte[] iv, byte[] ciphertext) throws StorageException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(MAGIC);
            return cipher.doFinal(ciphertext);
        } catch (InvalidKeyException exception) {
            throw new StorageException(Kind.KEY_UNAVAILABLE,
                    "The saved task encryption key is unusable.", exception);
        } catch (BadPaddingException | IllegalBlockSizeException exception) {
            throw new StorageException(Kind.AUTHENTICATION_FAILED,
                    "The encrypted task data could not be authenticated.", exception);
        } catch (GeneralSecurityException exception) {
            throw new StorageException(Kind.CRYPTO_UNAVAILABLE,
                    "The encrypted task data could not be decrypted.", exception);
        }
    }

    private byte[] encrypt(SecretKey key, byte[] plaintext) throws StorageException {
        if (plaintext.length > MAX_STORE_BYTES - MIN_BLOB_LENGTH) {
            throw new StorageException(Kind.STORAGE_LIMIT,
                    "The task data exceeds the encrypted-store size limit.");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(MAGIC);
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != IV_LENGTH_BYTES) {
                throw new GeneralSecurityException("Unexpected AES-GCM IV length.");
            }
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(HEADER_LENGTH + ciphertext.length);
            DataOutputStream data = new DataOutputStream(buffer);
            data.write(MAGIC);
            data.write(iv);
            data.write(ciphertext);
            data.flush();
            byte[] blob = buffer.toByteArray();
            if (blob.length > MAX_STORE_BYTES) {
                throw new StorageException(Kind.STORAGE_LIMIT,
                        "The task data exceeds the encrypted-store size limit.");
            }
            return blob;
        } catch (StorageException exception) {
            throw exception;
        } catch (InvalidKeyException exception) {
            throw new StorageException(Kind.KEY_UNAVAILABLE,
                    "The task encryption key is unusable.", exception);
        } catch (GeneralSecurityException | IOException exception) {
            throw new StorageException(Kind.CRYPTO_UNAVAILABLE,
                    "The task data could not be encrypted.", exception);
        }
    }

    private void writeAtomically(byte[] blob) throws StorageException {
        OutputStream output = null;
        try {
            output = file.startWrite();
            output.write(blob);
            output.flush();
            file.finishWrite(output);
            output = null;
        } catch (Exception exception) {
            if (output != null) {
                try {
                    file.failWrite(output);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
            }
            loadedSuccessfully = false;
            throw new StorageException(Kind.WRITE_FAILED,
                    "The encrypted task change could not be committed.", exception);
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_STORE_BYTES) {
                throw new StorageException(Kind.CORRUPT_DATA,
                        "The encrypted task file exceeds the size limit.");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    interface AtomicFileAccess {
        /** True when a committed base file or recoverable legacy backup exists. */
        boolean hasCommittedSnapshot();

        /** True for any base, legacy backup, or interrupted new-file artifact. */
        boolean hasAnyArtifacts();

        InputStream openRead() throws IOException;
        OutputStream startWrite() throws IOException;
        void finishWrite(OutputStream output) throws IOException;
        void failWrite(OutputStream output);
    }

    interface KeyAccess {
        SecretKey loadExistingKey() throws StorageException;
        SecretKey createKeyForNewStore() throws StorageException;
    }

    static final class LoadResult {
        private final boolean empty;
        private final byte[] plaintext;

        private LoadResult(boolean empty, byte[] plaintext) {
            this.empty = empty;
            this.plaintext = plaintext;
        }

        static LoadResult empty() {
            return new LoadResult(true, null);
        }

        static LoadResult present(byte[] plaintext) {
            return new LoadResult(false, Arrays.copyOf(plaintext, plaintext.length));
        }

        boolean isEmpty() {
            return empty;
        }

        byte[] plaintext() {
            if (empty) throw new IllegalStateException("An empty store has no plaintext snapshot.");
            return Arrays.copyOf(plaintext, plaintext.length);
        }
    }

    enum Kind {
        KEY_UNAVAILABLE,
        AUTHENTICATION_FAILED,
        CORRUPT_DATA,
        STORAGE_READ_FAILED,
        WRITE_FAILED,
        STORE_NOT_VERIFIED,
        STORAGE_LIMIT,
        CRYPTO_UNAVAILABLE
    }

    static final class StorageException extends IOException {
        private final Kind kind;

        StorageException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }

        StorageException(Kind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        Kind kind() {
            return kind;
        }
    }
}

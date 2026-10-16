package com.abrar.daymark;

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
    /** Legacy v1 on-disk header: magic + IV + ciphertext, bound to the original key alias. */
    private static final byte[] MAGIC = new byte[] { 'D', 'M', 'T', '1' };
    /** Versioned header: magic + keyVersion(2, big-endian) + IV + ciphertext. */
    private static final byte[] MAGIC_V2 = new byte[] { 'D', 'M', 'T', '2' };
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BYTES = 16;
    private static final int KEY_VERSION_LENGTH_BYTES = 2;
    private static final int HEADER_LENGTH = MAGIC.length + IV_LENGTH_BYTES;
    private static final int HEADER_LENGTH_V2 = MAGIC_V2.length + KEY_VERSION_LENGTH_BYTES + IV_LENGTH_BYTES;
    private static final int MIN_BLOB_LENGTH = HEADER_LENGTH + TAG_LENGTH_BYTES;
    private static final int MIN_BLOB_LENGTH_V2 = HEADER_LENGTH_V2 + TAG_LENGTH_BYTES;
    private static final int GCM_TAG_BITS = TAG_LENGTH_BYTES * 8;
    private static final int MAX_STORE_BYTES = 10 * 1024 * 1024;

    private final AtomicFileAccess file;
    private final KeyAccess keys;
    private boolean loadedSuccessfully;
    private boolean loadedExistingSnapshot;
    private byte[] loadedBlob;
    private int loadedKeyVersion;

    EncryptedBlobStore(AtomicFileAccess file, KeyAccess keys) {
        this.file = file;
        this.keys = keys;
    }

    LoadResult load() throws StorageException {
        loadedSuccessfully = false;
        loadedExistingSnapshot = false;
        loadedBlob = null;
        loadedKeyVersion = 0;

        if (!file.hasCommittedSnapshot()) {
            if (file.hasAnyArtifacts()) {
                throw new StorageException(Kind.CORRUPT_DATA,
                        "An incomplete encrypted task write was found.");
            }
            loadedSuccessfully = true;
            return LoadResult.empty();
        }

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

        byte[] iv;
        byte[] ciphertext;
        byte[] aad;
        SecretKey key;
        int keyVersion;
        if (hasMagic(blob, MAGIC_V2)) {
            if (blob.length < MIN_BLOB_LENGTH_V2) {
                throw new StorageException(Kind.CORRUPT_DATA,
                        "The encrypted task file has an invalid size.");
            }
            keyVersion = readKeyVersion(blob);
            if (keyVersion < 1) {
                throw new StorageException(Kind.CORRUPT_DATA,
                        "The encrypted task file format is not recognized.");
            }
            key = keys.loadKeyForVersion(keyVersion);
            iv = Arrays.copyOfRange(blob, MAGIC_V2.length + KEY_VERSION_LENGTH_BYTES, HEADER_LENGTH_V2);
            ciphertext = Arrays.copyOfRange(blob, HEADER_LENGTH_V2, blob.length);
            aad = versionedAAD(keyVersion);
        } else if (hasMagic(blob, MAGIC)) {
            // Legacy DMT1 snapshots stay readable with the original key alias.
            key = keys.loadExistingKey();
            keyVersion = 1;
            iv = Arrays.copyOfRange(blob, MAGIC.length, HEADER_LENGTH);
            ciphertext = Arrays.copyOfRange(blob, HEADER_LENGTH, blob.length);
            aad = MAGIC;
        } else {
            throw new StorageException(Kind.CORRUPT_DATA,
                    "The encrypted task file format is not recognized.");
        }

        byte[] plaintext = decrypt(key, aad, iv, ciphertext);
        loadedSuccessfully = true;
        loadedExistingSnapshot = true;
        loadedBlob = Arrays.copyOf(blob, blob.length);
        loadedKeyVersion = keyVersion;
        return LoadResult.present(plaintext);
    }

    void save(byte[] plaintext) throws StorageException {
        if (!loadedSuccessfully) {
            throw new StorageException(Kind.STORE_NOT_VERIFIED,
                    "Task storage must load successfully before it can be changed.");
        }

        SecretKey key;
        int keyVersion;
        if (loadedExistingSnapshot) {
            verifyLoadedSnapshotUnchanged();
            key = keys.loadKeyForVersion(loadedKeyVersion);
            keyVersion = loadedKeyVersion;
        } else {
            if (file.hasAnyArtifacts()) {
                loadedSuccessfully = false;
                throw new StorageException(Kind.CORRUPT_DATA,
                        "An encrypted task file appeared after the empty store was loaded.");
            }
            key = keys.createKeyForNewStore();
            keyVersion = keys.newStoreKeyVersion();
        }

        byte[] blob = encrypt(key, keyVersion, plaintext);
        writeAtomically(blob);
        loadedSuccessfully = true;
        loadedExistingSnapshot = true;
        loadedBlob = Arrays.copyOf(blob, blob.length);
        loadedKeyVersion = keyVersion;
    }

    /**
     * Re-encrypts the caller's current plaintext snapshot under a fresh key.
     * The committed snapshot is only replaced after the new blob is written and
     * read back; the retired key version is destroyed only after that commit.
     * Rotation of an empty store is a no-op.
     */
    void rotateKey(byte[] plaintext) throws StorageException {
        if (!loadedSuccessfully) {
            throw new StorageException(Kind.STORE_NOT_VERIFIED,
                    "Task storage must load successfully before it can be changed.");
        }
        if (!loadedExistingSnapshot) {
            return;
        }
        verifyLoadedSnapshotUnchanged();
        // Fail closed if the current key is already unusable, before creating a replacement.
        keys.loadKeyForVersion(loadedKeyVersion);
        VersionedKey replacement = keys.createReplacementKey();
        byte[] blob = encrypt(replacement.key, replacement.version, plaintext);
        writeAtomically(blob);
        int retiredKeyVersion = loadedKeyVersion;
        loadedSuccessfully = true;
        loadedExistingSnapshot = true;
        loadedBlob = Arrays.copyOf(blob, blob.length);
        loadedKeyVersion = replacement.version;
        try {
            keys.destroyRetiredKey(retiredKeyVersion);
        } catch (Exception keyStoreFailure) {
            // Retirement is best-effort: the committed blob no longer needs the retired key.
        }
    }

    private byte[] decrypt(SecretKey key, byte[] aad, byte[] iv, byte[] ciphertext) throws StorageException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad);
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

    private byte[] encrypt(SecretKey key, int keyVersion, byte[] plaintext) throws StorageException {
        if (keyVersion < 1) {
            throw new StorageException(Kind.CRYPTO_UNAVAILABLE,
                    "The task encryption key version is invalid.");
        }
        if (plaintext.length > MAX_STORE_BYTES - MIN_BLOB_LENGTH_V2) {
            throw new StorageException(Kind.STORAGE_LIMIT,
                    "The task data exceeds the encrypted-store size limit.");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(versionedAAD(keyVersion));
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != IV_LENGTH_BYTES) {
                throw new GeneralSecurityException("Unexpected AES-GCM IV length.");
            }
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(HEADER_LENGTH_V2 + ciphertext.length);
            DataOutputStream data = new DataOutputStream(buffer);
            data.write(MAGIC_V2);
            data.writeShort(keyVersion);
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

    private static boolean hasMagic(byte[] blob, byte[] magic) {
        if (blob.length < magic.length) return false;
        for (int index = 0; index < magic.length; index++) {
            if (blob[index] != magic[index]) return false;
        }
        return true;
    }

    private static int readKeyVersion(byte[] blob) {
        int offset = MAGIC_V2.length;
        return ((blob[offset] & 0xFF) << 8) | (blob[offset + 1] & 0xFF);
    }

    private static byte[] versionedAAD(int keyVersion) {
        byte[] aad = Arrays.copyOf(MAGIC_V2, MAGIC_V2.length + KEY_VERSION_LENGTH_BYTES);
        aad[MAGIC_V2.length] = (byte) (keyVersion >>> 8);
        aad[MAGIC_V2.length + 1] = (byte) keyVersion;
        return aad;
    }

    private void verifyLoadedSnapshotUnchanged() throws StorageException {
        if (loadedBlob == null || !file.hasCommittedSnapshot()) {
            loadedSuccessfully = false;
            throw new StorageException(Kind.STORE_NOT_VERIFIED,
                    "The committed encrypted snapshot is no longer the one that was loaded.");
        }
        try (InputStream input = file.openRead()) {
            byte[] currentBlob = readBounded(input);
            if (!Arrays.equals(loadedBlob, currentBlob)) {
                loadedSuccessfully = false;
                throw new StorageException(Kind.STORE_NOT_VERIFIED,
                        "The committed encrypted snapshot changed after it was loaded.");
            }
        } catch (StorageException exception) {
            loadedSuccessfully = false;
            throw exception;
        } catch (IOException exception) {
            loadedSuccessfully = false;
            throw new StorageException(Kind.STORAGE_READ_FAILED,
                    "The committed encrypted snapshot could not be rechecked before saving.", exception);
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
            // AtomicFile may log a failed rename without surfacing it to this caller.
            // Verify that the visible committed snapshot is exactly the one requested.
            try (InputStream committed = file.openRead()) {
                byte[] committedBlob = readBounded(committed);
                if (!Arrays.equals(blob, committedBlob)) {
                    throw new IOException("Committed encrypted snapshot did not match the requested write.");
                }
            }
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

        /** Returns the key bound to a versioned DMT2 blob header. */
        default SecretKey loadKeyForVersion(int keyVersion) throws StorageException {
            return loadExistingKey();
        }

        /** Key version stamped into blobs written for a brand-new store. */
        default int newStoreKeyVersion() throws StorageException {
            return 1;
        }

        /** Creates the replacement key used by rotation; fails closed when unsupported. */
        default VersionedKey createReplacementKey() throws StorageException {
            throw new StorageException(Kind.KEY_UNAVAILABLE,
                    "Task key rotation is not supported by this key provider.");
        }

        /** Best-effort destruction of a key version retired by a completed rotation. */
        default void destroyRetiredKey(int keyVersion) throws StorageException {
        }
    }

    static final class VersionedKey {
        final SecretKey key;
        final int version;

        VersionedKey(SecretKey key, int version) {
            this.key = key;
            this.version = version;
        }
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

package com.abrar.daymark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class EncryptedBlobStoreSmoke {
    private static int assertions;

    private EncryptedBlobStoreSmoke() { }

    public static void main(String[] args) throws Exception {
        validEmptyStoreIsDifferentFromUnreadableStore();
        truncatedCiphertextIsRejectedWithoutChangingBytes();
        tamperedCiphertextIsRejectedWithoutChangingBytes();
        missingKeyDoesNotGenerateAReplacementOrOverwriteData();
        invalidatedKeyLookupIsReportedAsUnavailable();
        unusableCipherKeyIsReportedAsUnavailable();
        interruptedWriteKeepsThePriorCommittedSnapshot();
        syncFailurePreservesPriorSnapshot();
        silentCommitFailureIsReportedAndPriorSnapshotRemains();
        staleSnapshotCannotOverwriteNewerCommit();
        incompleteFirstWriteIsNotMistakenForEmpty();
        legacyFormatBlobRemainsReadableAndIsUpgradedOnSave();
        keyRotationReplacesBlobAndRetiresOldKey();
        interruptedRotationPreservesPriorSnapshotAndRetiredKey();
        rotationRequiresAVerifiedLoadedStore();
        emptyStoreRotationIsANoOp();
        System.out.println("PASS encrypted storage recovery tests: " + assertions + " assertions");
    }

    private static void validEmptyStoreIsDifferentFromUnreadableStore() throws Exception {
        MemoryAtomicFile file = new MemoryAtomicFile();
        TestKeys keys = new TestKeys();
        EncryptedBlobStore store = new EncryptedBlobStore(file, keys);
        check(store.load().isEmpty(), "a store with no file or write artifacts is valid and empty");

        Fixture saved = saveNew("existing task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] original = saved.file.bytes();
        TestKeys missingKeys = new TestKeys();
        missingKeys.unavailable = true;
        EncryptedBlobStore unreadable = new EncryptedBlobStore(saved.file, missingKeys);
        EncryptedBlobStore.StorageException failure = expectFailure(unreadable::load,
                EncryptedBlobStore.Kind.KEY_UNAVAILABLE, "missing key is not an empty store");
        check(failure.kind() != null, "load error has a typed failure kind");
        check(Arrays.equals(original, saved.file.bytes()), "failed load leaves encrypted bytes unchanged");
    }

    private static void truncatedCiphertextIsRejectedWithoutChangingBytes() throws Exception {
        Fixture saved = saveNew("task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] truncated = Arrays.copyOf(saved.goodBytes, saved.goodBytes.length - 20);
        saved.file.replaceBase(truncated);
        byte[] beforeLoad = saved.file.bytes();
        EncryptedBlobStore.StorageException failure = expectFailure(
                () -> new EncryptedBlobStore(saved.file, saved.keys).load(),
                EncryptedBlobStore.Kind.CORRUPT_DATA, "truncated encrypted data is rejected");
        check(failure.getMessage().contains("size"), "truncated data reports a file-integrity failure");
        check(Arrays.equals(beforeLoad, saved.file.bytes()), "truncated encrypted bytes are preserved");
    }

    private static void tamperedCiphertextIsRejectedWithoutChangingBytes() throws Exception {
        Fixture saved = saveNew("task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] tampered = saved.goodBytes.clone();
        tampered[tampered.length - 1] ^= 0x01;
        saved.file.replaceBase(tampered);
        byte[] beforeLoad = saved.file.bytes();
        expectFailure(() -> new EncryptedBlobStore(saved.file, saved.keys).load(),
                EncryptedBlobStore.Kind.AUTHENTICATION_FAILED,
                "tampered GCM ciphertext fails authentication");
        check(Arrays.equals(beforeLoad, saved.file.bytes()), "tampered encrypted bytes are preserved");
    }

    private static void missingKeyDoesNotGenerateAReplacementOrOverwriteData() throws Exception {
        Fixture saved = saveNew("last good snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] original = saved.file.bytes();
        TestKeys missing = new TestKeys();
        missing.unavailable = true;
        EncryptedBlobStore unreadable = new EncryptedBlobStore(saved.file, missing);
        expectFailure(unreadable::load, EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                "missing Keystore key is reported distinctly");
        check(missing.createCalls == 0, "an existing ciphertext never triggers replacement-key creation");
        expectFailure(() -> { unreadable.save("replacement data".getBytes(java.nio.charset.StandardCharsets.UTF_8)); return null; },
                EncryptedBlobStore.Kind.STORE_NOT_VERIFIED,
                "a failed load blocks subsequent writes");
        check(Arrays.equals(original, saved.file.bytes()), "a save after failed load cannot overwrite the file");
    }

    private static void invalidatedKeyLookupIsReportedAsUnavailable() throws Exception {
        Fixture saved = saveNew("last good snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] original = saved.file.bytes();
        TestKeys invalidated = new TestKeys();
        invalidated.invalidated = true;
        expectFailure(() -> new EncryptedBlobStore(saved.file, invalidated).load(),
                EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                "a simulated permanently invalidated key is reported as unavailable");
        check(invalidated.createCalls == 0, "an invalidated key is not silently replaced");
        check(Arrays.equals(original, saved.file.bytes()), "invalidated-key lookup preserves ciphertext");
    }

    private static void unusableCipherKeyIsReportedAsUnavailable() throws Exception {
        Fixture saved = saveNew("last good snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] original = saved.file.bytes();
        TestKeys invalidated = new TestKeys();
        invalidated.legacyKey = new SecretKeySpec(new byte[17], "AES");
        expectFailure(() -> new EncryptedBlobStore(saved.file, invalidated).load(),
                EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                "an unusable AES key rejected at initialization is reported as unavailable");
        check(Arrays.equals(original, saved.file.bytes()), "cipher key-use failure preserves the ciphertext");
    }

    private static void interruptedWriteKeepsThePriorCommittedSnapshot() throws Exception {
        byte[] originalPlaintext = "previous committed task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Fixture saved = saveNew(originalPlaintext);
        byte[] originalBlob = saved.file.bytes();
        saved.file.failNextWriteAfter(13);
        expectFailure(() -> { saved.store.save("unsaved edit".getBytes(java.nio.charset.StandardCharsets.UTF_8)); return null; },
                EncryptedBlobStore.Kind.WRITE_FAILED, "interrupted write reports a failed commit");
        check(Arrays.equals(originalBlob, saved.file.bytes()), "interrupted save preserves the exact prior ciphertext");

        EncryptedBlobStore restarted = new EncryptedBlobStore(saved.file, saved.keys);
        EncryptedBlobStore.LoadResult recovered = restarted.load();
        check(!recovered.isEmpty(), "prior committed snapshot remains available after interruption");
        check(Arrays.equals(originalPlaintext, recovered.plaintext()),
                "reload returns the prior committed plaintext, not the failed edit");
    }

    private static void silentCommitFailureIsReportedAndPriorSnapshotRemains() throws Exception {
        byte[] originalPlaintext = "previous committed task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Fixture saved = saveNew(originalPlaintext);
        byte[] originalBlob = saved.file.bytes();
        saved.file.silentlyFailNextFinishWrite();
        expectFailure(() -> { saved.store.save("unsaved edit".getBytes(java.nio.charset.StandardCharsets.UTF_8)); return null; },
                EncryptedBlobStore.Kind.WRITE_FAILED,
                "a silent atomic-file commit failure is not reported as a successful save");
        check(Arrays.equals(originalBlob, saved.file.bytes()),
                "a silent commit failure preserves the exact prior ciphertext");

        EncryptedBlobStore restarted = new EncryptedBlobStore(saved.file, saved.keys);
        EncryptedBlobStore.LoadResult recovered = restarted.load();
        check(Arrays.equals(originalPlaintext, recovered.plaintext()),
                "reload returns the previous snapshot after a silent commit failure");
    }

    private static void syncFailurePreservesPriorSnapshot() throws Exception {
        byte[] originalPlaintext = "previous committed task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Fixture saved = saveNew(originalPlaintext);
        byte[] originalBlob = saved.file.bytes();
        saved.file.failNextFinishWrite();
        expectFailure(() -> { saved.store.save("unsaved edit".getBytes(java.nio.charset.StandardCharsets.UTF_8)); return null; },
                EncryptedBlobStore.Kind.WRITE_FAILED,
                "a surfaced sync/finish failure is reported as an unsuccessful save");
        check(Arrays.equals(originalBlob, saved.file.bytes()),
                "a surfaced sync/finish failure preserves the exact prior ciphertext");
        EncryptedBlobStore restarted = new EncryptedBlobStore(saved.file, saved.keys);
        check(Arrays.equals(originalPlaintext, restarted.load().plaintext()),
                "reload returns the previous snapshot after a surfaced sync failure");
    }

    private static void staleSnapshotCannotOverwriteNewerCommit() throws Exception {
        byte[] initial = "initial committed snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Fixture saved = saveNew(initial);
        EncryptedBlobStore staleActivityStore = new EncryptedBlobStore(saved.file, saved.keys);
        check(!staleActivityStore.load().isEmpty(), "a second Activity loads the existing snapshot");

        byte[] latest = "newer committed snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        saved.store.save(latest);
        byte[] latestBlob = saved.file.bytes();
        expectFailure(() -> { staleActivityStore.save("stale edit".getBytes(java.nio.charset.StandardCharsets.UTF_8)); return null; },
                EncryptedBlobStore.Kind.STORE_NOT_VERIFIED,
                "a stale Activity cannot overwrite a snapshot committed after its load");
        check(Arrays.equals(latestBlob, saved.file.bytes()),
                "stale-save rejection preserves the newer committed ciphertext");

        EncryptedBlobStore restarted = new EncryptedBlobStore(saved.file, saved.keys);
        check(Arrays.equals(latest, restarted.load().plaintext()),
                "reload returns the newer snapshot after stale-save rejection");
    }

    private static void incompleteFirstWriteIsNotMistakenForEmpty() throws Exception {
        MemoryAtomicFile file = new MemoryAtomicFile();
        file.leaveIncompleteFirstWrite(new byte[] { 'D', 'M', 'T' });
        TestKeys keys = new TestKeys();
        expectFailure(() -> new EncryptedBlobStore(file, keys).load(),
                EncryptedBlobStore.Kind.CORRUPT_DATA,
                "a staged first-write artifact is not treated as a valid empty store");
        check(keys.createCalls == 0, "an incomplete write does not create a new key");
        check(file.hasAnyArtifacts(), "failure does not delete the incomplete write artifact");
    }

    private static void legacyFormatBlobRemainsReadableAndIsUpgradedOnSave() throws Exception {
        MemoryAtomicFile file = new MemoryAtomicFile();
        TestKeys keys = new TestKeys();
        keys.legacyKey = new SecretKeySpec(new byte[32], "AES");
        byte[] plaintext = "legacy task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        file.replaceBase(craftLegacyBlob(keys.legacyKey, plaintext));
        byte[] legacyBlob = file.bytes();

        EncryptedBlobStore store = new EncryptedBlobStore(file, keys);
        EncryptedBlobStore.LoadResult loaded = store.load();
        check(!loaded.isEmpty() && Arrays.equals(plaintext, loaded.plaintext()),
                "a legacy DMT1 snapshot remains readable with the original key");

        byte[] upgradedPlaintext = "upgraded task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        store.save(upgradedPlaintext);
        byte[] saved = file.bytes();
        check(hasVersionedMagic(saved), "save rewrites the snapshot with the versioned DMT2 header");
        check(readBlobKeyVersion(saved) == 1, "an upgraded legacy snapshot keeps its original key version");
        check(!Arrays.equals(legacyBlob, saved), "the legacy-format bytes are replaced on save");

        EncryptedBlobStore reopened = new EncryptedBlobStore(file, keys);
        check(!reopened.load().isEmpty() && Arrays.equals(upgradedPlaintext, reopened.load().plaintext()),
                "an upgraded DMT2 snapshot loads back without the legacy header");
    }

    private static void keyRotationReplacesBlobAndRetiresOldKey() throws Exception {
        byte[] originalPlaintext = "pre-rotation committed snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Fixture saved = saveNew(originalPlaintext);
        check(hasVersionedMagic(saved.goodBytes) && readBlobKeyVersion(saved.goodBytes) == 1,
                "the pre-rotation snapshot names the original key version");

        saved.store.rotateKey(originalPlaintext);
        byte[] rotated = saved.file.bytes();
        check(hasVersionedMagic(rotated), "rotation writes the versioned DMT2 header");
        check(readBlobKeyVersion(rotated) == 2, "the rotated blob names the replacement key version");
        check(!Arrays.equals(rotated, saved.goodBytes), "rotation replaces the committed ciphertext");
        check(saved.keys.retiredVersions.contains(Integer.valueOf(1)),
                "the retired key version is destroyed after the new snapshot commits");
        check(saved.keys.legacyKey == null, "the retired key is no longer loadable after rotation");

        EncryptedBlobStore reopened = new EncryptedBlobStore(saved.file, saved.keys);
        check(!reopened.load().isEmpty() && Arrays.equals(originalPlaintext, reopened.load().plaintext()),
                "the reopened store reads the rotated snapshot with the replacement key");

        byte[] postRotation = "post-rotation edit".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        reopened.save(postRotation);
        EncryptedBlobStore reloaded = new EncryptedBlobStore(saved.file, saved.keys);
        check(Arrays.equals(postRotation, reloaded.load().plaintext()),
                "saving after rotation keeps working under the replacement key");
    }

    private static void interruptedRotationPreservesPriorSnapshotAndRetiredKey() throws Exception {
        byte[] originalPlaintext = "pre-rotation committed snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Fixture saved = saveNew(originalPlaintext);
        byte[] originalBlob = saved.file.bytes();
        saved.file.failNextWriteAfter(13);
        expectFailure(() -> { saved.store.rotateKey(originalPlaintext); return null; },
                EncryptedBlobStore.Kind.WRITE_FAILED, "interrupted rotation reports a failed commit");
        check(Arrays.equals(originalBlob, saved.file.bytes()),
                "interrupted rotation preserves the exact prior ciphertext");
        check(saved.keys.retiredVersions.isEmpty(),
                "no key version is retired by a failed rotation");
        check(saved.keys.legacyKey != null, "the original key survives a failed rotation");

        EncryptedBlobStore restarted = new EncryptedBlobStore(saved.file, saved.keys);
        check(!restarted.load().isEmpty() && Arrays.equals(originalPlaintext, restarted.load().plaintext()),
                "the prior snapshot remains readable after a failed rotation");
    }

    private static void rotationRequiresAVerifiedLoadedStore() throws Exception {
        Fixture saved = saveNew("verified snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        EncryptedBlobStore unloaded = new EncryptedBlobStore(saved.file, saved.keys);
        expectFailure(() -> { unloaded.rotateKey("replacement data".getBytes(java.nio.charset.StandardCharsets.UTF_8)); return null; },
                EncryptedBlobStore.Kind.STORE_NOT_VERIFIED,
                "rotation is blocked before the store is loaded");

        byte[] tampered = saved.file.bytes().clone();
        tampered[tampered.length - 1] ^= 0x01;
        saved.file.replaceBase(tampered);
        byte[] beforeRotation = saved.file.bytes();
        expectFailure(() -> { saved.store.load(); return null; },
                EncryptedBlobStore.Kind.AUTHENTICATION_FAILED,
                "a tampered snapshot fails authentication before any rotation state check");
        expectFailure(() -> { saved.store.rotateKey("replacement data".getBytes(java.nio.charset.StandardCharsets.UTF_8)); return null; },
                EncryptedBlobStore.Kind.STORE_NOT_VERIFIED,
                "rotation is blocked after a failed load");
        check(Arrays.equals(beforeRotation, saved.file.bytes()),
                "a blocked rotation leaves the ciphertext unchanged");
    }

    private static void emptyStoreRotationIsANoOp() throws Exception {
        MemoryAtomicFile file = new MemoryAtomicFile();
        TestKeys keys = new TestKeys();
        EncryptedBlobStore store = new EncryptedBlobStore(file, keys);
        check(store.load().isEmpty(), "new store starts empty");
        store.rotateKey("first task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        check(!file.hasAnyArtifacts(), "empty-store rotation writes no snapshot");
        check(keys.retiredVersions.isEmpty(), "empty-store rotation retires no key version");

        store.save("first task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        EncryptedBlobStore reloaded = new EncryptedBlobStore(file, keys);
        check(!reloaded.load().isEmpty()
                        && Arrays.equals("first task snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8), reloaded.load().plaintext()),
                "saving still works after a no-op empty-store rotation");
    }

    private static byte[] craftLegacyBlob(SecretKey key, byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(new byte[] { 'D', 'M', 'T', '1' });
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(new byte[] { 'D', 'M', 'T', '1' });
        buffer.write(iv);
        buffer.write(ciphertext);
        return buffer.toByteArray();
    }

    private static boolean hasVersionedMagic(byte[] blob) {
        return blob != null && blob.length >= 4
                && blob[0] == 'D' && blob[1] == 'M' && blob[2] == 'T' && blob[3] == '2';
    }

    private static int readBlobKeyVersion(byte[] blob) {
        return ((blob[4] & 0xFF) << 8) | (blob[5] & 0xFF);
    }

    private static Fixture saveNew(byte[] plaintext) throws Exception {
        MemoryAtomicFile file = new MemoryAtomicFile();
        TestKeys keys = new TestKeys();
        EncryptedBlobStore store = new EncryptedBlobStore(file, keys);
        check(store.load().isEmpty(), "new store starts empty");
        store.save(plaintext);
        return new Fixture(file, keys, store, file.bytes());
    }

    private static EncryptedBlobStore.StorageException expectFailure(
            Operation operation, EncryptedBlobStore.Kind expected, String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message + ": expected " + expected);
        } catch (EncryptedBlobStore.StorageException exception) {
            if (exception.kind() != expected) {
                throw new AssertionError(message + ": expected " + expected + " but got " + exception.kind(), exception);
            }
            return exception;
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface Operation {
        Object run() throws Exception;
    }

    private static final class Fixture {
        private final MemoryAtomicFile file;
        private final TestKeys keys;
        private final EncryptedBlobStore store;
        private final byte[] goodBytes;

        Fixture(MemoryAtomicFile file, TestKeys keys, EncryptedBlobStore store, byte[] goodBytes) {
            this.file = file;
            this.keys = keys;
            this.store = store;
            this.goodBytes = goodBytes;
        }
    }

    private static final class TestKeys implements EncryptedBlobStore.KeyAccess {
        private SecretKey legacyKey;
        private SecretKey rotatedKey;
        private boolean unavailable;
        private boolean invalidated;
        private int createCalls;
        private final List<Integer> retiredVersions = new ArrayList<>();

        @Override
        public SecretKey loadExistingKey() throws EncryptedBlobStore.StorageException {
            if (unavailable || invalidated || legacyKey == null) {
                throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                        invalidated ? "Simulated permanently invalidated key." : "Simulated missing key.");
            }
            return legacyKey;
        }

        @Override
        public SecretKey createKeyForNewStore() {
            createCalls++;
            if (legacyKey == null) legacyKey = new SecretKeySpec(new byte[32], "AES");
            return legacyKey;
        }

        @Override
        public SecretKey loadKeyForVersion(int keyVersion) throws EncryptedBlobStore.StorageException {
            if (keyVersion >= 2) {
                if (unavailable || rotatedKey == null) {
                    throw new EncryptedBlobStore.StorageException(EncryptedBlobStore.Kind.KEY_UNAVAILABLE,
                            "Simulated missing replacement key.");
                }
                return rotatedKey;
            }
            return loadExistingKey();
        }

        @Override
        public int newStoreKeyVersion() {
            return 1;
        }

        @Override
        public EncryptedBlobStore.VersionedKey createReplacementKey() {
            if (rotatedKey == null) rotatedKey = new SecretKeySpec(new byte[32], "AES");
            return new EncryptedBlobStore.VersionedKey(rotatedKey, 2);
        }

        @Override
        public void destroyRetiredKey(int keyVersion) {
            retiredVersions.add(keyVersion);
            if (keyVersion == 1) legacyKey = null;
        }
    }

    private static final class MemoryAtomicFile implements EncryptedBlobStore.AtomicFileAccess {
        private byte[] base;
        private byte[] legacyBackup;
        private ByteArrayOutputStream staged;
        private int failAfterBytes = -1;
        private boolean failFinishWrite;
        private boolean silentFinishFailure;

        @Override
        public boolean hasCommittedSnapshot() {
            return base != null || legacyBackup != null;
        }

        @Override
        public boolean hasAnyArtifacts() {
            return hasCommittedSnapshot() || staged != null;
        }

        @Override
        public InputStream openRead() throws IOException {
            if (legacyBackup != null) {
                base = legacyBackup;
                legacyBackup = null;
            }
            if (base == null) throw new FileNotFoundException("No committed snapshot.");
            if (staged != null) staged = null;
            return new ByteArrayInputStream(base);
        }

        @Override
        public OutputStream startWrite() {
            if (base != null) legacyBackup = base.clone();
            staged = new ByteArrayOutputStream();
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    if (failAfterBytes >= 0 && staged.size() >= failAfterBytes) {
                        throw new IOException("Simulated interrupted write.");
                    }
                    staged.write(value);
                }

                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    for (int index = offset; index < offset + length; index++) write(bytes[index]);
                }
            };
        }

        @Override
        public void finishWrite(OutputStream output) throws IOException {
            if (failFinishWrite) {
                failFinishWrite = false;
                throw new IOException("Simulated file sync failure.");
            }
            if (silentFinishFailure) {
                silentFinishFailure = false;
                staged = null;
                failAfterBytes = -1;
                return;
            }
            base = staged.toByteArray();
            staged = null;
            legacyBackup = null;
            failAfterBytes = -1;
        }

        @Override
        public void failWrite(OutputStream output) {
            staged = null;
            if (base == null && legacyBackup != null) base = legacyBackup;
            legacyBackup = null;
            failAfterBytes = -1;
        }

        void replaceBase(byte[] contents) {
            base = contents.clone();
        }

        void failNextWriteAfter(int byteCount) {
            failAfterBytes = byteCount;
        }

        void silentlyFailNextFinishWrite() {
            silentFinishFailure = true;
        }

        void failNextFinishWrite() {
            failFinishWrite = true;
        }

        void leaveIncompleteFirstWrite(byte[] partialBytes) {
            staged = new ByteArrayOutputStream();
            staged.write(partialBytes, 0, partialBytes.length);
        }

        byte[] bytes() {
            return base == null ? null : base.clone();
        }
    }
}

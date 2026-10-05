# Encrypted task storage: failure behavior and recovery boundary

## V1 behavior

The task file is an AES-GCM encrypted snapshot in app-private storage; its non-exportable AES key is held by Android Keystore. The app distinguishes a store with no committed or in-progress file from an unreadable store:

- **No store artifacts:** this is a valid empty task list. A new Keystore key is created only when the first snapshot is saved.
- **Existing or platform-recoverable snapshot:** loading requires the existing Keystore key. The app does not generate a replacement key for an existing ciphertext.
- **Missing/unusable key, truncated or unsupported file, authentication failure, malformed task schema, or read failure:** show “Saved tasks unavailable,” identify the broad failure class, display no ordinary empty-list invitation, and pause edits. The load path does not save or clear the task file or reset the last-known-good in-memory snapshot.
- **Interrupted update:** writes use Android `AtomicFile`; a failed stream write calls `failWrite`, and a completed write calls `finishWrite`. Android documents that a complete file is synced before commit. A stale staged file without any committed/recoverable snapshot is held as an error instead of being treated as empty or overwritten.
- **Failed edit/save:** the attempted values remain visible in the current screen and are labeled unsaved; further edits are paused. The previous committed encrypted snapshot remains the durable state. Leaving the app can discard the visible unsaved attempt; reopening may load the last committed snapshot.

The app does not delete or rotate an unavailable key, rewrite corrupt ciphertext as an empty list, or automatically replace the data with a new empty snapshot after a failure. Android `AtomicFile`'s interrupted-write rollback is the only automatic recovery behavior in scope; it is not a general corruption-repair mechanism.

## Recovery policy intentionally not selected

V1 has no task export, user-held recovery key, cloud sync, retained encrypted backup, or key-rotation recovery. Android Keystore key material is non-exportable, so replacing a missing/unusable key cannot decrypt ciphertext encrypted under the original key. Do not claim that key rotation restores tasks.

Before adding any other recovery behavior, the product owner must select and document a policy, including whether to provide a user-controlled export protected by a user-chosen recovery secret, retain a separate encrypted backup (and its retention/deletion rules), or keep failures non-recoverable. This change deliberately keeps the app at a non-destructive error state instead of selecting a backup-retention or data-loss policy.

## Automated evidence and remaining gates

Run the JDK suite from `android-app/` with `sh tools/run-storage-recovery-tests.sh`. It exercises valid-empty versus failure, truncated ciphertext, modified AES-GCM ciphertext, missing and simulated permanently invalidated keys, an AES key rejected at initialization, a partial update write, a staged first write, and a failed edit that leaves the previous encrypted bytes and plaintext snapshot unchanged. Source checks verify that Activity load failures do not clear task state, loading/failure are not reported as zero tasks, failed saves are visibly unsaved and block further edits, and storage errors do not render as a normal empty list.

These tests run the production encrypted-blob protocol with a deterministic in-memory atomic-file/key adapter. They do **not** execute Android Keystore or Android `AtomicFile` on a device. Device/emulator tests for Keystore loss/invalidation, process death during writes, filesystem/storage-full behavior, startup/relaunch behavior, and TalkBack announcement/reflow remain release gates. No device installation, APK upload, or release is part of this change.

## Official Android references

- [Android `AtomicFile` API](https://developer.android.com/reference/android/util/AtomicFile): documents the complete-write/sync/commit guarantee, `startWrite`/`finishWrite`/`failWrite`, read behavior, and the caller's responsibility for mutual exclusion. This app serializes its storage operations on one executor.
- [AOSP `AtomicFile` implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/util/AtomicFile.java): documents how reads handle legacy backup and staged-file cases; the app's adapter recognizes the base, `.bak`, and `.new` artifacts so absence is not confused with an interrupted write.
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore): key material remains non-exportable; the app therefore cannot recreate an unavailable original key from the ciphertext.
- [`KeyPermanentlyInvalidatedException`](https://developer.android.com/reference/android/security/keystore/KeyPermanentlyInvalidatedException): documents permanent invalidation for keys configured to require user authentication. V1's AES key does not require user authentication, but the app still treats any existing-key lookup or cryptographic key-use failure as unavailable rather than generating a substitute.

# Daymark (AI Personal Task Assistant)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Security Policy](https://img.shields.io/badge/Security-Policy-brightgreen.svg)](SECURITY.md)
[![Storage Recovery](https://img.shields.io/badge/Data%20Safety-Documented%20recovery%20limits-blue.svg)](android-app/STORAGE_RECOVERY.md)
[![Android](https://img.shields.io/badge/Android-Min%20SDK%2026%20%7C%20device%20tests%20pending-orange.svg)](android-app/)

## Release status

There is no current signed production APK. The published [v1.0.0 GitHub Release](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.0) has no attached binary asset, and its release notes contain a `raw/main` link to a debug APK path that is no longer present in the repository. The historical debug APKs that were once tracked in the repository tree have been removed, so this repository no longer serves a downloadable APK. Sizes and SHA-256 digests of the removed files are kept as a historical record in the [documentation truth audit](DOCUMENTATION_TRUTH_AUDIT.md). Release binaries are built, verified, and attached by the [Android device-test release workflow](.github/workflows/android-release-assets.yml) when a `v*-device-test.*` tag is pushed; the existing `v1.0.0-device-test.1` prerelease has **no attached asset**. No download link is published here until an artifact is built, verified, and attached through that release process.

The Android project declares `minSdk = 26`, `compileSdk = 35`, and `targetSdk = 35`. This is configuration evidence, not proof of runtime behavior on Android 8 or newer devices. The app has not been qualified for production or Google Play submission; ordinary new Play submissions and updates must target API 36 or higher from August 31, 2026 ([official requirement](https://developer.android.com/google/play/requirements/target-sdk)).

At the verified main baseline `66476cb49a6caa9a43b65db39912ee397b6e6857`, `npm test` passed 8 tests; the Android source/host checks and storage, attachment, and portable-backup suites also passed. GitHub Actions passed its Android debug-variant build on that commit. These checks do not establish device behavior, Play readiness, or a production release. See the [documentation truth audit](DOCUMENTATION_TRUTH_AUDIT.md) for exact commands, counts, limitations, and artifact inventory.

Updater code is present but disabled by a closed publisher gate. The common manifest declares `INTERNET`; the `githubSideload` flavor additionally declares `ACCESS_NETWORK_STATE`, while Play does not. If enabled after release signing is configured, the flow verifies an artifact and saves it to a user-selected document; it does not launch Android's installer. Real APK parsing, provider behavior, Android approval screens, and installation remain untested on a device.

Source-level checks use local test fixtures and do not perform live updater traffic or test Android's APK signer parser, document picker, or system installation UI. No device/emulator was available for install, launch, airplane-mode, TalkBack, locale, or visual text-size checks. Do not assume an APK update will preserve data unless package and signing identity match; export a portable backup before a device or release transition.

---



**Daymark** is a local-first task app with encrypted app-private task storage, user-selected attachments, portable encrypted backups, and source-level release-integrity checks. Browser access is a separate, user-initiated online feature.

---

## Key Features & Architecture

### 1. Privacy-First Encrypted Storage
- Task snapshots use AES-GCM with a key held by Android Keystore. Hardware-backed protection depends on device support and is not guaranteed on every device.
- Task and attachment payloads are encrypted in app-private storage. This describes stored files, not data in memory or an absolute security guarantee.
- Writes use Android `AtomicFile`, explicit failure handling, and read-back checks. These measures reduce interruption risk; they do not guarantee zero data loss or durability across every device or sudden power loss.

### 2. Data recovery and updates
- Task storage fails closed on unreadable or unavailable data instead of treating it as an ordinary empty list. This is not a promise that all failures are recoverable.
- Portable backups use a generated recovery key, AES-GCM, and HKDF-SHA-256. Passphrase-based backup is not implemented; device/provider restore behavior remains untested.
- Android normally preserves app data only when an update is accepted for the same package and signing identity. Daymark has no production signing setup, so update continuity has not been demonstrated.

### 3. Encrypted Task Attachments & Portable Backups
- Attachments use encrypted app-private payloads and explicit Android file selection; device-provider behavior remains untested.
- Portable `.dmbackup` export/import is present in `main`; it is explicitly user-triggered and protected by a generated 32-byte recovery key, not a passphrase.

### 4. Release-Gated In-App Updater
- Source contains a release-gated GitHub updater, but the publisher gate is currently disabled and no production signer is configured.
- The implemented handoff saves a verified APK to a user-selected document for manual opening; Daymark does not start Android's installer.

---

## Repository Structure

```text
ai-personal-task-assistant/
├── .github/                     # Issue templates & community standards
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   └── pull_request_template.md
├── android-app/                 # Native Android app (minSdk 26; runtime qualification pending)
│   ├── app/src/main/java/com/cue/daymark/
│   │   ├── EncryptedTaskStore.java       # Atomic AES-256-GCM storage
│   │   ├── EncryptedBlobStore.java       # Raw payload encryption
│   │   ├── AttachmentBlobStore.java      # Task attachment storage
│   │   ├── PortableBackupManager.java    # Generated-recovery-key encrypted backups
│   │   ├── updater/                      # Release client, SHA-256 verifier, SAF saver
│   │   └── MainActivity.java             # Main user interface & lifecycle
│   ├── tools/                           # Offline smoke test suites & verification scripts
│   ├── ANDROID_DESIGN.md                # System design & threat model
│   └── STORAGE_RECOVERY.md              # Recovery & migration guarantees
├── app.js                       # Web prototype companion
├── CONTRIBUTING.md              # Development & contribution guidelines
├── CODE_OF_CONDUCT.md           # Community code of conduct
├── SECURITY.md                  # Vulnerability reporting & security policy
└── README.md                    # Project documentation
```

---

## Verification & Testing

Available host and source-level checks exercise selected logic and file-format policies; they are not a guarantee of stability or device acceptance:

```bash
# Web prototype tests
npm test

# Android core verification
cd android-app
sh tools/check-v1-source.sh
sh tools/run-core-tests.sh
sh tools/run-storage-recovery-tests.sh
sh tools/run-attachment-tests.sh
sh tools/run-portable-backup-tests.sh
```

---

## Community & Support

- Found an issue or want to request a feature? Open an [Issue](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues).
- Want to contribute? Check our [Contributing Guide](CONTRIBUTING.md).
- Security concerns? Please read our [Security Policy](SECURITY.md).

---

## Building, Downloading, and Verifying Signed Release APKs

### 1. Creating a Release Tag
To trigger the automated GitHub Actions release workflow:
```bash
# Create an annotated release tag (e.g. v1.0.2)
git tag -a v1.0.2 -m "Release v1.0.2"

# Push the tag to GitHub
git push origin v1.0.2
```
The `.github/workflows/android-release-assets.yml` workflow automatically decodes the configured `KEYSTORE_BASE64` secret at runtime, builds `assembleGithubSideloadRelease`, verifies the signature, and attaches the signed release APK directly to the GitHub Release.

### 2. Downloading the Signed APK
1. Go to the [Releases](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases) section of the repository.
2. Select the published release tag.
3. Download the attached APK asset (e.g. `Daymark-githubSideload-release-v1.0.2.apk`).

### 3. Verifying the APK Signature
Using Android SDK's `apksigner`, verify that the APK is properly signed and check its certificate digest:
```bash
apksigner verify --verbose --print-certs Daymark-githubSideload-release-v1.0.2.apk
```
Expected verification output includes:
- `Verifies`
- `Verified using v1 scheme (JAR signing): true`
- `Verified using v2 scheme (APK Signature Scheme v2): true`
- Certificate SHA-256 digest matching your release keystore certificate.

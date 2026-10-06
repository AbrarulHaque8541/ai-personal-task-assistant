# Daymark (AI Personal Task Assistant)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Security Policy](https://img.shields.io/badge/Security-Policy-brightgreen.svg)](SECURITY.md)
[![Zero Data Loss](https://img.shields.io/badge/Data%20Safety-Zero%20Loss%20Guaranteed-success.svg)](android-app/STORAGE_RECOVERY.md)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026--35%29-orange.svg)](android-app/)

## 📥 Download Daymark

> **Note:** The current build is a **Debug / device-untested** build. It is not a signed production release. Clicking the link below downloads the APK file directly from this repository (a raw file link — your browser will download it, nothing opens on GitHub).

**Debug APK (Android 8.0+, API 26–35):** [Download Daymark debug APK (v1.0.0)](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/raw/main/artifacts/Daymark-debug-device-untested-api35.apk)

This review improved normal-light theme text contrast and added source-derived contrast regression checks. `sh android-app/tools/check-v1-source.sh` passes 25 JDK-only task-logic assertions, 70 updater-policy assertions, and 80 parser/transport/download assertions, plus manifest/privacy, accessibility, and contrast checks. The production downloader/core accepts a streamed APK of exactly 104,857,600 bytes and rejects an actual body of 104,857,601 bytes without allocating the body in memory. These host/source checks do not replace device UI or TalkBack testing.

The GitHub sideload updater is implemented in source but fail-closed: only that flavor declares `INTERNET` and `ACCESS_NETWORK_STATE`; the shared and Play manifests declare none. It checks GitHub in the foreground, requires a per-download choice (Wi-Fi only by default or explicitly allow mobile data), downloads only after confirmation, and verifies size, hash, package, version, minimum Android version, and signer before offering user-directed file saving. Before transfer, it stores validated release expectations in app-private `noBackupFilesDir`; the APK is staged and retained there, avoiding Android's evictable cache and backup. A digest-addressed `.verified.apk` is revalidated against the stored digest, package/version/minimum SDK, installed signer, and pinned publisher signer before it is offered after restart. Startup cleanup removes only abandoned `.partial` staging files. One verified APK can retain up to 100 MiB until the user explicitly discards it; there is no automatic expiry, a new updater download cannot replace a retained update, and saving a user-selected copy does not silently remove the retained copy. Daymark has no `REQUEST_INSTALL_PACKAGES`, `PackageInstaller` session, install-source Settings shortcut, or app-launched installer; the user must open the saved APK from Files and accept any Android-controlled approval. The production signer/release gate remains closed, with an empty signer pin and no production key or stable release. Offline API 35 Java/resource compilation succeeds with Gradle 8.10.2; no APK was assembled or retained in this updater task. Device-side signer parsing, file-picker/open behavior, Android confirmation UI, and install tests remain unverified. Debug signing is not suitable for production updates, and no release artifact exists to measure against the `<15 MB` target.

The shared and Play manifests declare no permissions; the GitHub sideload flavor declares only `INTERNET` and `ACCESS_NETWORK_STATE`. Source tests use fake clients, transport, and signer metadata: they caused no live request, APK download, installation, or task/history upload. They do not exercise Android’s real APK signer parser, file picker, source-approval Settings, or system install confirmation. ADB found no connected device; installation, launch, offline runtime, TalkBack, locale, and real-device text-size/contrast checks remain unverified. This task uses only already-installed SDK components and produces no APK. GitHub has no stable release APK or production signing setup. See [`android-app/README.md`](android-app/README.md), [`android-app/V1_ACCEPTANCE.md`](android-app/V1_ACCEPTANCE.md), and [`android-app/ANDROID_SOURCES.md`](android-app/ANDROID_SOURCES.md) for prerequisites and release gates.

- Size: 51,831 bytes · SHA-256: `e80428836bcac97ea6655cf9d865a7eb7b9d7bbcd7c4f6859c308d056b533724` (verified)
- Release page: [Releases → v1.0.0](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.0) — its notes carry the same direct download link; the release has **no attached binary asset** yet.
- Updating from an older Daymark APK is safe — your existing tasks, encrypted storage, and Keystore keys are preserved (same package `com.cue.daymark`, same signing key).

---



**Daymark** is a privacy-first, offline-ready personal task assistant with end-to-end encrypted local storage, portable encrypted backups, task attachments, and release-integrity verification.

---

## Key Features & Architecture

### 1. Privacy-First Encrypted Storage
- **Keystore Hardware-Backed Encryption:** Uses Android Keystore with AES-256-GCM authenticated encryption for all tasks and metadata (`EncryptedTaskStore`).
- **Zero Plaintext at Rest:** User tasks, notes, and attachments are encrypted before hitting flash storage.
- **AtomicFile Recovery Semantics:** Write operations use fail-safe staged writes with fallback `.bak` restoration. A crash during a write never corrupts previous valid state.

### 2. Zero-Data-Loss In-Place Updates
- When updating from an existing APK to a new release:
  - App-private data directory (`/data/user/0/com.cue.daymark/`) and Keystore keys are preserved across installations with matching signature and package name.
  - Automatic migration reconciles legacy records safely without deleting user notes.
  - Portable AES encrypted backups allow exporting data safely before major device transitions.

### 3. Encrypted Task Attachments & Portable Backups
- Encrypted file attachments stored locally via `AttachmentBlobStore`.
- Portable encrypted backup files (`.dmbackup`) protected by a one-time generated 32-byte recovery key (HKDF-SHA-256 per-record keys + AES-256-GCM).

### 4. Release-Gated In-App Updater
- Checks GitHub Releases securely via HTTPS.
- Displays download sizes, version changes, and prompts the user (*Update Now* vs *Update Later*).
- Allows choosing between Wi-Fi and Mobile Data.
- Validates cryptographic SHA-256 integrity of the downloaded APK before triggering system package installer.

---

## Repository Structure

```text
ai-personal-task-assistant/
├── .github/                     # CI workflows, issue templates & community standards
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   ├── workflows/
│   └── pull_request_template.md
├── android-app/                 # Native Android application (Java, API 26-35)
│   ├── app/src/main/java/com/cue/daymark/
│   │   ├── EncryptedTaskStore.java       # Atomic AES-256-GCM storage
│   │   ├── EncryptedBlobStore.java       # Raw payload encryption
│   │   ├── AttachmentBlobStore.java      # Task attachment storage
│   │   ├── PortableBackupManager.java    # Recovery-key encrypted backups
│   │   ├── updater/                      # Release client, SHA-256 verifier, SAF saver
│   │   └── MainActivity.java             # Main user interface & lifecycle
│   ├── tools/                           # Offline smoke test suites & verification scripts
│   ├── ANDROID_DESIGN.md                # System design & threat model
│   └── STORAGE_RECOVERY.md              # Recovery & migration guarantees
├── index.html                   # Web prototype companion (repo root)
├── app.js                       # Web prototype UI logic
├── task-logic.js                # Shared task/filter/suggestion logic
├── styles.css                   # Web prototype styling
├── tests/                       # Node.js tests for the web prototype (npm test)
├── wrangler.jsonc               # Cloudflare Workers static-asset deployment config
├── CONTRIBUTING.md              # Development & contribution guidelines
├── CODE_OF_CONDUCT.md           # Community code of conduct
├── SECURITY.md                  # Vulnerability reporting & security policy
├── LICENSE                      # MIT license
└── README.md                    # Project documentation
```

---

## Verification & Testing

Daymark maintains a strict offline test runner to guarantee stability:

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

# Daymark (AI Personal Task Assistant)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Security Policy](https://img.shields.io/badge/Security-Policy-brightgreen.svg)](SECURITY.md)
[![Zero Data Loss](https://img.shields.io/badge/Data%20Safety-Zero%20Loss%20Guaranteed-success.svg)](android-app/STORAGE_RECOVERY.md)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026--35%29-orange.svg)](android-app/)

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
- Portable encrypted backup files (`.daymark-backup`) protected by user passphrase (PBKDF2 + AES-GCM).

### 4. Release-Gated In-App Updater
- Checks GitHub Releases securely via HTTPS.
- Displays download sizes, version changes, and prompts the user (*Update Now* vs *Update Later*).
- Allows choosing between Wi-Fi and Mobile Data.
- Validates cryptographic SHA-256 integrity of the downloaded APK before triggering system package installer.

---

## Repository Structure

```text
ai-personal-task-assistant/
├── .github/                     # Issue templates & community standards
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   └── pull_request_template.md
├── android-app/                 # Native Android application (Java, API 26-35)
│   ├── app/src/main/java/com/cue/daymark/
│   │   ├── EncryptedTaskStore.java       # Atomic AES-256-GCM storage
│   │   ├── EncryptedBlobStore.java       # Raw payload encryption
│   │   ├── AttachmentBlobStore.java      # Task attachment storage
│   │   ├── PortableBackupManager.java    # User-passphrase encrypted backups
│   │   ├── updater/                      # Release client, SHA-256 verifier, SAF saver
│   │   └── MainActivity.java             # Main user interface & lifecycle
│   ├── tools/                           # Offline smoke test suites & verification scripts
│   ├── ANDROID_DESIGN.md                # System design & threat model
│   └── STORAGE_RECOVERY.md              # Recovery & migration guarantees
├── src/                         # Web prototype companion
├── CONTRIBUTING.md              # Development & contribution guidelines
├── CODE_OF_CONDUCT.md           # Community code of conduct
├── SECURITY.md                  # Vulnerability reporting & security policy
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

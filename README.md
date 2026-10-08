# Daymark

**Local-first Android task assistant** — encrypted on-device storage, portable backups, HTTPS-only embedded browser, and a release-gated GitHub sideload updater.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-minSdk%2026%20%7C%20target%2035-green.svg)](android-app/)
[![CI](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/workflows/android.yml/badge.svg)](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/workflows/android.yml)

## Download (production)

| Item | Link |
|------|------|
| **Latest release** | [v1.0.1](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.1) |
| **APK** | [Daymark-v1.0.1.apk](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.1/Daymark-v1.0.1.apk) |
| **All releases** | https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases |

Install only APKs from this GitHub account’s Releases. Prefer verifying the APK with `apksigner` before install.

```bash
apksigner verify --verbose --print-certs Daymark-v1.0.1.apk
```

> Device matrix (API 26–35), TalkBack, and airplane-mode qualification are still **pending**. Treat this as a production-*signed* build, not a fully device-qualified release.

## What it does

- **Tasks** — offline capture, edit, complete, delete + 7s undo, filters, priorities, due dates, deterministic suggestions (rules, not cloud AI)
- **Encryption** — AES-GCM task snapshots + attachments in app-private storage; Android Keystore key
- **Portable backup** — user-triggered `.dmbackup` export/import with a one-time recovery key
- **Browser** — in-app HTTPS WebView (DuckDuckGo default; Google/Bing/Brave; site shortcuts). Offline by default; Online only after disclosure + explicit tap
- **Updater** (`githubSideload` flavor) — checks GitHub `releases/latest`, verifies size/hash/package/signer, saves APK via SAF for **manual** install (does not drive PackageInstaller)

The **Play** flavor keeps the updater disabled (`UPDATER_ENABLED=false` in that flavor).

## Repository layout

```text
.
├── android-app/          # Native Android app (primary product)
├── .github/workflows/    # Android CI, release assets, SLSA
├── index.html, app.js, styles.css, task-logic.js   # Web prototype (reference only)
├── tests/                # Web unit tests
├── scripts/              # Deploy/package policy checks
└── docs/                 # Optional extra documentation
```

## Build & verify (host)

```bash
# Web prototype
npm test

# Android source/host checks
cd android-app
sh tools/check-v1-source.sh
sh tools/run-storage-recovery-tests.sh
sh tools/run-attachment-tests.sh
sh tools/run-portable-backup-tests.sh

# Debug assemble (SDK required)
./gradlew :app:assembleGithubSideloadDebug
```

Signed release APK/AAB are produced on **push to `main`** by [Android CI](.github/workflows/android.yml) using repository secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Device-test GitHub Releases are published from tags matching `v*-device-test.*` via [android-release-assets.yml](.github/workflows/android-release-assets.yml).

## Report a bug

1. **In the app:** More → **Report a bug** (opens GitHub with a prefilled template; **no task contents** are included).
2. **On the web:** [New bug report](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/new?template=bug_report.md)

## Privacy posture

- Core tasks work offline; no account, no sync service.
- Shared manifest: `INTERNET` (browser). Sideload flavor also: `ACCESS_NETWORK_STATE` (updater Wi‑Fi preference).
- No `REQUEST_INSTALL_PACKAGES`. Updater never uploads tasks.

## License & community

- [LICENSE](LICENSE) · [SECURITY.md](SECURITY.md) · [CONTRIBUTING.md](CONTRIBUTING.md) · [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)

Detailed design: [android-app/README.md](android-app/README.md), [android-app/ANDROID_DESIGN.md](android-app/ANDROID_DESIGN.md), [android-app/STORAGE_RECOVERY.md](android-app/STORAGE_RECOVERY.md).

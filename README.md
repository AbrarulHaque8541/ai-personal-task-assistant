# Daymark

**Daymark** is a local-first Android task assistant with an HTTPS-only embedded browser, encrypted local task storage, attachments, portable encrypted backups, and a lightweight page-local extension system.

- **Android application ID:** `com.abrar.daymark`
- **Architecture:** native Java Activity + Android System WebView; no third-party runtime dependencies
- **Task data:** encrypted on-device; no account, cloud task sync, analytics, or telemetry
- **Source version:** see `android-app/app/build.gradle.kts` (`versionName` / `versionCode`)

## Download (one file only)

**Install this:** the `.apk` from the [latest GitHub Release](https://github.com/AbrarulHaque8541/daymark/releases/latest).

| Do | Don't |
|----|-------|
| Download **only** the asset named `Daymark-v…-githubSideload.**apk**` | Download the `.aab` (Play bundle — **not** installable by tapping) |
| On Play Protect “unrecognized app”, choose **Download anyway** / **Install anyway** | Assume the warning means malware — every non-Play APK shows it |
| Uninstall any broken older Daymark build first if open crashes | Keep installing over a half-downloaded file |

**Why it looked like two downloads:** older releases attached both `.apk` and `.aab`. Saving both is easy to misread as a double APK. New releases attach **APK only** for install.

**Why size looks small (~200–300 KB):** Daymark has no heavy native libraries or image packs — pure Java UI + system WebView. That is expected, not an empty package.

**Why open crashed on some phones (v1.1 and earlier):** referencing Android 13 back / window APIs directly prevented the launcher activity from loading on Android 8–12. Current source isolates those calls (`Api33Back`, `Api30Window`). Use a release built **after** that fix (v1.2+).

Before installing, confirm the release notes package ID `com.abrar.daymark`, version, and signer SHA-256.

## What it does

- Local tasks: NL date/time, priority, `#tags`, notes, subtasks, attachments, templates
- Reminders (best-effort OS notifications) — see [docs/TASK_REMINDERS.md](docs/TASK_REMINDERS.md)
- Encrypted portable backup / restore
- HTTPS-only browser, search engines, AI shortcuts, tabs, Reader Mode, page-local extensions
- Optional signed GitHub update check (sideload flavor)

## Agents & contributors

Read **[AGENTS.md](AGENTS.md)** and **[RELEASE_SIGNING.md](RELEASE_SIGNING.md)** first.

- Production signing secrets are already in GitHub Actions — **do not ask the owner for keystore files**.
- Publish only through the production workflow with tag `v` + source `versionName` on `main`.

## Build and test

```sh
sh android-app/tools/check-v1-source.sh
npm test
```

See [`android-app/README.md`](android-app/README.md) for SDK setup.

## License

See repository `LICENSE` if present.

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-15T20:05:00Z

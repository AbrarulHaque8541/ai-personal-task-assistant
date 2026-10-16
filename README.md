# Daymark

**Daymark** is a local-first Android task assistant with an HTTPS-only embedded browser, encrypted local task storage, attachments, portable encrypted backups, and a lightweight page-local extension system.

- **Android application ID:** `com.abrar.daymark` (owner-approved fresh identity; previous app installations remain separate)
- **Architecture:** native Java Activity + Android System WebView; no third-party runtime dependencies
- **Task data:** encrypted on-device; no account, cloud task sync, analytics, or telemetry
- **Source version:** see `android-app/app/build.gradle.kts` (`versionName` / `versionCode`)

## Download

- **Production Download (latest APK):** [Daymark Latest Release](https://github.com/AbrarulHaque8541/daymark/releases/latest)
- **All Releases:** [GitHub Releases](https://github.com/AbrarulHaque8541/daymark/releases)

### Sideload Installation Guide

1. Open the [Latest Release](https://github.com/AbrarulHaque8541/daymark/releases/latest) on GitHub.
2. Under **Assets**, download the single file whose name ends in `.apk` (e.g. `Daymark-v...-githubSideload.apk`).
    - **Important:** Do not install a `.aab` file directly; it is an Android App Bundle for Play Console, not a phone-installable APK. Releases from v1.2 onward attach only the single `.apk` asset. Older releases (v1.0/v1.1) also attached a `.aab` build artifact — on those release pages select only the `.apk`.
3. Open the downloaded `.apk` file on your device and follow the prompts to install.
4. **App identity & safety notes:**
   - **Application ID:** `com.abrar.daymark`. This is a fresh app identity, separate from previous app installations. Android keeps it separate and legacy local data is not automatically migrated (use encrypted backup/restore).
   - **Target SDK:** Locked to **API 35 (Android 15)** with `minSdk = 26` (Android 8.0+) for broad OEM and runtime stability. The app is compiled against `compileSdk = 36`; moving the *target* SDK to API 36 is deliberately not enabled yet and remains tracked in `API36_MIGRATION_READINESS.md`.
    - **Play Protect warning:** A sideloaded app may be marked "unrecognized"; that alone is not a malware verdict. If Play Protect explicitly says the app is harmful, do not bypass the warning or install it. Verify the APK came from this official release and compare its SHA-256, package ID, version, and signer with the release metadata. If the harmful warning persists, stop and report it rather than choosing "Install anyway".
   - **Signing key:** The production signing certificate remains pinned to `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`.

**Repository URL:** The repository is located at [AbrarulHaque8541/daymark](https://github.com/AbrarulHaque8541/daymark). Legacy links pointing to `AbrarulHaque8541/ai-personal-task-assistant` automatically redirect here.

## What it does

> This inventory describes current source capabilities. The latest published production releases (v1.0 and v1.1) use the new `com.abrar.daymark` identity. Check each release's notes/assets rather than assuming every unreleased source feature is in the downloadable APK.

- Local task capture with natural-language date/time, priority, and `#tag` quick add; edit, completion, delete/undo, notes, due date/time, tags, subtasks, attachments, and templates
- Task reminders: in-app dialog plus best-effort OS notifications, daily/weekly/monthly repeat rules, custom notification sound — see [docs/TASK_REMINDERS.md](docs/TASK_REMINDERS.md) for the honest limits
- Encrypted portable backup / restore
- HTTPS-only in-app browser: starts Offline; first remote navigation requires an explicit disclosure and opt-in. Search engines, AI site shortcuts, tabs, HTTPS-origin bookmarks, find-in-page, downloads, Reader Mode (local text extract), page-local extensions
- Optional in-app update check against signed GitHub Releases (sideload flavor)

## Agents & contributors

Read **[AGENTS.md](AGENTS.md)** and **[RELEASE_SIGNING.md](RELEASE_SIGNING.md)** first. Then read the [agent status index](docs/AGENT_STATUS.md) and verify its linked snapshot against live GitHub.

- Production signing secrets are already in GitHub Actions — **do not ask the owner for keystore files**.
- Publish only through the production workflow with a tag exactly matching `v` + the source `versionName` on `main`, after all release gates pass.
- Verify live Releases, Issues, PRs, and Actions instead of trusting old “candidate” sentences in dated audits.

## Build and test

From the repository root:

```sh
sh android-app/tools/check-v1-source.sh
npm test
```

For Android SDK setup, build commands, measured artifact metadata, and device-test caveats, see [`android-app/README.md`](android-app/README.md).

## License

See repository `LICENSE` if present.

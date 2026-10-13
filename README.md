# Daymark

**Daymark** is a local-first Android task assistant with an HTTPS-only embedded browser, encrypted local task storage, attachments, portable encrypted backups, and a lightweight page-local extension system.

- **Android application ID:** `com.abrar.daymark` (owner-approved fresh identity; previous app installations remain separate)
- **Architecture:** native Java Activity + Android System WebView; no third-party runtime dependencies
- **Task data:** encrypted on-device; no account, cloud task sync, analytics, or telemetry
- **Source version:** see `android-app/app/build.gradle.kts` (`versionName` / `versionCode`)

## Download

**Production downloads:** [GitHub Releases](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases)

Before installing, confirm the release is actually published and that its APK package ID, version, and signer match the release notes. Do not assume a version mentioned in a PR or source tree has been published. The `com.abrar.daymark` identity is a fresh app identity, not an in-place update of the previous app; Android keeps it separate and old local data is not automatically migrated. The production signing key remains pinned and required.

## What it does

- Local task capture, edit, completion, delete/undo, notes, due date/time, priority, subtasks, attachments, templates
- Task reminders: in-app dialog plus best-effort OS notifications, daily/weekly/monthly repeat rules, custom notification sound — see [docs/TASK_REMINDERS.md](docs/TASK_REMINDERS.md) for the honest limits
- Encrypted portable backup / restore
- HTTPS-only in-app browser: starts Offline; first remote navigation requires an explicit disclosure and opt-in. Search engines, AI site shortcuts, tabs, find-in-page, downloads, Reader Mode (local text extract), page-local extensions
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

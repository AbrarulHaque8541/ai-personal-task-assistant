# Daymark

**Daymark** is a local-first Android task assistant with an HTTPS-only embedded browser, encrypted local task storage, attachments, portable encrypted backups, and a lightweight page-local extension system.

- **Android package:** `com.cue.daymark`
- **Architecture:** native Java Activity + Android System WebView; no third-party runtime dependencies
- **Task data:** encrypted on-device; no account, cloud task sync, analytics, or telemetry
- **Source version:** see `android-app/app/build.gradle.kts` (`versionName` / `versionCode`)

## Download

**Latest production build:** [GitHub Releases — latest](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/latest)

Install the `Daymark-v*-githubSideload.apk` asset. For an in-place update, use the same production signing key as previous sideload builds (package `com.cue.daymark`). Prefer updating over uninstalling so local encrypted data stays intact.

Previous releases remain available on the [full releases page](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases).

## What it does

- Local task capture, edit, completion, delete/undo, notes, due date/time, priority, subtasks, attachments, templates
- Task reminders: in-app dialog plus best-effort OS notifications, daily/weekly/monthly repeat rules, custom notification sound — see [docs/TASK_REMINDERS.md](docs/TASK_REMINDERS.md) for the honest limits
- Encrypted portable backup / restore
- HTTPS-only in-app browser: starts Offline; first remote navigation requires an explicit disclosure and opt-in. Search engines, AI site shortcuts, tabs, HTTPS-origin bookmarks, find-in-page, downloads, Reader Mode (local text extract), page-local extensions
- Optional in-app update check against signed GitHub Releases (sideload flavor)

## Agents & contributors

Read **[AGENTS.md](AGENTS.md)** and **[RELEASE_SIGNING.md](RELEASE_SIGNING.md)** first.

- Production signing secrets are already in GitHub Actions — **do not ask the owner for keystore files**.
- Publish only by tagging `vX.Y.Z` on `main` so `.github/workflows/android-production-release.yml` runs.
- Verify live Releases, Issues, and PRs instead of trusting outdated “candidate” sentences in old audit notes.
- For the verified source/release distinction, active blockers, and approved-but-unmerged identity target as of 2026-10-13, see the [repository status snapshot](docs/CURRENT_STATUS_2026-10-13.md).

## Build and test

From the repository root:

```bash
sh android-app/tools/check-v1-source.sh
npm test
```

For Android SDK setup, both debug-flavor build commands, measured artifact metadata, and the device-test caveats, see [`android-app/README.md`](android-app/README.md).

## License

See repository `LICENSE` if present.

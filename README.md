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
- Encrypted portable backup / restore
- HTTPS-only in-app browser: search engines, AI site shortcuts, tabs, find-in-page, downloads, Reader Mode (local text extract), page-local extensions
- Optional in-app update check against signed GitHub Releases (sideload flavor)

## Agents & contributors

Read **[AGENTS.md](AGENTS.md)** and **[RELEASE_SIGNING.md](RELEASE_SIGNING.md)** first.

- Production signing secrets are already in GitHub Actions — **do not ask the owner for keystore files**.
- Publish only by tagging `vX.Y.Z` on `main` so `.github/workflows/android-production-release.yml` runs.
- Verify live Releases, Issues, and PRs instead of trusting outdated “candidate” sentences in old audit notes.

## Build and test

From `android-app/`:

```bash
sh tools/check-v1-source.sh
./gradlew --no-daemon assembleGithubSideloadDebug lintGithubSideloadDebug testGithubSideloadDebugUnitTest
```

Root web prototype: `npm test`.

## License

See repository `LICENSE` if present.

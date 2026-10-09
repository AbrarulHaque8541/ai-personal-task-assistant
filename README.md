# Daymark

**Daymark** is a local-first Android task assistant with an HTTPS-only embedded browser, encrypted local task storage, attachments, portable encrypted backups, and a lightweight page-local extension system.

- **Android package:** `com.cue.daymark`
- **Architecture:** native Java Activity + Android System WebView; no third-party runtime dependencies
- **Task data:** encrypted on-device; no account, cloud task sync, analytics, or telemetry
- **Current source candidate:** v1.0.5 / versionCode 6
- **Latest published GitHub release:** v1.0.4

## Download the published app

| Release | APK | AAB |
|---|---|---|
| **Latest published** [v1.0.4](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.4) | [Daymark-v1.0.4-githubSideload.apk](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.4/Daymark-v1.0.4-githubSideload.apk) | [Daymark-v1.0.4-githubSideload.aab](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.4/Daymark-v1.0.4-githubSideload.aab) |
| Previous [v1.0.3](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.3) | [APK](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.3/Daymark-v1.0.3-githubSideload.apk) | — |

**v1.0.5 is not a published release yet.** A successful main-branch CI build is not a substitute for the exact-tag production workflow or real-phone testing. Do not treat an Actions artifact as a public production release.

## What is implemented

- Local task capture, edit, completion, deletion/undo, filters, search, priorities, due dates/times, notes, subtasks, templates, attachments, and deterministic suggestions.
- AES-GCM encrypted app-private task/attachment storage using Android Keystore.
- User-triggered portable encrypted `.dmbackup` export and add-only import with a separately held recovery key.
- HTTPS-only Android System WebView, explicit user-triggered navigation, browser history, tab management, search/site shortcuts, a local text-only Reader Mode with adjustable font size, sans/serif fonts, light/sepia/dark themes, and copy, downloads handoff, browser data clearing, and an opt-in network-image blocking switch for data savings.
- Compatible page-local CSS/JavaScript extension packs and userscript import; this is **not** a full Chrome/Firefox extension runtime.
- Signed sideload update-check/download verification flow, gated by release configuration and manual user-controlled saving/opening.

These are source-level capability descriptions. See [Android app documentation](android-app/README.md) and [V1 acceptance gates](android-app/V1_ACCEPTANCE.md) for the evidence and limitations of each feature.

## Important boundaries

- Task operations work offline. Web mode sends the URL/query and ordinary connection metadata to the selected destination after you tap **Go** or a site shortcut; pages may contact third parties.
- Imported scripts can read or modify matching website page content. Import only code you trust and review the confirmation before adding it.
- Browser extensions cannot use privileged Chrome/Firefox APIs, full network interception, or Tampermonkey `GM_*` storage.
- No cloud LLM API, local LLM inference, Telegram bot, voice capture, autonomous background agent, root access, or Termux shell bridge is currently implemented.
- **Updater metadata limitation at this audit:** the published v1.0.4 release has an empty notes body, so it does not satisfy Daymark's in-app updater metadata parser. PR #181 fixed the production workflow for future releases; until v1.0.5 is published, use the published release page above rather than assuming the in-app updater can validate v1.0.4.
- Host tests and CI do not prove real-device behavior. Android Keystore, SAF/document providers, TalkBack, process death, physical installation/update, and performance still require device checks.
- The current Gradle target is API 35. Google Play submissions from 31 August 2026 require API 36 or higher; Daymark has not yet completed an API 36 migration/edge-to-edge qualification. Personal sideload use is a separate distribution path.

## Before the next release

1. Read [Humans & Agents rules](AGENTS.md) and [release signing contract](RELEASE_SIGNING.md).
2. Review [pre-release audit and open gates](PRE_RELEASE_AUDIT_2026-10-09.md).
3. Run Android host/source checks and the CI debug build/lint/tests.
4. Complete the physical-phone checklist in [V1_ACCEPTANCE.md](android-app/V1_ACCEPTANCE.md).
5. Confirm the package ID, monotonically increasing version code, production signer SHA-256, and release workflow.
6. Publish only by pushing the exact `vX.Y.Z` tag to a commit already on `main`; verify the resulting GitHub Release has the expected APK/AAB and checksums.

## Build and test

Requirements: JDK 17 and Android SDK/Gradle setup. From the repository root:

```bash
npm test
cd android-app
sh ./tools/check-v1-source.sh
sh ./tools/run-storage-recovery-tests.sh
sh ./tools/run-attachment-tests.sh
sh ./tools/run-portable-backup-tests.sh
./gradlew --no-daemon assembleGithubSideloadDebug assemblePlayDebug lintGithubSideloadDebug lintPlayDebug
```

These commands are complementary: root tests cover the web prototype, host/source tests cover deterministic Java logic and policy markers, and Gradle checks compile/build Android variants. None replaces physical-device acceptance.

## Repository map

- `android-app/` — native Android application, tests, acceptance checklist, design and developer docs.
- `browser-extension/` — separate browser-extension prototype assets.
- `tests/` — web prototype tests.
- `AGENTS.md` — binding human/AI-agent workflow and safety rules.
- `RELEASE_SIGNING.md` — package/version/certificate invariants and publication steps.
- `PROJECT_PLAN.md` — current capabilities, deferred roadmap and architectural decisions.
- `DOCUMENTATION_TRUTH_AUDIT.md` — claim-by-claim evidence and qualification boundaries.
- `PRE_RELEASE_AUDIT_2026-10-09.md` — current audit findings and next priorities.

## License

MIT — see [LICENSE](LICENSE).

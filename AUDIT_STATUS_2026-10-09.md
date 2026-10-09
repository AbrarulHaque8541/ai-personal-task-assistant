# Daymark repository audit — 2026-10-09

## Current verified baseline

This report supersedes the older audit snapshot below. It reflects a live GitHub repository check and CI metadata review, not a physical-device acceptance claim.

- Current main at audit start: `4a0a9e3c558f2f6ccd84742f48d2becb1a1be4e3`.
- Latest published GitHub Release: [v1.0.4](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.4), with APK and AAB assets.
- Source candidate: v1.0.5 / versionCode 6. The exact `v1.0.5` tag had not been created; therefore v1.0.5 was not published.
- Main CI run #314: [run 37942559451](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/37942559451) passed. It validated protected signing secrets, restored the keystore, built signed candidate APK/AAB artifacts, and verified the APK certificate against the pinned publisher certificate.
- Package ID remains `com.cue.daymark`; pinned production signer SHA-256 remains `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`.
- Android Gradle target is API 35. API 36 migration/edge-to-edge/predictive-Back qualification is still needed before claiming Google Play submission readiness.
- The repository searches returned no open PRs or issues at the start of the follow-up audit. Recheck live state before release.

## Merged pre-release hardening (PR #181)

PR #181 was merged at `1357e9481c31949fc3463ebd2a6989e36051a3cf`; main CI run #331 passed.

- Replaced the flat legacy More menu with grouped settings rows and a short entrance transition.
- Added an explicit review and **Trust & add** confirmation before imported extension code is persisted.
- Added persisted opt-in network-image blocking and source regression coverage.
- Fixed the release-contract mismatch: `GitHubReleaseClient` rejects a stable release without a `daymark-updater-v1` metadata block, but the production workflow previously generated notes without it. The workflow now derives min SDK from the built APK and includes package/version/min-SDK/signer metadata. The current v1.0.4 release body is empty, so the next workflow-published release should contain the required block.
- Refreshed the root README, agent rules, release guidance, project plan and audit notes.

## Reader Mode merged; release candidate state

PR #182 added a real local text-only Reader Mode, separate from the live full-screen WebView. PR #184 added adjustable text size, sans/serif font choice, light/sepia/dark themes, and compact mobile controls. The appearance update was merged at `e3033ef74e370bd5b4c06b1b20c1fcc927386fb6`; main CI run #351 passed signed candidate APK/AAB generation and release artifact metadata verification. No exact `v1.0.5` tag or public v1.0.5 release exists yet. Physical-device QA remains outstanding.

## Evidence boundaries

- **PASS:** named automated check completed successfully.
- **SOURCE-VERIFIED:** current source/configuration was inspected; runtime is not implied.
- **PARTIALLY VERIFIED:** host tests cover the logic but Android integration remains.
- **NOT TESTED:** no qualifying emulator/device test was run.
- **PUBLISHED:** exact GitHub Release/tag exists with the expected assets.

No physical Android phone or emulator was operated during this audit. Install/update, task/backup/attachment workflows, real Android Keystore and SAF provider behavior, TalkBack, keyboard/insets, WebView lifecycle, media downloads, and real-device performance remain unverified. See [PRE_RELEASE_AUDIT_2026-10-09.md](PRE_RELEASE_AUDIT_2026-10-09.md), [DOCUMENTATION_TRUTH_AUDIT.md](DOCUMENTATION_TRUTH_AUDIT.md), and [V1_ACCEPTANCE.md](android-app/V1_ACCEPTANCE.md).

Scope note: this report reflects a live GitHub source/metadata/CI audit, not a claim that every line/device scenario was exhaustively tested. Commit `0c55048f6e3cea794de930212cf2e0284dd0a86c` is the main head at the time of writing.

## Changes actually completed
- Merged PR #159: README release downloads now point at the latest published stable v1.0.2 rather than a nonexistent v1.0.3 APK; release publishing instructions gate on signer continuity. Commit `bfe4d5f8b716d34c835780aba0809ceb1bfe07c7`.
- PR #157 merged: added host JVM regression tests for extension-pack parsing, userscript metadata, WebExtension content-script-only conversion, archive limits and cosmetic filter rules; fixed the `@run-at` hyphenated metadata parsing bug. Merge commit `0c55048f6e3cea794de930212cf2e0284dd0a86c`.
- Updated/reopened issue #147 after source audit: notes, due time, reminder presets and subtasks already exist, so the remaining scope is bounded recurrence plus honest reminder lifecycle/backup/migration verification.
- Reopened issue #137 because closed host/source checks do not equal physical-device browser/update acceptance.
- Created issue #160 for the Android runtime QA matrix, including accessibility, SAF downloads, WebView state and same-signer update testing.
- Created issue #161 for explicit, unavoidable trust confirmation before imported JS is persisted/enabled, with match-rule and extension-injection regression tests.

## CI evidence
- Main Android CI run: https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/37876293459 — success on its checked SHA before PR #157 merge.
- PR #157 Android CI: https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/37876253840 — success; host regression tests passed after the `@run-at` fix.
- PR #157 device-test artifact workflow: https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/37876253772 — success for build/manifest/APK metadata validation only. It does not install or test on a physical device/emulator.
- PR #157 first attempt exposed the actual @run-at assertion failure: https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/37876193975.

## Release blocker — not changed
- The latest published stable release remains v1.0.2 (`Daymark-v1.0.2.apk`). Stable v1.0.3 does not exist in Releases yet.
- Source is configured as `applicationId=com.cue.daymark`, `versionName=1.0.3`, `versionCode=4`.
- Existing issue #145 reports current production keystore/signing setup does not match the v1.0.2 pinned production certificate `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`.
- Do not publish v1.0.3 or change/rotate secrets until signer continuity is restored and confirmed.

## Current product boundaries observed
- Embedded browser remains Android System WebView, not a full Chrome/Firefox extension engine.
- Current extension importer translates only supported page-local CSS/JS/content-script portions. It does not provide privileged Chrome/Firefox APIs or full CRX compatibility.
- Imported JavaScript runs in page context without a native bridge; users need a clear risk confirmation before trusting third-party code.
- Task model/UI currently contain notes, due time, reminder lead presets and up to 20 subtasks. Reminder behavior is foreground/app-open alert-dialog based; UI explicitly states it does not post Android notifications. Recurrence is absent.
- CI can build and inspect APKs, but real Android behavior (Keystore, SAF providers, TalkBack, keyboard/insets, in-place upgrade/data retention) remains NOT VERIFIED without device/emulator testing.

## Open issues to continue
- #145 production signer mismatch (release blocker)
- #147 recurrence + reminder lifecycle + migration/backup round-trip
- #137 and #160 Android runtime acceptance matrix
- #161 extension-code trust confirmation and matching/injection regression coverage
- #150 GeckoView future prototype is closed in current GitHub state; do not duplicate it unless explicitly reopened with new scope.

## Honest status
- Host/source regression tests: PASS for the runs cited above.
- Android build/manifest/APK metadata check: PASS for the cited device-test artifact run.
- Physical-device/emulator installation: NOT VERIFIED.
- Production v1.0.3 signed release: NOT PUBLISHED / blocked by issue #145.
- No release tag, production secret or release asset was changed by this audit.
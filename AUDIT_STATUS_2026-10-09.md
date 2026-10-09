# Daymark repository audit — 2026-10-09

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
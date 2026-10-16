# Daymark status snapshot — 2026-10-16 0513 UTC

Checked against live GitHub at `2026-10-16T05:13:43Z`.

## Repository and identity
- Canonical repository: [AbrarulHaque8541/daymark](https://github.com/AbrarulHaque8541/daymark).
- Current `main` at audit start: `9be0b7753de3ff240a9c239e2ef36b8e3d770e40`.
- Installed application ID/source namespace: `com.abrar.daymark`; live code search for the retired namespace returned no results.
- Current main build baseline at audit start: `versionName=1.2`, `versionCode=9`, `targetSdk=35`, `compileSdk=36`.

## Theme work
- [PR #307](https://github.com/AbrarulHaque8541/daymark/pull/307) merged as `9be0b7753de3ff240a9c239e2ef36b8e3d770e40`.
- Exact PR head Android CI [38853943131](https://github.com/AbrarulHaque8541/daymark/actions/runs/38853943131) PASS and device-test APK build/verification [38853943096](https://github.com/AbrarulHaque8541/daymark/actions/runs/38853943096) PASS. No production artifact was created. Physical-device theme/contrast/performance inspection remains NOT TESTED.
- Duplicate PR #308 and issue #305 were closed in favor of canonical issue #306 / PR #307.

## Published release
- Latest release at audit time: [v1.2](https://github.com/AbrarulHaque8541/daymark/releases/tag/v1.2), one APK asset, 248,679 bytes, SHA-256 `de3e59757182992d7da5df3c47007720a813f9bed796c21e1a15dd162807bdce`.
- Its metadata states `com.abrar.daymark`, versionCode 9, target SDK 35. This historical release is not modified here.
- Candidate next release baseline: `versionName=1.2.1`, `versionCode=10`, `targetSdk=36`; tag `v1.2` already exists and must not be reused. Production signer pin is unchanged.

## Release blocker and workflow diagnosis
- [Issue #299](https://github.com/AbrarulHaque8541/daymark/issues/299) remains OPEN. The API-29 class-loading crash fix has merged, but exact candidate/device acceptance is not yet complete.
- Prior branch `fix/release-integrity-cold-start-20261016` was 9 commits ahead and 5 behind main. Android CI run [38802365931](https://github.com/AbrarulHaque8541/daymark/actions/runs/38802365931) failed in the emulator action before launching the app: `/usr/bin/sh: 1: set: Illegal option -o pipefail`. This is a shell mismatch in workflow code, not evidence of an APK crash. Its device-test asset build [38802365868](https://github.com/AbrarulHaque8541/daymark/actions/runs/38802365868) passed.
- Current candidate branch at snapshot time: `fix/release-integrity-candidate-20261016`, head `d93a1ac3f8e59785591c35f2aac02ce13a064f92`. It ports artifact integrity checks, removes unsafe instructions to bypass an explicit harmful-app warning, keeps only one installable APK on the release page, and invokes a shared Bash cold-launch helper explicitly.
- Candidate workflows test the signed production APK on Android API 29 and 36 before publication; PR CI also tests the current debug APK on both APIs and checks the published v1.2 APK against its pinned SHA-256.

## Verification boundaries
- Candidate CI has not completed at snapshot creation.
- No new release tag or production artifact was created by this candidate branch.
- Owner's physical phone remains NOT TESTED. Do not report release readiness until exact-head host checks, Android build/lint, signed artifact verification, API-29/API-36 emulator cold launch, and owner-device cold launch are evidenced.

---
Work by: ChatGPT
Model: GPT-6
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-16T05:13:43Z

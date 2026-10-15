# Daymark release-integrity and cold-start status — 2026-10-15 20:08 UTC

Checked against live GitHub at `2026-10-15T20:09:29.613Z`. This snapshot is a point-in-time audit; re-check the exact PR head and Actions results before relying on any status.

## Live repository and release state

- Repository: [AbrarulHaque8541/daymark](https://github.com/AbrarulHaque8541/daymark), default branch `main`.
- Main SHA at audit start: `e9c1d78aca1c403ece95f6c4f9d8ad2538ab65bf`.
- Main source baseline at audit start: application ID/namespace `com.abrar.daymark`, `versionName=1.2`, `versionCode=9`, compile/target SDK 36.
- Latest published release observed: [v1.1](https://github.com/AbrarulHaque8541/daymark/releases/tag/v1.1). It contains one APK asset (248,282 bytes; SHA-256 `d0a6556582698d2a972249f68400118777422316c3b05f38fe62a946540156ed`) and one AAB asset (244,631 bytes). The AAB is not directly installable; public release pages should offer exactly one installable APK. These byte counts alone do not prove the APK is incomplete.
- Owner reports duplicate/redirecting downloads, a harmful-app warning, a suspiciously small download, and an immediate crash. No device logcat or copied startup stack trace is available in this live audit; the physical-device crash root cause remains **UNCONFIRMED**.

## Verified blockers

- Main Android CI run [38797265580](https://github.com/AbrarulHaque8541/daymark/actions/runs/38797265580) failed the source gate because `check-window-insets.py` expected API-30 calls directly in `MainActivity`, while current code routes them through `Api30Window`.
- Main v1.2 production workflow [38797269363](https://github.com/AbrarulHaque8541/daymark/actions/runs/38797269363) failed release lint with 19 errors / 121 warnings; the first reported issue was an API-29 window call in the API-30 helper.
- PR #301 is open and its latest observed CI failed. It is working on release README/link cleanup and a target-SDK change; targetSdk reduction alone is not evidence of a crash fix.
- Startup issue [#299](https://github.com/AbrarulHaque8541/daymark/issues/299) has been reopened and tracks APK integrity, release download safety, CI, emulator launch, and owner-device acceptance.
- PR #301 coordination comment requests removal of instructions to blindly bypass a harmful-app warning, a fix for its package-cleanliness CI failure, and accurate agent attribution.

## Current fix branch

- Branch: `fix/release-integrity-cold-start-20261016`.
- Branch head before this snapshot commit: `e28cba6c421ee446ac2482776a567e0eaef26446`.
- Changes include API-29/API-30 helper isolation; a source guard aligned with the helper architecture; APK size/ZIP/manifest/resources/DEX/launcher checks; API-36 emulator cold-launch gates for PR debug APKs and signed production APKs; safe Play Protect warning language; and version baseline `1.2.1` / versionCode 10 because tag `v1.2` already exists.
- The release workflow is intended to publish exactly one installable APK; the AAB remains in the Actions artifact.

## Evidence boundaries and next actions

- **Source changes committed:** yes, on the fix branch; review the current head before merging.
- **CI for the current branch head:** NOT YET VERIFIED in this snapshot.
- **APK integrity/signature:** NOT YET VERIFIED for a new candidate.
- **API-36 emulator cold launch:** new gate added; NOT YET RUN on this branch head.
- **Owner's physical phone:** NOT TESTED. Do not close #299 or claim the crash fixed until the owner installs the exact signed candidate and confirms cold launch. Record device model, Android API, APK SHA-256, and result.
- Do not publish while source/host checks, release lint, signed-artifact checks, emulator launch, or required owner-device acceptance is failing/unverified.

---
Work by: ChatGPT
Model: GPT-6
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-15T20:09:29.613Z

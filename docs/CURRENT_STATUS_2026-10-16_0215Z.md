# Daymark status snapshot — 2026-10-16 0215 UTC

Checked against live GitHub at `2026-10-16T02:15:00Z`.

## Current repository, source, and release

- Repository: [`AbrarulHaque8541/daymark`](https://github.com/AbrarulHaque8541/daymark) (redirected from `AbrarulHaque8541/ai-personal-task-assistant`).
- Live `main`: `e55727b989305fd24fccc5ef49c4e0f33a2b2b8d` (PR #301 merged).
- Android build identity: `applicationId com.abrar.daymark`, `versionName 1.2`, `versionCode 9`, `compileSdk 36`, `targetSdk 35`, `minSdk 26`.
- Android CI run on `main` [38837193144](https://github.com/AbrarulHaque8541/daymark/actions/runs/38837193144): **PASS**.

## Cold-launch crash fix (Issue #299)

- Root cause diagnosed and fixed: On Android 10 (API 29), `MainActivity` called `Api30Window.relaxBarContrast(window)`. Class-loading `Api30Window` on API 29 caused `NoClassDefFoundError` / `VerifyError` because `Api30Window` referenced `WindowInsets.Type` (API 30+).
- Fix:
  - Extracted `relaxBarContrast` into `Api29Window.java` (`@SuppressLint("NewApi")`, touches only API 29 `Window.setStatusBarContrastEnforced` and `Window.setNavigationBarContrastEnforced`).
  - Restricted `Api30Window.java` strictly to API 30+ calls (`setDecorFits`, `applyInsets`).
  - Gated `MainActivity` to invoke `Api29Window.relaxBarContrast` on `SDK_INT >= 29`. Devices on API 26–29 now never load `Api30Window`.

## PR Queue Reconciliation

- **PR #301:** Merged into `main` (`e55727b989305fd24fccc5ef49c4e0f33a2b2b8d`). Locked `targetSdk = 35`, updated README release links and download guidance.
- **PR #303:** Closed as superseded by PR #301. Its window-insets guard adaptation and README updates were already incorporated.
- **PR #302:** Closed due to merge conflicts across 13 files with `main` and failing CI emulator runner script syntax. The core `Api29Window` isolation was cleanly extracted and applied to `main`.

## Verification

- `npm test`: PASS (13/13 tests pass).
- `bash android-app/tools/check-v1-source.sh`: PASS.
- `python3 android-app/tools/check-api36-readiness.py android-app`: PASS.
- `bash android-app/tools/check-release-identity.sh`: PASS.
- `python3 android-app/tools/check-window-insets.py`: PASS.
- `bash android-app/tools/run-check-guard-tests.sh`: PASS.

---
Work by: Lovable
Model: not disclosed by runtime
Tooling: GitHub API connector, bash sandbox
Timestamp (UTC): 2026-10-16T02:15:00Z

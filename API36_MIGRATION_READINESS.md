# Android API 36 readiness

> **Status checked 2026-10-13 against `main` `43fbe7670ad19ff2809f916f1b7dace4bc5ceb04`.** Current source uses application ID/namespace `com.abrar.daymark`, `versionName = 1.0`, `versionCode = 7`, `compileSdk = 36`, and `targetSdk = 36`. The latest published release remains **v1.0.5**, whose APK targets API 35 and belongs to a separate, older application identity. The current source line has **not** been published; it installs separately, and there is no automatic task-data migration. API 36 source/workflow gates are present, but Android 16 runtime/device acceptance and a signed API 36 production artifact are still **NOT TESTED / NOT COMPLETE**.

## Policy

Google Play's documented requirement for ordinary mobile apps is API 36 or higher for new submissions and updates from 2026-08-31, subject to listed exceptions. This is a Play-submission requirement, not a blanket requirement for local builds or GitHub sideloading. Check the [official target API guidance](https://developer.android.com/google/play/requirements/target-sdk) before a future Play submission.

## Source and workflow gates on main

- PR [#261](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/261) moved the source target to API 36 and aligned predictive Back handling. Current Gradle `compileSdk` and `targetSdk` are both 36.
- PR [#280](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/280) updated the Android Gradle Plugin to 8.12.2 and Gradle Wrapper to 8.13, the documented API-36-compatible toolchain. The former AGP 8.8.2 / Gradle 8.10.2 warning describes an older source snapshot.
- PR [#290](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/290) added target-SDK 36 assertions to production-release and device-test workflows. Its exact-head Android CI [run 38409504613](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38409504613) and device-test APK verification [run 38409505263](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38409505263) passed; production publishing was skipped.
- The current-main build check [run 38412156622](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38412156622) passed on the reviewed main line. A CI/device-test APK is not a signed production release or physical-device acceptance.
- Predictive Back uses an API 33+ `OnBackInvokedCallback`; legacy Back remains for API 32 and lower. The shared handler covers WebView history, fullscreen exit, tab close, and browser-to-task fallback.

## Gates still open

- [Issue #257](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/257) remains the release-readiness tracker. Before any production release, inspect the exact signed artifact and verify application ID, version name/code, min SDK, target SDK 36, pinned signer, and updater metadata against source and release metadata. Production signing/publication occurs only through the exact-version tag workflow; a successful main build does not publish an artifact.
- The latest published **v1.0.5** is still the only production release. It is evidence for its API-35 artifact, not an API-36 production build. Do not infer that the current `1.0`/code-7 source has been published from its Gradle values.
- [Issue #160](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/160) remains the device/emulator acceptance matrix. API 36 Back gestures/buttons, WebView rendering and lifecycle, tabs, fullscreen, keyboard/insets, edge-to-edge, accessibility, storage/backup, and install/update behavior are **NOT TESTED** until recorded against a device/build/APK.
- [Issue #221](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/221) (blank WebView viewport) and [#222](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/222) (browser UX acceptance) still require owner/device evidence. Diagnostics merged to source do not prove visible rendering or fix the reported device behavior.

Record device model, Android API, Android System WebView provider/version, build SHA, APK SHA, scenario, and result for each runtime check. Do not label source checks, host tests, CI builds, or generated APK artifacts as device acceptance.

---
Work by: Cue
Model: not disclosed by runtime
Tooling: GitHub connector CLI, git, local source and workflow review
Timestamp (UTC): 2026-10-13T14:42:14Z

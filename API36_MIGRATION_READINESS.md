# API 36 target migration readiness

> **Status refreshed 2026-10-13 against live `main` `efbc1cce87dc118ffdf959a14ce75199a8f860c4`.** The API 36 source/toolchain migration is on `main`; Android 16 device and production-release qualification are not complete. Current `main` and the published v1.0.5 release remain on the existing identity/version line (`com.cue.daymark`, v1.0.5/code 6; the published artifact targets API 35). Separately, the owner-approved upcoming fresh-install target is `com.abrar.daymark`, versionName `1.0`, versionCode `7` in issue [#285](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/285), tracked by [PR #283](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/283). That PR is based on an older main SHA, is currently `DIRTY`, and has no checks reported; the new identity is **not shipped or merge-ready**. This API 36 status update does not change identity, version metadata, signing, or release behavior.

## Policy status verified 2026-10-11

Google Play requires new app submissions and app updates to target Android 16 / API 36 or higher from 2026-08-31, subject to category exceptions.

- Official policy: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-en
- Android 16 target behavior: https://developer.android.com/about/versions/16/behavior-changes-16

## API 36 source changes now on main

- PR [#261](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/261) moved the source target to API 36 and aligned predictive Back handling. Current Gradle `compileSdk` and `targetSdk` are both 36, and the device-test artifact validator is aligned.
- `MainActivity` registers an API 33+ `OnBackInvokedCallback`; legacy and predictive Back share the same handler for WebView history, fullscreen exit, tab close, and browser-to-task fallback. `onBackPressed()` is retained only for API 32 and lower. The `GestureBackNavigation` lint finding is suppressed on that method only because lint does not model the app's direct framework callback; no AndroidX dependency is added.
- PR [#280](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/280) updated the Android Gradle Plugin to 8.12.2 and Gradle Wrapper to 8.13, the API-36-compatible pair documented by Android. The old AGP 8.8.2 / Gradle 8.10.2 warning describes the earlier source snapshot, not current `main`.
- The source guard checks callback registration/cleanup, target SDK alignment, and the selected AGP/Gradle toolchain. Main Android CI at the reviewed SHA passed: https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38399676414. CI/build success does not establish Android 16 runtime behavior.

## Acceptance before production release

- Android CI and both sideload/Play flavor lint/unit tests pass on the exact release PR head.
- Verify the signed artifact's application ID, pinned signer, monotonic versionCode, and updater metadata against the identity actually selected for that release. The currently published v1.0.5 remains historical evidence for its API-35 artifact, not an API-36 production build.
- API 36 device/emulator: test Back gesture/buttons, WebView history, tab navigation, fullscreen exit, keyboard/insets, navigation modes, cutout, edge-to-edge layout, and bottom navigation.
- Also test notifications, SAF downloads, task/attachment retention, portable backup/restore, HTTPS/WebView paint/navigation, and installation behavior for the release's intended identity.
- Record device model/API, Android WebView provider/version, build SHA/APK SHA, and PASS/FAIL/NOT TESTED for each scenario. Do not infer device acceptance from source checks or debug builds.

## Status

The API 36 compile/target/toolchain changes are on `main`, but Android 16 runtime, visual, lifecycle, and production-artifact checks remain **NOT TESTED / NOT COMPLETE**. Release readiness remains open in [issue #257](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/257), and the canonical device matrix remains open in [issue #160](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/160). Keep the blank-WebView report [#221](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/221) open until actual device evidence exists.

---
Previous proposal: Work by ChatGPT; Model GPT-6; Tooling: GitHub MCP tools; Timestamp (UTC): 2026-10-11T18:06:00Z.

Current status update:
Work by: Cue
Model: not disclosed by runtime
Tooling: GitHub connector CLI and local source/documentation review
Timestamp (UTC): 2026-10-13T13:20:53Z

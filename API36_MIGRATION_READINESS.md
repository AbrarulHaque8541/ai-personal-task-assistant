# API 36 target migration readiness (draft)

## Policy status verified 2026-10-11

Google Play requires new app submissions and app updates to target Android 16 / API 36 or higher from 2026-08-31, subject to category exceptions.

- Official policy: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-en
- Android 16 target behavior: https://developer.android.com/about/versions/16/behavior-changes-16

## Migration-specific source changes in this branch

- Gradle `compileSdk` and `targetSdk` use API 36; the device-test artifact validator is aligned.
- `MainActivity` registers an API 33+ `OnBackInvokedCallback`; legacy and predictive Back share the same handler for WebView history, fullscreen exit, tab close and browser-to-task fallback. `onBackPressed()` is retained only for API 32 and lower. The new `GestureBackNavigation` lint finding is suppressed on that method only because lint does not model the app's direct framework callback; no AndroidX dependency is added.
- Base `main` paired API 36 with AGP 8.8.2 / Gradle 8.10.2, although official AGP 8.8 support stops at API 35. This branch updates to AGP 8.12.2 / Gradle 8.13, the API 36-compatible pair documented by Android.
- The source guard checks callback registration/cleanup, target SDK alignment, and the selected AGP/Gradle toolchain.
- Unrelated changes from main, including the task-list render gate and WebView hardware acceleration, must be preserved during review.

## Acceptance before production release

- Android CI and both sideload/Play flavor lint/unit tests pass on the exact PR head.
- Signed artifact verification must confirm application ID `com.abrar.daymark`, pinned signer, monotonic versionCode and updater metadata. This approved identity reset intentionally creates a separate app from legacy `com.abrar.daymark`; tag `v1.0` must match `versionName 1.0`.
- API 36 device/emulator: test Back gesture/buttons, WebView history, tab navigation, fullscreen exit, keyboard/insets, navigation modes, cutout and bottom navigation.
- Also test notifications, SAF downloads, task/attachment retention, portable backup/restore, HTTPS/WebView paint/navigation and upgrade from previous production-signed release.
- Record device model/API, Android WebView provider/version, build SHA/APK SHA, and PASS/FAIL/NOT TESTED for each scenario.

## Status

This remains a migration proposal; CI cannot verify predictive-back UX, edge-to-edge rendering, WebView paint or device integration. API 36 runtime acceptance remains required.

---
Work by: ChatGPT
Model: GPT-6
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-11T18:06:00Z

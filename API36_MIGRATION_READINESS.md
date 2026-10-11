# API 36 target migration readiness (draft)

## Policy status verified 2026-10-11

Google Play requires new app submissions and app updates to target Android 16 / API 36 or higher from 2026-08-31, subject to category exceptions.

- Official policy: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-en
- Android 16 target behavior: https://developer.android.com/about/versions/16/behavior-changes-16

## Migration-specific source changes in this branch

- Gradle `compileSdk` and `targetSdk` use API 36; the device-test artifact validator is aligned.
- `MainActivity` registers an API 33+ `OnBackInvokedCallback`; legacy and predictive Back share the same handler for WebView history, fullscreen exit, tab close and browser-to-task fallback. The old `onBackPressed()` remains for pre-33 compatibility.
- The source guard checks callback registration/cleanup and target SDK alignment.
- Unrelated changes from main, including the task-list render gate and WebView hardware acceleration, must be preserved during review.

## Acceptance before production release

- Android CI and both sideload/Play flavor lint/unit tests pass on the exact PR head.
- Signed artifact verification confirms package ID `com.cue.daymark`, pinned signer, monotonic versionCode and updater metadata. No production signer, package ID, or release tag is changed by this PR.
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

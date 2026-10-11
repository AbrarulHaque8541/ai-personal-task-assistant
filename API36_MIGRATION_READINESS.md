# API 36 target migration readiness (draft)

## Policy status verified 2026-10-11

Google Play requires new app submissions and app updates to target Android 16 / API 36 or higher from 2026-08-31, subject to category exceptions. Daymark's existing installed release can still be sideloaded, but this source branch is not evidence of Play submission readiness by itself.

- Official policy: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-en
- Android 16 target behavior: https://developer.android.com/about/versions/16/behavior-changes-16

## Known migration-sensitive source

- `MainActivity.onBackPressed()` implements WebView back history, tab close, fullscreen exit and Web-to-Tasks switching. Android 16 target-36 apps need supported predictive Back handling; blindly changing only targetSdk is unsafe.
- `MainActivity.applySystemBarsAndInsets()` relies on native edge-to-edge/insets behavior. Test API 26, 29, 35 and 36 paths, including keyboard visible, navigation modes, display cutout and bottom navigation.
- `.github/workflows/android-release-assets.yml` validates APK target SDK; it must agree with Gradle or valid artifacts will be rejected.

## Acceptance before production release

- Run Android CI and both sideload/Play variant lint/unit tests on the exact PR head.
- Build APK/AAB using the existing protected signing workflow and verify package ID `com.cue.daymark`, pinned signer, versionCode monotonicity and updater metadata. Do not change production signer or issue a production tag from this branch.
- Test API 36 on a device/emulator: system Back gesture and buttons, tab navigation, fullscreen exit, keyboard/IME/insets, notification permission/reminder, download SAF flow, task and attachment retention, WebView painting/navigation, and upgrade from the previous production-signed version.
- Do not label any scenario DEVICE VERIFIED unless actually run; attach device model/API/build SHA/APK SHA and PASS/FAIL/NOT TESTED.

## Status

This is a scoped compatibility proposal. Code/source assertions can catch accidental configuration drift, but they cannot validate predictive-back UX, edge-to-edge rendering or WebView behavior. API 36 runtime QA remains required.

---
Work by: ChatGPT
Model: GPT-6
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-11T18:10:00Z

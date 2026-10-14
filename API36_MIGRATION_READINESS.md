# Android 16 / API 36 target and release readiness

> **Checked 2026-10-13 against `main` `a36bf7467b5cbe4c0b5162cec68412680447c33d`.** Current source uses application ID/namespace `com.abrar.daymark`, `versionName = 1.0`, `versionCode = 7`, `compileSdk = 36`, `targetSdk = 36`, AGP 8.12.2, and Gradle 8.13. The latest published release remains v1.0.5 (API 35, previous app identity); current source has not been published and installs separately with no automatic task-data migration. API 36 CI gates are present, but a signed current-identity production artifact and physical-device acceptance remain **NOT TESTED / NOT COMPLETE**.

## Repository-name transition

PR [#295](https://github.com/AbrarulHaque8541/daymark/pull/295) merged preparation for future `daymark` repository URLs. As checked through the GitHub API on 2026-10-13, the actual repository slug is still `AbrarulHaque8541/ai-personal-task-assistant`; the owner has not applied the repository rename. Prepared `daymark` links in the repository may therefore not resolve until that separate owner action. This review preserves those future-name references and does not rename the repository.

## Google Play target policy

Google Play's [target API requirement](https://developer.android.com/google/play/requirements/target-sdk) says that, starting 2026-08-31, ordinary mobile new apps and app updates must target API 36 or higher, subject to stated category exceptions. This is a Play-submission requirement, not a blanket requirement for local builds or GitHub sideloading. The current source meets the target-SDK threshold; that alone does not establish full Play or release readiness.

## Source and workflow evidence

- PR [#261](https://github.com/AbrarulHaque8541/daymark/pull/261) moved source to API 36 and aligned predictive Back handling; current Gradle `compileSdk` and `targetSdk` are both 36.
- PR [#280](https://github.com/AbrarulHaque8541/daymark/pull/280) updated the toolchain to AGP 8.12.2 and Gradle 8.13. The earlier AGP 8.8.2 / Gradle 8.10.2 warning is historical, not a current-main limitation.
- PR [#290](https://github.com/AbrarulHaque8541/daymark/pull/290) added API 36 SDK provisioning and target-SDK assertions to release workflows. Exact-head Android CI [run 38409504613](https://github.com/AbrarulHaque8541/daymark/actions/runs/38409504613) and device-test APK verification [run 38409505263](https://github.com/AbrarulHaque8541/daymark/actions/runs/38409505263) passed; production publishing was skipped.
- Current-main commit `a36bf7467b5cbe4c0b5162cec68412680447c33d`, which includes merged PR [#297](https://github.com/AbrarulHaque8541/daymark/pull/297), passed its Android build check [run 38438713728](https://github.com/AbrarulHaque8541/daymark/actions/runs/38438713728). A CI build/device-test APK is not a signed production release or a physical-device test.

## Gates still open

- [Issue #257](https://github.com/AbrarulHaque8541/daymark/issues/257) remains open as the release-readiness tracker. Before any production release, verify the exact signed artifact's application ID, version name/code, min SDK, target SDK 36, pinned signer, updater metadata, workflow run, and attached release assets. Production publication requires the exact-version tag workflow and owner authorization.
- The only current published release is [v1.0.5](https://github.com/AbrarulHaque8541/daymark/releases/tag/v1.0.5). It targets API 35 and belongs to the previous application identity; it is not an API 36 production build. Do not infer publication from current Gradle values or a CI artifact.
- Open [issue #160](https://github.com/AbrarulHaque8541/daymark/issues/160) tracks the device/emulator acceptance matrix. Open [#221](https://github.com/AbrarulHaque8541/daymark/issues/221) tracks the reported blank WebView viewport, and [#222](https://github.com/AbrarulHaque8541/daymark/issues/222) tracks browser UX acceptance. API 36 Back, WebView rendering/lifecycle, tabs, fullscreen, keyboard/insets, edge-to-edge, accessibility, storage/backup, reminders, and install/update behavior remain **NOT TESTED** until recorded against an identified device/build/APK.

Record device model, Android API, Android System WebView provider/version, build SHA, APK SHA, scenario, and result for every runtime check. Do not label source checks, host tests, CI builds, or generated APK artifacts as device acceptance.

---
Work by: Cue
Model: not disclosed by runtime
Tooling: GitHub connector CLI, git, local source/workflow/documentation review
Timestamp (UTC): 2026-10-13T17:59:30Z

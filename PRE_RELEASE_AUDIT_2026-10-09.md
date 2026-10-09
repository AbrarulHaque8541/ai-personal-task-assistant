# Daymark pre-release audit — 2026-10-09

## Scope and honesty

This audit began against main `4a0a9e3c558f2f6ccd84742f48d2becb1a1be4e3`. PR #181 is now merged at `1357e9481c31949fc3463ebd2a6989e36051a3cf`; main CI run #331 passed host/source checks, signed candidate build, and signer verification. PR #182 is the current Reader Mode follow-up. This is a source/repository/official-documentation review plus automated CI evidence; it is **not** a claim of exhaustive testing on a physical phone.

## Verified baseline

- Latest published GitHub release: [v1.0.4](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.4), with APK/AAB assets.
- Source candidate: `versionName = 1.0.5`, `versionCode = 6`; v1.0.5 was not published at audit time.
- Main CI run #314: [run 37942559451](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/37942559451) passed on the checked main SHA, including host checks, signing-secret validation, signed APK/AAB build, and APK signer match. This is not a physical-device test and does not itself publish a GitHub Release.
- Package ID: `com.cue.daymark`. Production certificate pin: `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`.
- No open PRs or open issues were returned by the GitHub repository searches at the start of this audit; recheck live state before release.
- Android project is native Java with platform APIs and System WebView; the root HTML/CSS/JS project is a separate web prototype, not the Android app UI.

## Findings and priority

### P0 — Release evidence and on-device acceptance

1. **Do not treat v1.0.5 as published until the exact tag workflow succeeds.** A signed candidate artifact in CI is not the public release. Keep the owner-controlled tag step separate from source changes.
2. **Physical phone QA remains mandatory.** Test first install, in-place update over the same production signer, task creation/edit/delete/undo, encrypted backup export/import, attachments, browser Back/tabs/downloads, extension enable/disable, More menu, Reader Mode extraction/copy, image blocking, keyboard/insets, and TalkBack. Record device/API/WebView version and results.
3. **Android target API:** Gradle currently targets API 35. Google's current Play requirement is API 36+ for ordinary new apps and updates submitted from 2026-08-31 ([official policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-IN)). Personal sideload use is a separate path, but do not claim Play-ready until API 36 migration and edge-to-edge/predictive-Back qualification are complete. Android's [API 36 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16) call out edge-to-edge and predictive Back changes.

### Merged in PR #181 — UX, extension trust, release metadata, and image blocking

1. The More screen was an old-style flat `AlertDialog.setItems` list. PR #181 replaced it with grouped settings rows, explanatory subtitles, clearer labels, and a restrained entrance transition.
2. The extension import path previously called `installUserPack` immediately after parsing. PR #181 added a pre-install review showing source/type, CSS/JS size, run timing, match scope, parser warnings, and an explicit **Trust & add** confirmation warning about logged-in page content and script network effects.
3. PR #181 added an opt-in **Block network images (save data)** preference using Android `WebSettings.setBlockNetworkImage`, persisted locally and applied to current/new WebViews. This is a real native feature, but not a full ad/tracker blocker; already loaded images need a reload to disappear.
4. Regression coverage must verify the import flow always routes through the confirmation method and does not persist an imported pack directly. The dialog is a user-facing risk gate, not a sandbox or static security proof; scripts remain untrusted.

### Browser feature research and implementation decision

- Mozilla's current Firefox for Android feature list advertises Reader Mode, supported extensions, and enhanced tracking protection ([Firefox on Google Play](https://play.google.com/store/apps/details?id=org.mozilla.firefox), [Firefox menu features](https://support.mozilla.org/en-US/kb/explore-firefox-android-menu)). Brave documents engine-integrated Shields for ad/tracker/fingerprinting protection ([Brave Shields](https://brave.com/shields/)).
- Daymark embeds Android System WebView rather than owning a Chromium/Gecko browser engine. Reproducing Firefox's extension system or Brave's network-level filtering is not a safe small patch; the app's current extension runtime is intentionally page-local and cannot intercept every network request.
- PR #181 implements the practical native feature available in WebView: opt-in network-image blocking via `WebSettings.setBlockNetworkImage`. It can reduce image traffic but is not an ad blocker, tracker blocker, or guarantee that every image is removed without reloading.
- PR #182 adds a true local text-only view: user-triggered DOM extraction from article/main/body, removal of obvious navigation/form/script elements from a cloned DOM, a 60,000-character bound, native selectable text, and explicit Copy. The extracted content is never rendered as HTML and the original page DOM is not modified. The full-screen live-page view is a separate action. Article heuristics can still be poor on dynamic/non-article pages; real-device accessibility, large-text, memory, and extraction-quality tests remain required.

### P1 — Browser security and capability honesty

- Android System WebView is not a full Chrome/Firefox engine. Keep limitations explicit: no privileged extension APIs, full request interception, `GM_*` storage, or guaranteed video downloading.
- Keep HTTPS-only navigation, no cleartext traffic, safe WebView file-access defaults, and no broad native JavaScript bridge to untrusted pages.
- Official Android guidance: [WebView unsafe file inclusion](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion) and [native bridge risks](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges). Do not weaken these boundaries to imitate a commercial browser feature.
- Direct media handoff works only when a safe direct URL is identifiable; DRM, HLS/DASH segmentation, authentication and provider restrictions may prevent download. Never imply universal media extraction.

### P1 — Data integrity

- Task and attachment storage use app-private encrypted mechanisms; host tests cover defined failure cases but do not prove universal zero-loss behavior.
- Portable backup uses a generated recovery key, not a passphrase. The owner must retain that key separately. Import is add-only and provider behavior varies.
- Do not turn storage/key failures into an empty task list; do not add silent destructive migrations.
- Real Android Keystore invalidation, process-death recovery, SAF rename/read-back/collision behavior and uninstall/reinstall recovery are not proven by host tests.

### P2 — Performance, motion and internal pages

- Existing code uses a single large Activity and multiple programmatically built dialogs/pages. Test on the owner's real device for cold start, typing latency, scroll smoothness, memory pressure with multiple WebViews, long extension lists, and repeated open/close cycles.
- Six browser tabs are an intentional memory-conscious cap; do not increase the cap without device measurements and lifecycle tests.
- Motion should remain short, non-looping, and respectful of Android's system animation scale. Avoid expensive shadows, full-screen blur, or auto-playing effects.
- Internal/nested screens that deserve a visual pass: More/settings, Browser actions/settings, extension list/details/site rules/import review, backup/restore/recovery-key screens, task editor, attachment actions, update verification and file-save steps.

### P2 — Repository knowledge and agent workflow

- The old `AGENTS.md` was materially stale: it cited v1.0.2 as the latest production baseline and described the current browser-fix PR as open. It did not describe current main CI evidence, how to distinguish source/host/device/published claims, the browser's real limits, or a practical branch/PR/test workflow.
- This branch replaces it with explicit human/agent rules, source map, current release baseline, regression-test expectations, personal-use flexibility, security boundaries, and an evidence vocabulary.
- The root README is expanded to explain what exists, what is deferred, where to find details, how to test, and how to publish without mistaking a CI artifact for a release.

## Recommended next sequence

1. Let CI run for this branch; fix any failures it identifies.
2. Review the diff and ensure only intended source/docs/tests changed.
3. Merge after passing CI.
4. Re-run main CI and check for newly opened issues/PRs or stale docs.
5. Only then push `v1.0.5` from current main and wait for the exact-tag production workflow to publish APK/AAB.
6. Install/update on the actual phone and complete the relevant device checklist before treating the release as accepted.
7. Keep API 36 migration as a separate, fully tested change; do not bump target SDK blindly in the release-hardening branch.

## Research references

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-IN)
- [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Android WebView unsafe file inclusion](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion)
- [Android WebView native bridge risks](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)
- [Android WebView management and Safe Browsing](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview)
- Native network-image blocking API: [WebSettings.setBlockNetworkImage](https://developer.android.com/reference/android/webkit/WebSettings#setBlockNetworkImage(boolean))

## Not verified by this audit

No physical device or emulator was operated. No claim is made that animations render perfectly on the user's phone, that TalkBack passes, that API 36 is supported, or that the exact v1.0.5 tag has published.

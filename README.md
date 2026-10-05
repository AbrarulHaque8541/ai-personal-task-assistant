# Daymark — native Android app

The native Android project in [`android-app/`](android-app/) is the intended deliverable. The root web prototype remains a reference only; its browser `localStorage` does not describe Android storage or prove Android behavior.

This review improved normal-light theme text contrast and added a source-derived contrast regression test. `android-app/tools/check-v1-source.sh` passes 25 JDK-only task-logic assertions, manifest/privacy-policy checks, and source assertions for app text scaling, spoken-label strings, English-only disclosure, and device-locale dates. The contrast check covers standard/high-contrast light/dark palettes and reports a minimum tested text ratio of **5.00:1**. These static checks do not replace device UI or TalkBack testing. A debug APK build completed with Gradle 8.10.2 and the installed Android SDK Platform 35; the current debug artifact is `android-app/app/build/outputs/apk/debug/app-debug.apk` (45,031 bytes, SHA-256 `4688733df7429495ae9b74504bc71b703186d7f366b02611e535e57a59afaa71`). It is a debug artifact only, not a release APK or evidence that the `<15 MB` release target is met.

The Android manifest in the built APK declares no permissions. No model, language, plugin, or inference assets are bundled, and no downloader, provider, Termux bridge, or background inference path is implemented. ADB found no connected device; install, launch, offline-runtime, TalkBack, locale, and real-device text-size/contrast checks remain unverified. The debug rebuild used the preinstalled SDK only; no package was installed and no new license was accepted. No APK has been published to GitHub Releases. See [`android-app/README.md`](android-app/README.md), [`android-app/V1_ACCEPTANCE.md`](android-app/V1_ACCEPTANCE.md), and [`android-app/ANDROID_SOURCES.md`](android-app/ANDROID_SOURCES.md) for the exact limits, build steps, and official sources.

## Legacy web prototype (reference only)

To preview the static prototype locally, run from the repository root:

```sh
python3 -m http.server 4173 --bind 127.0.0.1
```

Open `http://127.0.0.1:4173` in a browser and stop the preview with Ctrl+C. This serves local files; it does not publish or deploy the prototype.

### Running the web prototype tests

The task-logic unit tests use the Node.js built-in test runner (Node 18+):

```sh
npm test
# or directly:
node --test tests/
```

No install step is needed; the tests have no third-party dependencies.

# Daymark — native Android app

The native Android project in [`android-app/`](android-app/) is the intended deliverable. The root web prototype remains a reference only; its browser `localStorage` does not describe Android storage or prove Android behavior.

> **Debug APK status:** [`Daymark-debug-untested.apk`](Daymark-debug-untested.apk) is a debug build and has not been device-tested. It is not a release APK.

This review improved normal-light theme text contrast and added source-derived contrast regression checks. `android-app/tools/check-v1-source.sh` passes 25 JDK-only task-logic assertions and 34 dependency-free fake updater assertions, plus manifest/privacy, accessibility, and contrast checks. These host/source checks do not replace device UI or TalkBack testing. The Android source now contains a fail-closed GitHub updater scaffold, but the manifest has no permissions, so it makes no network request; publisher signing is unconfigured and no Android installer handoff is enabled. Offline API 35 Java/resource compilation succeeds with Gradle 8.10.2; no updater APK is retained. Debug signing is not suitable for production updates, and no release artifact exists to measure against the `<15 MB` target.

The Android manifest in this source still declares no permissions. Updater tests use fake clients/verifiers only; no network request, updater download, install, or task/history upload occurred. ADB found no connected device; install, launch, offline-runtime, TalkBack, locale, and real-device text-size/contrast checks remain unverified. The compile check used only already-installed SDK components; no APK was installed and no new license was accepted. GitHub has no releases, and no release APK has been published. See [`android-app/README.md`](android-app/README.md), [`android-app/V1_ACCEPTANCE.md`](android-app/V1_ACCEPTANCE.md), and [`android-app/ANDROID_SOURCES.md`](android-app/ANDROID_SOURCES.md) for prerequisites and release gates.

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
node --test
```

No install step is needed; the tests have no third-party dependencies.

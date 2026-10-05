# Daymark — native Android app

The native Android project in [`android-app/`](android-app/) is the intended deliverable. The root web prototype remains a reference only; its browser `localStorage` does not describe Android storage or prove Android behavior.

> **Debug APK status:** [`Daymark-debug-untested.apk`](Daymark-debug-untested.apk) is a debug build and has not been device-tested. It is not a release APK.

This review improved normal-light theme text contrast and added source-derived contrast regression checks. `sh android-app/tools/check-v1-source.sh` passes 25 JDK-only task-logic assertions, 59 updater-policy assertions, and 74 parser/transport/download assertions, plus manifest/privacy, accessibility, and contrast checks. These host/source checks do not replace device UI or TalkBack testing. The GitHub sideload updater is implemented in source but fail-closed: only that flavor declares `INTERNET` and `ACCESS_NETWORK_STATE`; the shared and Play manifests declare none. It checks GitHub in the foreground, requires a per-download choice (Wi-Fi only by default or explicitly allow mobile data), downloads only after confirmation, and verifies size, hash, package, version, minimum Android version, and signer before offering user-directed file saving. Daymark has no `REQUEST_INSTALL_PACKAGES`, `PackageInstaller` session, install-source Settings shortcut, or app-launched installer; the user must open the saved APK from Files and accept any Android-controlled approval. The production signer/release gate remains closed, with an empty signer pin and no production key or stable release. Offline API 35 Java/resource compilation succeeds with Gradle 8.10.2; no APK was assembled or retained in this updater task. Device-side signer parsing, file-picker/open behavior, Android confirmation UI, and install tests remain unverified. Debug signing is not suitable for production updates, and no release artifact exists to measure against the `<15 MB` target.

The shared and Play manifests declare no permissions; the GitHub sideload flavor declares only `INTERNET` and `ACCESS_NETWORK_STATE`. Source tests use fake clients, transport, and signer metadata: they caused no live request, APK download, installation, or task/history upload. They do not exercise Android’s real APK signer parser, file picker, source-approval Settings, or system install confirmation. ADB found no connected device; installation, launch, offline runtime, TalkBack, locale, and real-device text-size/contrast checks remain unverified. This task uses only already-installed SDK components and produces no APK. GitHub has no stable release APK or production signing setup. See [`android-app/README.md`](android-app/README.md), [`android-app/V1_ACCEPTANCE.md`](android-app/V1_ACCEPTANCE.md), and [`android-app/ANDROID_SOURCES.md`](android-app/ANDROID_SOURCES.md) for prerequisites and release gates.

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

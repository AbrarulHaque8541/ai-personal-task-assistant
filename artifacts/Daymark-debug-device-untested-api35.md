# Daymark API 35 debug APK — device-untested

**Status: debug / device-untested.** This is a locally built debug APK for testing, not a release artifact.

- Package: `com.cue.daymark` (`1.0.0`), minimum API 26, target/compile API 35.
- APK size: 51,831 bytes.
- SHA-256: `e80428836bcac97ea6655cf9d865a7eb7b9d7bbcd7c4f6859c308d056b533724`.
- Source commit: `aac189a5b6fe177fd4fbe480232ff81390f3e139`.
- Source tree: `4224f44c21ef365f9ccb805b81dd19577b8a8260`.

## Build and checks

Built from the source commit above with Android Gradle Plugin 8.8.2, Gradle wrapper 8.10.2, OpenJDK 21.0.12.1, Android SDK Platform 35, and Build-Tools 35.0.0. No package installation or SDK license acceptance was performed.

Command, run from `android-app/`:

```sh
ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" \
  ./gradlew clean :app:assembleDebug --offline --no-build-cache --no-daemon --console=plain
```

Checks passed: `npm test` (8/8); `sh ./tools/check-v1-source.sh` (36 core assertions plus source, manifest, backup, accessibility/localization, and contrast checks); `sh ./tools/run-storage-recovery-tests.sh` (45 storage assertions and 27 storage-failure UI/source checks); and `git diff --check`. The offline debug build succeeded (33 Gradle tasks). The APK passed ZIP integrity, 4-byte alignment, manifest/package/API inspection, and `apksigner verify`; it is signed with the Android debug key (APK Signature Scheme v2).

## Limitations

No physical device or emulator test was performed: `adb` is unavailable in this environment and no device was attached. Android API 36 was not tested; only SDK Platform 35 is installed. These checks do not establish on-device behavior, Android Keystore behavior, process-death/recovery behavior, UI behavior, or TalkBack accessibility.
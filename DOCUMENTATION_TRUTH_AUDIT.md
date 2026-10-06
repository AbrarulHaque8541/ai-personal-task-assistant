# Documentation truth audit

**Snapshot:** 2026-10-06, live repository `main` at `66476cb49a6caa9a43b65db39912ee397b6e6857` (PR #24 merge). These labels apply to the narrow claim they describe: **VERIFIED** means source/configuration or live repository state was reproduced; **PARTIALLY VERIFIED** means source/host evidence supports the mechanism but runtime/release evidence is missing; **NOT TESTED** means no qualifying test was run; **PLANNED** and **DEFERRED** describe future work; **INCORRECT** means current source, merged build output, or GitHub release state contradicts the claim.

## Major claims

| Claim or claim family | Classification | Evidence and accurate boundary |
|---|---|---|
| “Zero data loss” / “Zero Loss Guaranteed” | **INCORRECT** | `EncryptedTaskStore` has fail-closed error handling, serialized writes, Android `AtomicFile`, and host recovery tests. `STORAGE_RECOVERY.md` also documents that these checks do not prove durability across all Android implementations or sudden power loss. They reduce specific risks; they cannot establish a universal guarantee. |
| “Secure,” “end-to-end encrypted,” or hardware-backed encryption as an unqualified product guarantee | **PARTIALLY VERIFIED** (mechanism); **INCORRECT** (absolute wording) | Source uses AES-GCM and Android Keystore for app-private task/attachment data; portable archives use authenticated encryption. Android Keystore hardware backing varies by device. Host tests do not validate the real device Keystore, the whole device threat model, or data while in memory. Prefer mechanism-specific wording, not a blanket security guarantee. |
| “Offline” / “offline-ready” | **PARTIALLY VERIFIED** | Task CRUD and local storage are implemented without a cloud task service, and source/host checks exercise local logic. Airplane-mode behavior was **NOT TESTED** on a device. Web mode intentionally sends user-requested traffic; platform-managed WebView Safe Browsing may operate separately from Daymark's page-load switch. Do not promise zero network traffic. |
| “Android 8+” / API 26 support | **PARTIALLY VERIFIED** (configuration); **NOT TESTED** (device compatibility) | Gradle declares `minSdk = 26`, `compileSdk = 35`, and `targetSdk = 35`. Main CI passed debug-variant builds, but no API 26 device/emulator runtime test was performed. Describe API 26 as the declared minimum, not verified device support. |
| “Production ready” / “Play ready” | **INCORRECT** | No production signing setup or pinned publisher signer exists; the updater publisher gate is false. No signed production APK is attached to the release, and no device acceptance is established. Current target API 35 is below Google's API 36 requirement for ordinary new Play submissions and updates from August 31, 2026 ([official requirement](https://developer.android.com/google/play/requirements/target-sdk)). |
| “Fully automated” | **DEFERRED** | No app-owned scheduled work, background polling, or autonomous agent is implemented. The updater source is foreground/manual and currently disabled. Agent/workspace automation remains roadmap material, not a current capability. |
| Attachments are implemented and encrypted | **PARTIALLY VERIFIED** | Attachment source is on current `main`; host tests passed 35 assertions and source checks passed. The feature uses encrypted app-private payloads and explicit SAF selection. Real provider behavior, Android Keystore, UI, and device lifecycle remain untested. |
| Portable backup uses a user passphrase / PBKDF2 | **INCORRECT** | Current source creates a random 32-byte recovery key and uses AES-GCM with HKDF-SHA-256 per-record keys. Passphrase support is deferred. Host protocol tests passed 307 assertions; real document-provider and device restore behavior remains untested. |
| Updater validates then installs automatically | **INCORRECT** | Updater source is present but `UpdaterPublisherConfig` disables it and has an empty publisher signer pin. Its intended flow saves a verified APK to a user-selected document for manual opening; there is no `REQUEST_INSTALL_PACKAGES` or app-launched installer handoff. Runtime behavior is **NOT TESTED**. |
| Only sideload has Internet permission / Play is permission-free | **RESOLVED IN ISSUE #33** | The common manifest declares `INTERNET` for the embedded browser; the merged Play manifest inherits `INTERNET` without extra permissions; the merged sideload manifest contains `INTERNET` and `ACCESS_NETWORK_STATE`. The test-reporting defect has been resolved: check-v1-source.sh and check-merged-manifests.py explicitly verify the merged manifest architecture without inaccurate permission-free claims. |
| Attachments and portable backups exist only on a feature branch | **INCORRECT** | Their source is in the checked `main` tree and their host test suites passed. This does not establish an attached release APK, release qualification, or device behavior. |
| The `v1.0.0` release contains an attached APK | **INCORRECT** | GitHub reports the release as published, with an empty `assets` list. Its release notes instead link to a mutable `raw/main` APK and make stronger claims than the actual verification supports. The release page, tag, and assets were not changed. |
| Release/runtime acceptance | **NOT TESTED** | No device/emulator install, launch, API 26 runtime, TalkBack, real SAF, real Keystore, process-death, live updater, or manual install test was run in this audit. No APK was assembled or installed during this audit. |

The ten-feature roadmap, general model/provider integration, fully autonomous work, and localization packs remain **PLANNED** or **DEFERRED**, as described in [`PROJECT_PLAN.md`](PROJECT_PLAN.md) and [`android-app/V1_ACCEPTANCE.md`](android-app/V1_ACCEPTANCE.md). They are not current `main` capabilities.

## Repository and release artifacts

**Update (2026-10-07):** the two tracked debug APKs recorded below were removed from the repository tree in a focused `chore(repo): remove stale tracked APK artifacts` cleanup. The repository now tracks no `.apk`, `.aab`, `.rom`, or `.zip` binary; the only remaining tracked binary is `android-app/gradle/wrapper/gradle-wrapper.jar`, which is required Gradle build configuration. The digests are retained here as the historical record of the removed files. No GitHub Release, tag, or release asset was created, edited, or deleted by that cleanup.

At the audit snapshot, the repository tracked two different debug APKs; neither is a production release. Their sizes and SHA-256 digests were:

- `Daymark-debug-untested.apk` — 45,031 bytes; `4688733df7429495ae9b74504bc71b703186d7f366b02611e535e57a59afaa71` (removed from the tree).
- `artifacts/Daymark-debug-device-untested-api35.apk` — 51,831 bytes; `e80428836bcac97ea6655cf9d865a7eb7b9d7bbcd7c4f6859c308d056b533724` (removed from the tree; its provenance note remains at `artifacts/Daymark-debug-device-untested-api35.md`).

They are distinct binaries, not duplicate byte-for-byte copies. The second artifact's metadata records source commit `aac189a5b6fe177fd4fbe480232ff81390f3e139`; it is historical and device-untested. Local Gradle output in the audit workspace also contained Git-ignored debug APKs and unsigned release APKs under `android-app/app/build/outputs`; these were generated build output, not tracked release assets. No temporary `.tmp`/`.temp` files were found. At the audit snapshot no generated build output or APK was added, removed, or published by that change.

The published [v1.0.0 release](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.0) has **zero attached assets**. Its release notes link to a mutable `raw/main` artifact and state that the release has no attached asset. The release page, tag, and assets were not edited by the audit or by this cleanup; because the tracked `artifacts/` APK was subsequently removed from the repository, that `raw/main` pointer is now dangling. The repository README download pointer was removed so users are not directed to a stale debug binary.

## Checks reproduced at the audited main SHA

| Command or check | Result |
|---|---|
| `npm test` (repository root) | **PASS** — 8 passed, 0 failed. |
| `sh tools/check-v1-source.sh` (`android-app/`) | **PASS** — 55 core, 70 updater-policy, 120 parser/transport, 35 attachment, and 307 portable-backup assertions, plus source-policy, schema, accessibility, and contrast checks. The printed permission summary is inaccurate (see above). |
| `sh tools/run-storage-recovery-tests.sh` (`android-app/`) | **PASS** — 45 storage assertions and 27 storage-failure UI/source checks. |
| `sh tools/run-attachment-tests.sh` (`android-app/`) | **PASS** — 35 assertions. |
| `sh tools/run-portable-backup-tests.sh` (`android-app/`) | **PASS** — 307 assertions. |
| `./gradlew --offline --no-daemon --console=plain :app:processGithubSideloadDebugMainManifest :app:processPlayDebugMainManifest` (`android-app/`) | **PASS** — `BUILD SUCCESSFUL`; merged Play debug has `INTERNET`, and sideload debug has `INTERNET` plus `ACCESS_NETWORK_STATE`. |
| GitHub Actions Android CI for `66476cb49a6caa9a43b65db39912ee397b6e6857` | **PASS** — host regression checks and debug-variant build/verification ([run](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/37445697952)). This is CI evidence, not device testing. |

The local audit did not assemble an APK, install an APK, test a device/emulator, test Android API 26 runtime behavior, or exercise live network/update or real Android document-provider/Keystore behavior. `adb` was unavailable. The `main` SHA and GitHub release/PR state can change after this snapshot.

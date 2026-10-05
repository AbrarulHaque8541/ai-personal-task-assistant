# Daymark Android V1

A native, single-user, local-first task manager. This is a separate Android project under `android-app`; the existing web prototype in the parent directory remains intact.

## Attached brief: requirement status map

These labels describe the current source, not a delivery promise. **Later opt-in** means a separately approved, tested phase; any optional content or external service must also require a deliberate user choice.

| Requirement area | Status | Current boundary |
|---|---|---|
| Offline task capture, edit, completion, delete/undo, filters, priorities, due dates, deterministic suggestions | **V1 implemented** | Source-level functionality; device/offline runtime checks remain pending. Suggestions are rules, not AI. |
| Encrypted local tasks and attachments | **Source-only · not shipped** | This open stacked feature branch contains task and AES-GCM attachment source; it is not shipped or release-qualified. No APK was installed or uploaded for this review. Attachments use Android's user-driven document picker and require no broad storage permission; device behavior remains untested. |
| Room/SQLCipher repositories, schedules, profiles, schema migrations, encrypted export/recovery | **Later opt-in** | Not in this prototype; first resolve database, key lifecycle, backup, migration, and recovery decisions. |
| GGUF/ONNX/MLC model manager, local file/Hugging Face sources, signed catalogs, artifact verification | **Later opt-in** | No model picker, registry, catalog, importer, or model files ship in V1. |
| CPU inference engine, model runtime, GPU/NPU backends, hardware benchmarks | **Later opt-in** | No inference runtime or model artifact is bundled. Pin, license-review, benchmark, and test each device/ABI before any addition. |
| Optional functionality, models and localization storage | **Later opt-in** | None is present. Optional functionality must not consume installed storage before the user explicitly requests it. Distinguish Play-delivered dynamic feature code modules from separately fetched data/model/locale assets; no arbitrary remote executable code. See the future delivery policy below. |
| Bounded user-requested local data work | **Later opt-in** | If justified, use OS-compliant WorkManager or visible foreground work with cancellation; never schedule model download/update/index/inference or claim indefinite residency. |
| Dynamic downloaded locale JSON | **Later opt-in** | No locale JSON or language pack is currently shipped or downloadable; UI strings are English-only source resources/code. Any future localization must be user-triggered, show exact size before fetching, never auto-fetch, and be cancellable/resumable, integrity-checked, app-private, and fully removable including caches. |
| PRoot/Linux shell, ADB/Termux bridges, app CLI, Accessibility/overlay access, daemons, raw OS integration, plugins/hooks, cross-format/runtime hot-swapping | **Later opt-in** | These need separate security, compatibility, Android-policy, permission, licensing, and real-device tests before any claim. PRoot is user-space chroot-like behavior, not root/chroot privilege or a sandbox escape ([PRoot](https://proot-me.github.io/), [Android sandbox](https://source.android.com/docs/security/app-sandbox)). Keep all access opt-in and least-privilege; do not expose raw shell/secret/path access or bypass platform rules. |
| Assistant providers and Telegram | **Later opt-in** | No network client or credentials. Use only a user-selected provider with no silent cloud fallback, pre-call data/cost disclosure, minimum context, and no prompt/key logs. Telegram needs a separate backend and protected bot token. |
| CI, dependency/SBOM review, signing/reproducibility, API/ABI/device qualification | **Later opt-in** | Release engineering, release signing, and hardware tests remain incomplete; the debug build is not release qualification. |
| Base APK size target: `<15 MB` | **Later opt-in** | Unverified release target only; measure exact bytes after a genuine release build, never use debug size, and make no claim before a release APK exists. An existing debug APK does not qualify. |
| Root, privileged/signature/arbitrary system grants, guaranteed resident daemon, background-limit bypass, universal accelerator support | **Unsupported/platform-limited** | A normal APK cannot grant itself root, privileged/signature-level access, arbitrary system permissions, or disable Android background limits. GPU/NPU support is device-specific and must be measured and verified. |
| Downloading executable code or self-updating plugins | **Unsupported/platform-limited** | No dynamic executable loading in V1 and no arbitrary remote code. Android [security guidance](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading) warns about tampering, data exfiltration/code-execution risks and Play policy violations. If separable Android code is ever justified, use reviewed signed modules through Play Feature Delivery from an Android App Bundle, not arbitrary URL/plugin downloads. |

## V1 experience

- **Simple path (default):** the first screen asks “What do you want to get done?”, gives an everyday example, and offers an editable text field with a one-tap Add action. A new quick-added task starts with no due date and medium priority; that default is stated on screen. **Add with a date or priority** opens the same editable task form with the draft filled in.
- **Power path:** an explicit top-bar switch reveals title search, All / Today / Upcoming / Completed filters, and deterministic Demo suggestions. It uses the **same task records** as Simple; changing paths does not copy, transform, or hide stored data.
- Both paths support task edit, completion, and deletion. Deletion asks first and can be undone for seven seconds. The task editor has Cancel and system Back behavior.
- Attachments support user-selected photos, audio, video, PDFs and other provider-openable documents. They are copied into the app and encrypted at rest. Daymark has no in-app preview and never executes a file. Only files matching a limited MIME/extension allowlist are offered through a **user-confirmed** “Open with another app” chooser; all files remain untrusted, and active, executable, installer and unknown types are not handed to another app.
- Local status is visible. Text entry and date/priority remain editable before saving; a visible message explains the next step after a quick add.

## Accessibility and settings

The app uses native Android controls, descriptive TalkBack labels, scrollable content, keyboard/IME submission, and touch targets of at least 48 dp for primary actions, filters, task controls, and search clearing. **More** provides device/light/dark appearance, Compact/Standard/Extra large app text, a high-contrast palette, permission status, and technical details.

Language is **English-only UI** in this prototype; dates use the device locale. Other translations and right-to-left review are not complete. The screen-reader item points to native Android/TalkBack support; it does not replace Android's accessibility settings. V1 has no custom looping/auto-playing motion, so Android system reduced-motion behavior applies to platform UI; there is no separate in-app reduced-motion switch.

## Local-only operation and content policy

The manifest declares **no permissions**, including `INTERNET`; there is no account, server, telemetry, sync, runtime network client, background service, poller, model runtime, or optional-asset downloader. Core task use needs no permission and triggers no startup prompt. Attachment selection uses the Android document picker only after a user action; a selected document provider may use its own network to retrieve that item, but Daymark does not sync or upload it. The task UI and task records are designed to work offline, including airplane mode; that behavior still requires on-device runtime testing before being called verified.

**More → Permission status** reads the installed app's declared permission list and reports that none are needed in V1. It is read-only information, not a permission center: it does not request, grant, manage, revoke, or automatically enable access. A full permission center is a proposed/deferred UI, not present in the current source. If a future feature needs protected access, explain why and request only the minimum when the user chooses that feature; runtime permissions require the Android system prompt and the user's decision, while special app access must be granted by the user in Android Settings. If access is denied, keep the rest of the offline app usable, explain the affected feature without repeated pressure, and offer the applicable Settings route for revocation or reconsideration. An ordinary app cannot silently grant itself arbitrary system-level access or bypass the platform's privileged/signature-only grant rules. Do not describe an “autopilot” that requests or accepts everything ([Android permissions overview](https://developer.android.com/guide/topics/permissions/overview), [runtime requests](https://developer.android.com/training/permissions/requesting), [special access](https://developer.android.com/training/permissions/requesting-special), [manifest protection levels](https://developer.android.com/guide/topics/manifest/permission-element)).

Make the app UI itself usable with TalkBack and other accessibility tools; do not request `AccessibilityService` as a generic permission or plugin workaround. Android positions that service for assistive technology, and broad event monitoring can be resource-intensive. Keep it deferred unless a concrete assistive use case is approved, then require separate user opt-in, security review, and device testing ([AccessibilityService guidance](https://developer.android.com/guide/topics/ui/accessibility/service)).

**No optional content ships in V1:** no GGUF weights, voice/language packs, compiled inference runtime, plugin packs, bundled demo content, or other model artifacts. The app's small vector launcher icon is UI chrome, not an optional pack. Optional pack sizes are **N/A (none exist)**. A base release APK target of **<15 MB** remains unmeasured until a genuine release build; no release APK has been built, so do not estimate or claim its size. A debug APK exists but is not eligible for this target.

The app performs ordinary foreground UI, file, and Keystore work while open, so literal zero device load is impossible. There is no app-owned background task, polling, inference, or download path. Near-zero idle/background work is a design target, not a measured battery claim.

## Task storage and demo suggestions

Task records use schema-versioned JSON encrypted in `files/tasks.enc` with AES-GCM and an AES key kept by Android Keystore. The reader accepts existing schema v1 task data; current writes use schema v2 to include attachment references. Keys are non-exportable; secure-hardware backing depends on the device. Disk and cryptographic operations run on one background executor. A truly absent store is a valid empty list; malformed data, an unavailable key, or authentication/read failure is a distinct fail-closed state. The app does not replace a missing key for existing ciphertext. Writes use Android `AtomicFile` and its commit/failure path, not a non-atomic replacement fallback. If a save fails, the attempted change remains visible and is explicitly labeled unsaved while edits are paused; leaving the app may discard that visible change, while the last committed encrypted snapshot remains the durable state.

## Task attachments

Choose **Files · N** on a task to attach one photo, audio clip, video, PDF, or other openable document at a time through Android's `ACTION_OPEN_DOCUMENT` system picker. Daymark reads the selected provider stream once and copies it into `noBackupFilesDir/attachments` as a streaming AES-GCM encrypted payload. The v2 payload format's length-delimited AES-GCM associated data binds the canonical app-owned blob ID and owning task ID, so copying ciphertext under another attachment ID or associating it with another task fails authentication. It does not retain the provider URI, provider path, or persistable permission grant; the separately authenticated task snapshot stores only an app-owned ID and sanitized display name, MIME type and measured byte size. A separate Android Keystore key protects payloads, and the attachment folder is outside Android backup even if backup configuration changes later. The unshipped development-only v1 payloads created before this binding are not migrated and fail closed; no release compatibility is claimed.

The manager shows limits before the picker: **20 MiB per file, 100 MiB total, 100 files overall and five files per task**. Both provider size hints and actual streamed bytes are checked; the total limit uses the snapshot's remaining quota and does not trust provider metadata. Import can be cancelled while copying; once the atomic task-reference save starts, cancellation is disabled. Low free space, revoked/missing providers, stream errors and oversize files abort without committing the task reference. Plaintext is never staged: the temporary file is encrypted while streaming, removed on failure, and orphaned encrypted payloads are swept after a successful task load. Removing an attachment saves its task metadata before deleting the local encrypted file. Deleting a task waits until the deletion is saved and its seven-second Undo window expires before cleanup. Missing payloads are identified as unavailable and can be removed without clearing other task data.

To open a supported attachment, the user must tap **Open with another app**, accept a warning and choose a handler. Daymark shares a read-only, temporary content-URI grant backed by an authenticated decrypted pipe; it creates no plaintext temp copy. External applications may retain shared data, and streaming/seek support varies by handler. SVG/HTML/script, installer/executable, macro-enabled and unknown MIME/file types are blocked from this route. Formats outside the allowlist remain safely stored and removable, but cannot be opened from Daymark. These 20 MiB/100 MiB limits are prototype guardrails; larger media and broader format support need a separately tested policy.

Attachments are local to this app install and are not automatically backed up. A missing or invalidated Keystore key has no recovery path in this version. Personal-cloud use is only a possible future direction for provider-neutral, user-triggered encrypted export/import of Daymark data through Android document providers; recovery, key handling and restore semantics must be reviewed before implementation. Selecting an item from a provider for attachment is not Daymark export, cloud sync, or app-data backup.

V1 has no automatic corruption repair, task export, retained backup, or key-recovery scheme. See [STORAGE_RECOVERY.md](STORAGE_RECOVERY.md) for the failure contract, tests, Android references, and recovery decisions intentionally left open.

The encrypted writer and reader share the same task-list validation: every task must be valid, titles must stay within the 160-character limit, and IDs must be unique. This prevents the writer from persisting a duplicate-ID snapshot that the reader would later reject.

The app's **Demo suggestion** ranking is deterministic local code: open tasks by overdue/nearest due date, then high → medium → low priority for equal dates. It is not an LLM, does not access the network, and does not infer intent. Suggestions are not a downloaded asset.

Clearing app data or uninstalling removes the local task file, attachment payloads and keys. Automatic backup is disabled; there is no task export, cloud sync, or cross-install migration. Theme and accessibility preferences are non-sensitive ordinary app preferences.

## Deliberately not implemented

- No voice capture, push-to-talk, speech provider, microphone permission, or transcript creation. A future voice feature must request permission only when used, disclose any off-device audio processing, and let the user edit/review the transcript before a task is created.
- No on-device model/GGUF loader, model download, inference, language pack, plugin/CLI/Termux bridge, OTA/update channel, or hot reload.
- No `AssistantProvider`, remote-provider router, or Telegram connection ships in V1. Selection, disclosure, credential, and hosted-service requirements are documented in the deferred integration section below.
- No fake download, model, voice, or plugin controls are present. Future optional content must require an explicit tap; show exact source, license, verified size, device/storage impact; support progress, cancel, resume, hash check, and complete removal of asset plus cache; never download, update, index, or infer in the background; and unload after a user-requested inference.

### Optional functionality storage and delivery policy (future only)

Optional functionality must not occupy installed storage before the user taps/requests it. These two cases are different:

- **Feature code/resources:** only if separable Android code is justified, use a reviewed, signed dynamic feature module delivered on demand through [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery/on-demand) from an [Android App Bundle](https://developer.android.com/guide/app-bundle). This depends on Google Play distribution; it is not a generic delivery path for a standalone APK or ordinary sideloaded build. Do not fetch executable code from arbitrary remote URLs or run downloaded plugins.
- **Data/model/locale assets:** treat these as data, not code, and fetch only after an explicit user request. Before any transfer show source/provider, license, exact download size, installed-storage impact, network/mobile-data usage, device compatibility, and what removal will clear. Provide visible progress, cancel, retry when offline, integrity/hash verification, and full removal including caches; no pre-tap fetch, background prefetch, auto-update, indexing, or inference.

Future assets require authenticated source metadata (for example, a signed manifest with hashes), integrity checks before use, a reviewed license and compatibility, and private storage. A data pack must not smuggle executable code or be interpreted as code. No module or asset download path is implemented now. Android's [dynamic code loading guidance](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading) recommends avoiding dynamic code loading unless needed, warns about tampering/data-exfiltration/code-execution, and notes many remote-loading forms may violate Google Play policies.

## Deferred optional integrations — future only, not implemented

Everything below is a future design reference, not a V1 feature. There are currently no provider adapters or router, cloud API clients, Telegram bridge, Internet permission/network client, model runtime, or provider credentials in the app. These are optional cloud or hosted-bridge ideas—not on-device integrations and not part of the offline core. The local task workflow and existing no-download acceptance criteria remain unchanged.

- **DeepSeek API:** DeepSeek Open Platform is a hosted LLM inference API requiring a Bearer API key, with token-based pricing and HTTP 429 when rate limits are exceeded. The reviewed API references do not settle API-specific retention, data-residency, or key-revocation details; verify the applicable privacy terms and key controls before any future use ([API docs](https://api-docs.deepseek.com/), [pricing and limits](https://api-docs.deepseek.com/quick_start/pricing/), [terms](https://cdn.deepseek.com/policies/en-US/deepseek-open-platform-terms-of-service.html)).
- **Harness — exact target unresolved:** The user's “Harness” reference is not assumed to mean “DeepSeek Harness.” DeepSeek's preview-stage open-source desktop/web Harness is only a design reference, not an Android library; Harness's own API docs describe a DevOps platform rather than a generic LLM inference API. Keep any Harness integration deferred until the exact product and use case are identified ([DeepSeek Harness overview](https://www.deepseek.com/en/harness/), [DeepSeek Harness repository](https://github.com/deepseek-ai/deepseek-harness), [Harness API docs](https://apidocs.harness.io/)).
- **Grok and Telegram:** The official [Grok Bot announcement](https://x.ai/news/introducing-grok-bot) describes a separate hosted product announced for desktop and iOS, not a Telegram bot. xAI’s [Grok API](https://docs.x.ai/overview) is a separate hosted inference route. xAI states that API inputs and outputs are retained for 30 days by default and are not used for training by default; a global endpoint may route data across regions, and prices plus request/token limits vary by model and feature, so check the current account console before enabling ([security and retention](https://docs.x.ai/developers/faq/security), [regions](https://docs.x.ai/developers/advanced-api-usage/regions), [pricing](https://docs.x.ai/developers/pricing)). Any custom [Telegram Bot API](https://core.telegram.org/bots/api) bridge would be a distinct, network-connected hosted service with a protected bot token and explicit opt-in/disconnect; continuous polling from the phone conflicts with this app’s offline and no-background-work goals. Forwarding Telegram messages to an AI provider would require clear user consent and review of [Telegram's bot developer terms](https://telegram.org/tos/bot-developers).
- **Alibaba Cloud Model Studio:** This hosted service offers cloud Qwen and third-party-model APIs. Calls require an Alibaba Cloud account and API key, use region-specific endpoints, need network access, and may incur token-based charges. Alibaba states provider data is not used for model training, but the reviewed references do not establish a clear retention period or deletion SLA; confirm the current privacy terms before sending task data. The license for downloadable Qwen weights, if any are later selected, is separate from Model Studio API terms ([service overview](https://www.alibabacloud.com/help/en/model-studio/what-is-model-studio), [first Qwen API call](https://www.alibabacloud.com/help/en/model-studio/first-api-call-to-qwen), [privacy notice](https://www.alibabacloud.com/help/en/model-studio/privacy-notice), [regions](https://www.alibabacloud.com/help/en/model-studio/regions)).

Any future provider adapter must implement one `AssistantProvider` contract exposing capabilities, an offline/remote flag, configuration status, and an inference call. Routing must go only to the provider the user explicitly selected; never silently fall back to cloud. Before a remote call, disclose which task data will leave the phone, the destination/region, network requirement, privacy implications, and possible cost; send only the minimum necessary context. Never log prompts or keys. Encrypt user-supplied keys under Android Keystore when held on-device, or use protected server-side secret storage for hosted services; never place credentials in app source, an APK, or a public repository. Provide clear revocation and disconnect controls. Telegram remains a separate hosted service requiring a backend and protected bot token, with no background phone polling.

## Build and inspect (debug only)

Requirements: JDK 17+, Android SDK Platform 35, Android Build Tools 35.0.0, and Platform Tools for `adb`. The project pins Gradle Wrapper 8.10.2 and Android Gradle Plugin 8.8.2, uses Java 17 source/target, `compileSdk` / `targetSdk` 35, `minSdk` 26, and has no runtime third-party libraries.

**Release blocker:** Google's [current Play target API requirement](https://developer.android.com/google/play/requirements/target-sdk) requires ordinary new apps and updates to target API 36+ starting 2026-08-31. This project remains on API 35 and pinned AGP 8.8.2, which supports at most API 35; only SDK Platform 35 / Build Tools 35.0.0 are installed here. Google's [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16) disable the edge-to-edge opt-out for apps targeting API 36. Upgrade the toolchain/SDK target and test system bars, display cutouts, keyboard/IME, dialogs, and gesture/three-button navigation on API 35 and 36 before any store release. API 36 has not been built or runtime-tested here; the debug APK is not Play-submission-ready.

The first SDK license acceptance/package installation occurred before the chronology correction and remains unapproved. After that correction, the user explicitly consented prospectively to using **only the already-installed** Platform 35, Build Tools 35.0.0, and Platform Tools 37.0.1 for this debug rebuild/verification; this does not retroactively approve the earlier action. No new license or package was accepted/installed. This consent does not cover device installation or publication.

The commands below use the existing SDK only and build a **debug preview**. A debug APK does not qualify for the `<15 MB` target; measure that target only from a genuine release APK.

```sh
export ANDROID_HOME="$HOME/Android/Sdk"  # use your actual SDK path
export ANDROID_SDK_ROOT="$ANDROID_HOME"
cd android-app
./gradlew :app:assembleDebug --no-daemon --console=plain
cp app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk
stat -c '%s bytes' ./app-debug.apk
sha256sum ./app-debug.apk
```

The root copy is ignored by Git and remains unpublished. This workflow intentionally does not include an SDK package installation, `adb install`, or app launch.

## Current feature-branch verification (2026-10-05)

- Current main had stored the Gradle wrapper and both Android shell test scripts without executable mode, so the documented `./...` commands initially failed with `Permission denied`. This branch records them as executable; the documented commands now run directly.
- `./tools/check-v1-source.sh` passes: **30** JDK-only task-logic assertions, manifest/privacy policy checks, shared writer/reader task-list validation, accessibility/localization source assertions, and text contrast. The lowest checked contrast is **5.00:1** across standard/high-contrast light/dark palette pairs. These source checks do not replace rendered UI, translated-flow, or TalkBack testing.
- `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:assembleDebug --offline --no-daemon --console=plain` completed with `BUILD SUCCESSFUL` using only already-installed SDK Platform 35 / Build Tools 35.0.0. No package or license was installed/accepted. Gradle emitted a non-blocking SDK XML v4/v3 compatibility warning and a Java deprecated-API note.
- The generated debug APK is `app/build/outputs/apk/debug/app-debug.apk`, **45,091 bytes**, SHA-256 `e5e8f05d163fa7514085e7b4c06d20de09781c7b590d8f26a45bcebd279b980e`. `aapt` identified `com.cue.daymark` version `1.0.0`, min SDK 26 / target SDK 35; the packaged manifest declares no permissions. `apksigner verify` succeeded with APK Signature Scheme v2. It remains an ignored, unpublished debug artifact and does not qualify for the `<15 MB` release target.
- Root `npm test` passes **8/8** tests. `adb devices -l` reports no device; APK installation/launch were not tested. Airplane-mode runtime, Keystore behavior on a device, TalkBack, rendered font scaling/locale behavior, and API 36 edge-to-edge behavior remain unverified.
- No optional model, language, or plugin packs exist, so their size is **N/A**. No release APK was produced; the base-release size target is unmeasured.

Run source-level checks without Android SDK packages from the repository root:

```sh
cd android-app
./tools/check-v1-source.sh
```

## Initial build attempt before SDK installation (historical)

- Ubuntu OpenJDK 21 headless JDK is installed; `javac -version` reports `21.0.12.1`.
- The checked-in Gradle Wrapper runs successfully and reports Gradle `8.10.2` on Java 21.
- Official Android CLI `1.0.16500706` is installed from Google's Linux download at `~/.local/bin/android`; CLI metrics were disabled for the version check. Its first-run output displayed the SDK agreement URL but did not request or receive any license acceptance. No platform/build-tools package was requested by that command.
- Android SDK Platform 35, Build Tools 35.0.0, Platform Tools/adb, and the legacy SDK Command-Line Tools archive are **not installed**. Google’s SDK license approval is pending; no SDK package download, installation, acceptance, or bypass has occurred.
- `./tools/check-v1-source.sh` passes: **25 native core assertions** and the source policy checks.
- `./gradlew :app:assembleDebug --no-daemon --console=plain` was attempted without SDK environment variables and failed with:

  ```text
  Could not determine the dependencies of task ':app:compileDebugJavaWithJavac'.
  SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local.properties file at '/workspace/team_project/android-app/local.properties'.
  BUILD FAILED in 7s
  ```

- No `app-debug.apk` has been created, copied, installed, or launched. Base APK size is **not measured**. Runtime airplane-mode, TalkBack, Keystore/device behavior, and on-device performance remain unverified.

See [V1_ACCEPTANCE.md](V1_ACCEPTANCE.md) for the manual release checks and explicit items still blocked or deferred. See [ANDROID_SOURCES.md](ANDROID_SOURCES.md) for official references, package provenance, and version facts.


## SDK/build actions performed before the consent correction (2026-10-05; do not treat as authorized)

- Official Android CLI `1.0.16500706` at `~/.local/bin/android` matches its recorded Google-download SHA-256 `54b6e2d382444b91511fcc7ab34ddec6561f257d6d1cdce16bb91af6789b6de2`.
- The CLI created `licenses/android-sdk-license` (41 bytes; SHA-256 `c43fa37686457c3f18caa3607945f4ec52a9d1beaaad8117e50dc4e863270c85`) and installed `platforms/android-35` 2.0.0, `build-tools/35.0.0` 35.0.0, and `platform-tools` 37.0.1. The user later clarified that the preceding “ok to karo” was not legal consent. Treat this acceptance and installation as **unapproved**; this is a factual record, not authorization. No other SDK package/license was accepted, and no further SDK terms or packages may be handled before an explicit **YES** to the exact legal-consent question. The emulator and legacy command-line-tools package are not installed.
- `./tools/check-v1-source.sh` passes with 25 native-core assertions plus permission, background-component, runtime-dependency, and optional-asset checks.
- `./gradlew :app:assembleDebug --no-daemon --console=plain` completes with `BUILD SUCCESSFUL`. The build emitted a non-blocking SDK XML v4/v3 compatibility warning and a Java deprecated-API note. The first resource-link attempt exposed an unavailable framework `Theme.Material.DayNight.NoActionBar`; the default was changed to the supported light theme, with the existing `values-night` dark theme retained.
- The pre-correction debug artifact (now superseded by the fresh build below) was 45,027 bytes, SHA-256 `0edb81e786f60b43cdc0521bc00ded35f695c113040e796b59766f2bf3309b27`. It was package `com.cue.daymark`, version `1.0.0`, min SDK 26/target SDK 35, debug-signed, and had no requested permissions. This historical artifact must not be confused with the current root copy or a release APK.
- `adb devices -l` returned no attached device, and the Android emulator package was not installed. APK installation and app launch could not be verified on-device. At the time of this historical snapshot, no post-correction SDK build had yet occurred; the later, consented rebuild is recorded below.

## Gap between chronology correction and explicit consent (historical)

No SDK package/license acceptance or Android build occurred during this gap; work was limited to source and documentation checks. The fresh debug rebuild below followed the user's explicit prospective YES.

## Fresh rebuild after prospective consent (2026-10-05)

The user explicitly consented to future use of only the already-installed Platform 35, Build Tools 35.0.0, and Platform Tools 37.0.1 for this debug rebuild/verification. That consent does not retroactively authorize the earlier license acceptance or package installation. No additional package or license was accepted/installed. Source checks passed (25 core assertions, source/permission policy, and text contrast minimum 5.00:1); Gradle completed with `BUILD SUCCESSFUL` and emitted only the noted SDK XML compatibility and deprecated-API warnings. The fresh APK is `app-debug.apk` (45,031 bytes; SHA-256 `4688733df7429495ae9b74504bc71b703186d7f366b02611e535e57a59afaa71`), signed with the debug certificate and verified by APK Signature Scheme v2. It has no declared permissions or optional payload. No device install/launch or GitHub publication occurred; the release size target remains unmeasured.

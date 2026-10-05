# Daymark Android V1 acceptance checklist

This is a release checklist, not a claim that every device-level item has already passed. Source-only status is distinguished from runtime verification.

## Attached brief: requirement status map

| Requirement area | Status | Evidence or gate |
|---|---|---|
| Offline task CRUD, dates/priorities, filters, deterministic Demo suggestions | **V1 implemented** | JDK core tests pass; Android UI/device and airplane-mode checks remain unrun. |
| Encrypted local task file and permission status | **V1 implemented** | AES-GCM/Keystore source exists; manifest has no permissions; device behavior is unverified. Not Room/SQLCipher. |
| Room/SQLCipher, schedules, profiles, migrations, export/recovery | **Later opt-in** | Not implemented; requires architecture, key, backup, migration, and recovery decisions plus tests. |
| GGUF/ONNX/MLC model manager, imports/catalogs, CPU runtime or accelerator | **Later opt-in** | No engine, model, importer, catalog, or model UI in V1; require provenance, license, parser, lifecycle, and device qualification. |
| Model/runtime/plugin/language packs and downloaded locale JSON | **Later opt-in** | No packages ship or download. Any future network fetch must be explicit, disclosed, cancellable/resumable, verifiable, removable with caches, and compatible with the offline core boundary. |
| Bounded user-requested local data work | **Later opt-in** | No worker exists in V1; any future WorkManager/foreground operation must follow Android constraints and remain cancellable, without scheduling model downloads, updates, indexing, or inference. |
| PRoot/Linux shell, app CLI, plugins/hooks, cloud and Telegram | **Later opt-in** | Absent; require separate threat models, narrow permissions/capabilities, explicit consent, security/licensing/performance tests. |
| Hardware benchmarks, API/ABI/device matrix, CI/SBOM/signing/reproducibility | **Later opt-in** | Not complete or measured; device-specific accelerators are not assumed. |
| Base release APK `<15 MB` | **Later opt-in** | Unverified release target only; measure exact bytes after a genuine release build, never use debug size, and make no claim before a release APK exists. A debug APK exists but does not qualify. |
| Root/privileged/signature grants, background-limit bypass, permanent daemon, arbitrary downloaded executable code | **Unsupported/platform-limited** | Ordinary APKs cannot self-grant these powers or defeat Android lifecycle/policy restrictions. Use signed code delivery; never promise a resident daemon. |

“Later opt-in” means not present in this V1 and subject to a separately approved phase; it is not a commitment to ship. “V1 implemented” here is a source status, not proof of device-level acceptance.

## Automated checks available in this workspace

- [x] Core task validation, IDs/timestamps, date-only rules, filters/search, and deterministic suggestions compile/run on the installed JDK.
- [x] Manifest has no declared permissions or background component; no runtime dependency or optional model/media asset is included in the source tree.
- [x] Source-derived palette contrast test checks standard and high-contrast light/dark pairs against the 4.5:1 normal-text threshold; the lowest tested ratio is 5.00:1. It is not a rendered-screen or TalkBack test.
- [x] Static accessibility/localization checks confirm that every source `setTextSize` call applies the selected app text scale, expected spoken-label strings/live status regions are present, the UI remains explicitly English-only, and date formatting uses the device locale. These source assertions do not test visual reflow, translated flows, or TalkBack traversal.
- [x] Android Gradle debug build completed with `BUILD SUCCESSFUL` after the user explicitly consented prospectively to using only already-installed SDK components. No SDK package was installed and no new license was accepted in this rebuild; the earlier acceptance/install remains unapproved and is not retroactively authorized. Install/launch remain unverified because `adb devices -l` returned no attached device and no emulator package is installed.
- [x] Fresh root/output debug APK copies are byte-identical: `app-debug.apk`, 45,031 bytes, SHA-256 `4688733df7429495ae9b74504bc71b703186d7f366b02611e535e57a59afaa71`; package `com.cue.daymark`, version `1.0.0`, min SDK 26 / target SDK 35. The built manifest declares no permissions; `apksigner verify` succeeded with APK Signature Scheme v2. It was not installed or published. Debug size does not measure or qualify the `<15 MB` release target.

Run available source checks from `android-app/`:

```sh
./tools/check-v1-source.sh
```

## Permission UX (device/emulator required)

The current manifest declares no permissions and the source has no runtime-permission or special-access flow. **More → Permission status** is present but read-only; it is not a permission center and cannot request, grant, manage, revoke, or open Settings. The source-level absence is covered by the checked source policy; it does not prove runtime behavior. These device checks remain **unrun** because no Android device/emulator is available. The fresh debug APK was built after prospective consent to the existing SDK components, but it was not installed or launched, and it remains a debug-only unpublished artifact. The feature-specific cases below are release gates before any future permission-dependent feature ships; do not mark them passed for a feature that is not implemented.

- [ ] **No initial prompts:** install/launch the current app and enter the offline task flows; confirm no Android permission prompt appears at launch or for existing core tasks. Device/emulator test unrun.
- [ ] **Request only on the chosen action:** for every future permission-dependent feature, confirm its specific permission is introduced only after the user chooses the action requiring it, with an explanation of why and no unrelated/blanket requests. No such feature exists today; unrun/not applicable to current source.
- [ ] **Runtime grant and denial:** for each future runtime permission, test grant and denial. Confirm grant enables only the dependent feature; denial leaves the rest of the app usable and clearly identifies the limited feature without repeated pressure.
- [ ] **Repeated denial / “don't ask again”:** test the target Android version's persistent-denial behavior (including an explicit “don't ask again” choice when offered). Confirm the app does not rely on another prompt appearing, respects the choice, and offers the applicable Android Settings route; re-check access after returning.
- [ ] **Special permission Settings return:** for each future special permission, confirm the app explains the need and offers an opt-out before opening the relevant Settings page; test both grant and return without grant, then verify the app re-checks access and degrades gracefully. Android Settings actions, not an in-app permission dialog, control this access.
- [ ] **Offline core after denial:** deny each future feature-specific permission and verify create/edit/search/filter/complete/delete/undo and local persistence remain available offline. No permission-dependent feature exists today; unrun/not applicable to current source.

## User-flow acceptance (device/emulator required)

1. **Simple capture:** first screen asks “What do you want to get done?” and shows the school-trip example. Add a valid title with the keyboard action and button. Blank input is rejected; title can be edited before save. Quick add says it creates medium priority/no due date and shows the next step.
2. **Detailed capture/edit:** choose Add with a date or priority; verify the typed draft is prefilled, due date can be selected/cleared, and priority is Low/Medium/High. Cancel or system Back leaves the existing list unchanged. Edit a saved task and verify its ID/created time remain stable.
3. **Power path parity:** switch to Power and verify exactly the same tasks and completion states are present. Search, All/Today/Upcoming/Completed, and Demo suggestions work. Add/edit in Power, switch back to Simple, and verify the same saved data remains visible.
4. **Complete/delete/cancel/undo:** complete and uncomplete a task. Delete shows a clear confirmation; Keep task cancels without changes. Confirm Delete, then Undo within seven seconds and verify task, due date, priority, and completion state return. Relaunch and verify the final state persists.
5. **Offline:** enable airplane mode before first launch; create, edit, search/filter, complete, delete/undo, force-stop/relaunch, and verify the local task file. Verify no network permission in the built manifest and no outbound traffic during core flows.
6. **Keyboard and focus:** operate capture, mode switch, search, dialogs, selection controls, and buttons using keyboard/focus navigation; verify visible focus order and Enter/IME actions. Verify Android Back/Cancel discards an unsaved edit and does not create a task.
7. **Text size and contrast:** check system font scaling and app Compact/Standard/Extra large. Check Standard and high-contrast palettes in light, dark, and system-following appearance. Confirm layouts scroll without clipped actions and text remains readable.
8. **TalkBack:** traverse every field/action with TalkBack. Confirm the task-specific completion, edit/delete, mode switch, search-clear, date/priority and storage-status labels are understandable and that dialogs announce their title/actions. Make the app itself accessible; do not ask for `AccessibilityService` as a generic permission/plugin workaround. Any future AccessibilityService needs a concrete assistive use case, separate opt-in/security review and device testing; broad event monitoring can be resource-intensive.
9. **Non-English locale:** test at least one non-English locale and an RTL locale. Dates/numbers currently follow device locale, but app strings remain English only; therefore **full non-English/RTL support is not accepted yet**. Translate every user-facing string and accessibility label, then repeat flows 1–8 before claiming support.
10. **Simple language:** review with nontechnical users; verify the quick-add default, date/priority editor, save status, next step, cancel, confirmation, and Undo are understood without technical guidance.
11. **Permission status (device check unrun):** open More → Permission status and verify it reports no permissions declared, does not show a system prompt, and leaves task flows usable. Confirm the built/merged manifest still has no `<uses-permission>` entries.

## Pack, localization, size, and background-work release gate

- [x] Current source has no pre-downloaded models/GGUF weights, language packs, locale JSON, compiled inference runtime/native model engine, plugin packs, demo task dataset, or model artifacts; no optional-content downloader exists. UI strings are English-only source resources/code, not a downloadable locale pack.
- [ ] After a genuine **release** APK build, inspect its contents and record the exact byte size of the base release artifact with `stat -c '%s bytes' <built-apk>`. The spec's **`<15 MB` is an unverified target**; a debug APK does not satisfy it. Record the exact byte count and artifact variant, and make no size claim before a release artifact exists.
- [ ] If any optional download is implemented, test that it starts only after an explicit user action and discloses source, license, exact size, device compatibility/performance impact, and storage impact before download. Verify cancellation, resume, hash/integrity checking, app-private storage, and complete removal of the asset and all caches. Current behavior is absent; these checks are **unrun/not applicable until a feature exists**.
- [ ] If localization is added, verify there is no automatic locale fetch; the user initiates it, sees its exact size before download, can cancel/resume, and can verify and remove locale data and caches. No locale JSON/language pack is currently shipped or downloadable; no additional locale capability is claimed.
- [ ] Before any model/locale capability or compatibility claim, measure the relevant asset/runtime footprint and performance and test on representative real devices. No model/runtime capability currently exists.
- [x] Source has no app-owned polling, scheduled work, foreground service, inference loop, or optional-content update path.
- [ ] Profile actual device idle/background behavior. Zero CPU load while the UI is open is impossible; state only measured near-zero idle/background results, never a literal zero-load guarantee. Android controls background execution; the app cannot disable platform limits.

## Later-phase qualification gates (not run; not V1 acceptance claims)

- [ ] **Database and recovery:** if Room/SQLCipher is selected, test coherent pinned versions, encrypted open, oldest-to-current migrations, transactions, process death, WAL/journal handling, wrong-key and key-invalidation failure, backup policy, and explicit export/recovery without destructive fallback.
- [ ] **Model import and registry:** test Storage Access Framework/provider sources; size and free-space limits; streaming hash verification; bounded GGUF parsing/fuzz cases; atomic staging/activation; cancellation/restart/reboot; license/provenance metadata; old-model retention; and deletion of asset plus caches.
- [ ] **AICore/ML Kit GenAI (only if evaluated):** verify device/API availability and model readiness; handle `UNAVAILABLE`, `DOWNLOADABLE`, `DOWNLOADING`, and `AVAILABLE`; enforce visible foreground invocation and quota/cancellation behavior; decide whether AICore's system-managed model distribution/updates fit the strict user-started-download policy. Do not enable the capability if this download behavior cannot be reconciled, and never silently fall back to cloud.
- [ ] **Inference and hardware:** test a pinned CPU correctness baseline, cancellation/process death, memory and KV-cache bounds, load/first-token/throughput, thermal/battery on low/mid/high physical devices, supported ABIs/APIs and page sizes; treat ONNX/MLC/GPU/NPU delegates as optional and test fallback on each supported device.
- [ ] **Locales and online sources:** a downloaded locale JSON or model source requires a visible network/region/source/size disclosure and explicit user action. Verify offline operation without it, no silent fallback, removable cached data, and no background downloads.
- [ ] **Background jobs:** if a user-requested local-data import later needs longer execution, test OS-compliant WorkManager or visible foreground work, constraints, cancellation, process death/reboot recovery, and denial/failure paths. Verify the app never schedules model downloads, updates, indexing, or inference; never relies on indefinite residency; and never bypasses Android background limits.
- [ ] **CLI/plugins/providers:** test allowlisted typed inputs, caller/signature checks, permissions, cancellation/quotas/timeouts, denied/unavailable companion behavior, no shell-string execution, no secret/path leakage, provider selection without cloud fallback, remote data/cost disclosure, and Telegram backend/token isolation.
- [ ] **Play target API:** this app currently targets API 35. Google's current Play requirement is API 36+ for ordinary new apps and updates submitted from 2026-08-31; upgrade the SDK/AGP toolchain and target, then run API 36+ compatibility tests before any Play release. The present debug APK is not Play-submission-ready; see `ANDROID_SOURCES.md`.
- [ ] **Release pipeline:** pin and verify dependencies/toolchain, produce SBOM/license inventory, build twice for reproducibility, inspect manifest and APK/AAB contents for hidden developer bypasses, test endpoints, sensitive logging, or executable downloads, align/sign/verify release certificate identity, run connected Android tests and install/update/rollback on the supported matrix. Release qualification remains pending. The initial license acceptance/package installation remains unapproved; the user later prospectively consented only to using the existing packages for a debug rebuild. No additional license/package, device install, or publication was authorized.

## Explicitly deferred (not V1 functionality)

- Voice/push-to-talk: no microphone permission/provider/audio path is present. A future transcript must be shown and editable, then require explicit user confirmation before task creation.
- System permissions: V1 has no permission requirement or prompt. A future runtime permission must be requested only after the user starts the feature, with a clear purpose and graceful denial. Route special access through Android Settings; an ordinary APK cannot grant itself root, privileged/signature-level or arbitrary system permissions, or disable Android background limits. Do not claim root/superuser access.
- PRoot/Linux setup: absent. PRoot is user-space, chroot-like behavior; it is not actual root/chroot privilege or a way around Android's app sandbox or permission boundaries.
- ADB/Termux bridges, Accessibility/overlay access, persistent daemons, raw OS/system integration, arbitrary executable plugins, cross-format/runtime hot-swapping, GGUF/local model inference, CLI, hot reload, and app/OS OTA are deferred and unimplemented. Each requires separate security, compatibility, Android-policy, permission, and real-device tests before any claim; never recommend bypassing platform/security rules.
- No language packs or locale JSON are shipped or downloadable; UI strings are English-only. Localization and RTL layout remain release blockers for any non-English market.
- The Android Gradle debug build is verified, but APK install/launch, Keystore runtime behavior, airplane-mode runtime, TalkBack, device font scaling, and device profiling remain unverified because no device/emulator is available. The source-level contrast calculation passed but is not a rendered-UI test.

### Future AssistantProvider and Telegram policy

Any future local or remote provider must implement one `AssistantProvider` contract exposing capabilities, offline/remote status, configuration status, and an inference call. The user must explicitly select the provider, and routing must never silently fall back to cloud. Before a remote call, disclose what information leaves the phone and possible cost, send minimum context, never log prompts or keys, and encrypt user-supplied credentials with an Android Keystore-protected key. This contract, routing, and remote credential support are not in V1.

Telegram is a separate hosted-bot service that needs a backend and bot token. It conflicts with V1's offline/no-backend/no-background design and remains deferred; do not add a mock connection or background phone polling.

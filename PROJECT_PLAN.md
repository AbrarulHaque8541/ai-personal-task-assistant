# Daymark Project Plan

Daymark should remain a fast, simple, privacy-first task app that works offline, while letting interested users reveal more capability through task-specific workspaces. The ordinary task flow must not require users to learn about AI, models, plugins, terminals, or Android internals. Advanced tools should be optional, visible, modular where the distribution channel permits, and bounded by user-approved scopes.

**Status snapshot.** This document contains a staged roadmap, not proof that a feature is implemented. Verify live `main`, open PRs/issues, `android-app/app/build.gradle.kts`, and the [Releases page](https://github.com/AbrarulHaque8541/daymark/releases) before making implementation or publication claims. CI/build success does not establish physical-device behavior; device acceptance is tracked separately in issue #160.


## Execution queue (checked 2026-10-13)

This is the actionable priority order. A roadmap entry is not an implementation claim; move an item to **Done** only after source review and the stated checks. Recheck live issues and PRs before opening work so agents do not duplicate one another.

### P0 — Stabilize the current browser before adding features

1. **Browser load watchdog recovery — merged (PR #241); device verification pending.** The watchdog is present on current `main`. Preserve its bounded retry/error behavior and HTTPS-only navigation. CI/source evidence is not a device result; use the live status index for current workflow evidence.
2. **Diagnose the owner-reported blank browser viewport.** Track [issue #221](https://github.com/AbrarulHaque8541/daymark/issues/221). After the source fix, verify on a real device whether a normal HTTPS page and a search result visibly render. Capture WebView version, Android API/device, visible lifecycle status, and logcat if available. CI is not device verification; do not close this issue based on CI alone.
3. **Complete the Android runtime acceptance matrix.** Track [issue #160](https://github.com/AbrarulHaque8541/daymark/issues/160). Record PASS / FAIL / NOT TESTED by scenario, device/API, variant, commit SHA, and APK SHA. Cover browser navigation/renderer recovery, Reader Mode, image blocking, extensions, downloads/SAF, encrypted task data/backup restore, and same-signer upgrade. Never label a built APK as device-tested.
4. **Regression/security audit.** Run the repository's prescribed host/source tests and Android CI after each focused change. The owner-approved `com.abrar.daymark` application identity is now on `main` via merged PR [#286](https://github.com/AbrarulHaque8541/daymark/pull/286); keep it, the production signer pin, monotonic `versionCode`, HTTPS-only browsing, encrypted user data, and the no-broad-JavaScript-bridge boundary unchanged without explicit owner approval. The identity reset is a separate install; do not promise automatic local-data migration.
   - *Historical host run, 2026-10-10 (branch `arena/2564f1a3-daymark`, base `477b347`):* `npm test` — **PASS** (12/12); browser-watchdog check — **PASS**; core Java tests — **NOT TESTED** because `javac` was unavailable in that run. The PR #243/#246 overlap note describes the queue at that time only; check the live status index instead of treating those PR states as current.

### P1 — First feature after P0 stability evidence

5. **Selected-text actions in the browser.** Inspect current source first and implement only missing actions. Candidate actions: Copy, Explain, Translate, Summarize, and Create Task. Keep actions in a contextual/overflow menu rather than permanently consuming viewport space. Copy and Create Task should work locally; Explain/Translate/Summarize must clearly open a user-selected provider or disclose the required API/network path. Do not imply that Daymark already has an AI backend. Acceptance: selection survives menu invocation, actions handle empty/long text safely, task creation is explicit, HTTPS/provider disclosure is preserved, and automated/source tests cover the action routing.
6. **Per-site browser preferences.** Only proceed after checking what global controls already exist. Candidate per-origin settings: Reader preference, image loading, and theme/font choice. Keep an explicit reset/clear path; store only the minimum origin-level state, explain that current browser history/preferences may not be encrypted, and do not claim network-level ad/tracker blocking. Acceptance: preference is origin-scoped, survives the documented lifecycle, resets cleanly, and does not affect unrelated sites.
7. **User-facing browser diagnostics mode (still planned).** PR [#291](https://github.com/AbrarulHaque8541/daymark/pull/291) merged privacy-safe WebView visual-state/lifecycle log breadcrumbs, but not a user-triggered diagnostics screen or export. If that UI is proposed, redact query strings/fragments and credentials; export must be opt-in. CI logs are not proof that issue #221 is fixed.

### P2 — Consider after the core browser and task flows are reliable

8. **Command palette / quick actions.** Search existing tasks and invoke existing actions from one compact UI. Reuse current task APIs; do not create a second task store or duplicate commands. Acceptance: keyboard and touch access, accessible labels, deterministic ordering, and existing task regression tests.
9. **Research-to-task workflow.** Let a user explicitly save selected page text/link as a task or note, with a preview before writing. Keep it local-first; external AI analysis is optional and clearly disclosed. Acceptance: user confirms saved content, URL sanitization, bounded text size, no silent background capture, and backup/restore compatibility.
10. **Local model/runtime discovery — official source survey completed (2026-10-12); selection remains open.** Do not implement, bundle, or auto-download a model. The [source review](android-app/ANDROID_SOURCES.md#official-source-refresh-2026-10-12) distinguishes AICore/ML Kit, llama.cpp plus GGUF, and LiteRT-LM; no universal compatibility or current model-size claim is supported. Choose nothing until the target device matrix, exact checkpoint/runtime licenses, RAM/storage/size, performance/thermal/battery behavior, removal, and user-started transfer are verified. Decide separately whether AICore's system-managed model delivery fits explicit-download requirements. Never silently cloud-fallback.

### Delivery rules for every queue item

- One focused change per branch/PR where practical; inspect open work before starting.
- State exact tests run and their result. Distinguish **SOURCE-VERIFIED**, **PASS**, **NOT TESTED**, **DEVICE VERIFIED**, and **PUBLISHED** accurately.
- Add a feature to **Done** only when its acceptance criteria are met; otherwise leave it **In review**, **Blocked**, or **Planned** with the relevant PR/issue link.
- Do not bundle speculative features into a browser bug fix. Do not create a release tag without owner approval or an existing standing release instruction.

## Current app and live repository status

**Verified snapshot (2026-10-13):** live `main` was `43fbe7670ad19ff2809f916f1b7dace4bc5ceb04`. Current Gradle source uses application ID `com.abrar.daymark`, `versionName = 1.0`, `versionCode = 7`, and API 36. The latest published release remains v1.0.5 and targets API 35; the current source line is not published and is a separate install with no automatic task-data migration. This is a dated snapshot, not a live dashboard; follow [the status index](docs/AGENT_STATUS.md) and recheck GitHub before acting.

Current `main` includes encrypted task storage/attachments/portable backup, an HTTPS-only embedded WebView, origin bookmarks, filterable tabs, and offline natural-language quick add. PR [#292](https://github.com/AbrarulHaque8541/daymark/pull/292) added quick-add parsing for supported dates/times, priorities, and `#tag` tokens; the parser smoke test passed 16 assertions. PR #291 adds privacy-safe source diagnostics, not device rendering acceptance. Current-main build check [run 38412156622](https://github.com/AbrarulHaque8541/daymark/actions/runs/38412156622) passed; this does not publish a production artifact or establish physical-device behavior.

The shared manifest declares normal `INTERNET`; the Play flavor inherits it, and `githubSideload` additionally declares `ACCESS_NETWORK_STATE`. `INTERNET` is install-time, not a runtime prompt, and its presence alone does not prove a request occurs. Browser requests remain user-initiated and HTTPS-only; platform-managed WebView Safe Browsing may create separate network activity.

The repository source pins the expected production signer SHA-256 and enables the updater gate for the signed sideload flavor. The current source version is not published until the exact-tag production workflow succeeds and verified assets appear on the [Releases page](https://github.com/AbrarulHaque8541/daymark/releases). The backup format uses a randomly generated recovery key, AES-GCM, and HKDF-SHA-256—not a passphrase.

Issues [#221](https://github.com/AbrarulHaque8541/daymark/issues/221) (blank viewport), [#222](https://github.com/AbrarulHaque8541/daymark/issues/222) (browser UX), [#257](https://github.com/AbrarulHaque8541/daymark/issues/257) (release readiness), and [#160](https://github.com/AbrarulHaque8541/daymark/issues/160) (device matrix) were OPEN at this snapshot. Identity coordination issue [#285](https://github.com/AbrarulHaque8541/daymark/issues/285) is closed and implementation PR #286 is merged. Check the live status index for subsequent changes; do not infer current PR counts from this document.

No physical Android device/emulator was available for this review, so install/launch, API 26/36 runtime, WebView/provider/Keystore behavior, TalkBack, notifications, and process-death behavior remain **NOT TESTED**. See [`DOCUMENTATION_TRUTH_AUDIT.md`](DOCUMENTATION_TRUTH_AUDIT.md) for claim classifications and evidence boundaries.

## Product direction and architecture

Keep the core task experience small and local. Let a task expand into a workspace only when the user chooses. Preserve the current minimal UI with progressive disclosure rather than putting agent controls on the default home screen.

Use a stable, provider-neutral capability interface between the task/workspace layer and optional tools. Each capability should declare its purpose, typed input/output schema, version, permissions, data scope, online/offline needs, risk, resource limits, cancellation behavior, and license/terms. A broker—not model-generated tool code—checks that a request is within the current task’s granted scope. Keep model, search, and tool adapters replaceable; do not hard-wire task storage or UI to one vendor.

The core tasks, local search, filters, and existing data access must work without a network connection. Optional web search, browsing, or a user-selected hosted model
 may need the network and must say so before use. **No feature requires cloud fallback.** A local model that is unavailable, too large, or out of memory should report that limitation; it must not silently send the prompt to a cloud provider.

## Capability map and feasibility matrix

| Capability | Status | Evidence status | Feasible scope and boundary |
|---|---|---|---|
| Core tasks: title, notes, subtasks, due date/time, priority, completion, search/filter/sort, templates, attachments, deterministic suggestions | **Current** | **Source confirmed; device acceptance remains separate** | Keep task edits local and available offline. Do not describe the existing rule-based demo suggestions as AI. Reminder timing/platform delivery remains best-effort and device-dependent; see [`docs/TASK_REMINDERS.md`](docs/TASK_REMINDERS.md). |
| Offline natural-language quick add | **Current** | **Source merged; parser smoke passed 16 assertions; device UX remains untested** | Only the single-line capture path parses supported dates/times, priorities, and `#tag` tokens; unknown words remain title text and the regular editor is unchanged. See [`docs/NL_QUICK_ADD.md`](docs/NL_QUICK_ADD.md). |
| Encrypted local task storage, attachments, portable backup | **Current** | **Source confirmed** in `main` via current source/merged PRs; **untested on device** for this review | Preserve atomic recovery, encrypted app-private data, explicit file selection, migration safety, and user-controlled portable restore. Avoid absolute “zero data loss” promises. |
| Suggestion-card actions/navigation | **Needs current-source verification** | **Older PR #15 reference is historical; no current implementation claim is made here** | Verify the live source and UI tests before treating suggestion-card navigation as implemented. Keep distinct from future additions; the ten-feature slate is not tracked in public `main`. |
| Offline timed reminders | **Implemented in source; device behavior unverified** | **Current-source checks/documentation; no device acceptance** | Reminder prompts and best-effort OS notifications are implemented. Permission, Doze, reboot, time-zone, revocation, and delivery timing remain device-dependent; do not describe timing as guaranteed. |
| Release updater | **Source enabled and signer-pinned; device flow unverified** | **v1.0.5 is the latest published release; current API-36 source is unpublished** | The [release-signing contract](RELEASE_SIGNING.md) and updater source describe the sideload updater and pinned certificate. The user-directed handoff saves a verified APK for manual opening, not app-launched installation. Real provider/process-death and update behavior remain unverified. |
| HTTPS-only browser, Reader Mode, bookmarks, filterable tabs, and network-image preference | **Source present; device/network behavior unverified** | **Source/host/CI checks; no WebView runtime testing** | Browser networking starts Offline and requested navigation needs disclosure acceptance plus a tap. Reader Mode extracts bounded text; image blocking is not network-level ad/tracker filtering. History stores origins only and is not encrypted. Platform Safe Browsing may make separate network requests. |
| WebView visual-state diagnostics | **Source log breadcrumbs merged; user-facing diagnostics mode remains planned** | **PR #291 CI passed; not device render-validated** | The merged privacy-safe log path is distinct from a diagnostic screen/export. Do not claim the blank viewport is fixed until #221 records device evidence. |
| Other task additions (share-in, research capture, widget) | **Future work** | **No ten-feature slate tracked in public `main`** | Natural-language quick add is already implemented; do not duplicate it. Treat other proposals as unaccepted until the owner adds acceptance criteria to the live queue. |
| User-approved file workspace | **Later feasible** | **Not implemented as an agent workspace in main** | Begin with app-private task/workspace files and Android’s user-selected document access, not arbitrary filesystem access. |
| Local model manager and on-device inference | **Research required** | **No implementation observed in main** | Evaluate runtime compatibility, model license, download size, RAM/storage, CPU/GPU/NPU support, thermal/battery cost, startup latency, and deletion before selecting a model. Never auto-download a large model. |
| Hosted model/provider adapters | **Research required** | **No model API connected in current source** | First-party adapter boundary only after provider terms, privacy, retention, region, price/rate limits, and data disclosure are reviewed. Hosted use is explicit and optional; no cloud fallback. |
| Computer vision, OCR, speech, camera/microphone features | **Research required** | **No implementation observed in main** | Prefer user-selected images/files and on-device processing when practical. Request microphone/camera or media access only for a user-invoked feature. Android's general `SpeechRecognizer` implementation is likely to stream audio to remote servers; on-device recognition availability varies by device/service. Keep typing as a first-class alternative and clearly disclose any audio leaving the device before recording or sending it. See the [SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer). |
| Downloadable language/model/data assets | **Later feasible** | **Architecture proposal only** | Treat weights and content as data, not remotely injected application code. Show size, license/source, compatibility, integrity, storage use, and removal/update behavior; require a user choice. |
| Play dynamic feature delivery | **Android-limited** | **Official platform constraint** | Play Feature Delivery separates feature modules inside an Android App Bundle and delivers them through Google Play. It is not a module downloader for a GitHub-sideloaded APK. Do not remotely inject executable modules into the GitHub APK. Google Play’s [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en) also restricts downloading executable code outside Play, subject to its stated interpreter/VM distinctions. |
| General plugin system | **Android-limited** | **Architecture proposal only** | Prefer vetted, in-process capabilities shipped in the app, or Play-delivered modules where the app is actually distributed as an eligible AAB. A “plugin” label does not grant arbitrary code execution or Android API access. |
| General-purpose terminal and coding environment | **Research required** | **No implementation observed in main** | A userspace runtime is not a Linux host, root shell, or kernel-isolated container. Bound filesystem, process, CPU, memory, battery, and network access; do not promise arbitrary package installation or full builds on low-end phones. |
| Single-agent task workspace, permission broker, visible activity, cancellation, rollback | **Later feasible** | **Architecture proposal only** | Start with one task-scoped agent and a small stable tool contract. Require visible actions, least-privilege scopes, cancellable foreground work, preview/snapshot for mutations, and user approval for sensitive or external effects. |
| Memory, diagnostics, and device adaptation | **Later feasible** | **Architecture proposal only** | Keep task/session/workspace memory separate and user-resettable. Bound resource use; collect only needed device capability signals; redact logs and make diagnostic export opt-in. |
| Session recording and multi-agent coordination | **Deferred** | **No implementation observed in main** | Reconsider only after single-agent isolation, deletion, consent, audit redaction, and resource limits have been validated. Do not record screens by default. |
| Arbitrary Android app cloning/virtualized app containers | **Deferred** | **No implementation observed in main** | Do not promise unrestricted app cloning. If useful, research a narrower web-app or task-workspace alternative under Android and Play constraints. |

## Task evolution and near-term scope

Extend the task model through small, versioned, reversible migrations, preserving existing IDs, encrypted storage, backups, and undo behavior. Offline natural-language quick add is now implemented in the single-line capture path; see [`docs/NL_QUICK_ADD.md`](docs/NL_QUICK_ADD.md). The older ten-feature slate remains untracked in public `main`; share-in, research capture, and widgets are proposals, not in-flight work. Keep the simple add/edit/complete flow prominent, and make richer fields discoverable only when the user expands a task.

For every task-data migration, test old snapshot reading, interrupted writes, backup export/import, missing or invalid fields, undo/delete interactions, and recovery after process death. Avoid silently rewriting an 
existing task when a template changes, a recurrence advances, or a task is restored.

## Local-first data, security, and permissions

Keep task content in app-controlled storage and encrypted at rest. Android Keystore key material is designed to remain non-exportable; treat it as device-scoped rather than as a portable backup key. For device migration, use a separate authenticated encrypted export/recovery flow that can be decrypted with user-held recovery material and re-encrypted under the destination device’s key. Test missing-key, damaged-archive, interrupted-import, and duplicate-import cases. See Android’s [Keystore guidance](https://developer.android.com/privacy-and-security/keystore), [app-specific storage guidance](https://developer.android.com/training/data-storage/app-specific), and [backup security recommendations](https://developer.android.com/privacy-and-security/risks/backup-best-practices).

Use Android’s narrow system pickers/intents for user-selected files and calendar handoff. Keep app-private workspace files separate from task records and external documents. A permission broker should record the capability, purpose, requesting task/tool, granted scope, lifetime, revocation path, and data exposed. Explain permission requests in context and ask only when the user invokes a feature that needs them.

Do not seek root, hidden APIs, privilege escalation, permission bypasses, or an AccessibilityService as a general agent-control channel. Google Play’s [AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en) prohibits autonomous initiation, planning, and execution through the Accessibility API outside its dedicated accessibility-tool exception. Use accessible controls in Daymark itself and explicit Android APIs/intents for allowed handoffs. Background work must respect Android process, Doze, and battery limits; no permanent invisible agent loop or broad battery-optimization exemption is part of the design.

## Reminder-specific Android constraints

Historical reminder implementation references (PR #17/#19 in the old PR #15 stack) are not current live-queue evidence. Recheck the current source and live PR/issue state before claiming reminder implementation. Any reminder design must disclose platform limits:

- Android 13 (API 33) and later requires the `POST_NOTIFICATIONS` runtime permission for ordinary notifications. Ask in context when the user creates a reminder; if permission is denied or later revoked, state that no notification can be shown. See [notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission).
- Prefer inexact alarms when exact timing is not essential. Exact scheduling is special access through `SCHEDULE_EXACT_ALARM` on relevant versions; request it only when the user explicitly chooses a precise reminder and explain that access can be denied/revoked. Do not assume `USE_EXACT_ALARM` is available to a general task app. See [exact-alarm access](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms) and [alarm scheduling](https://developer.android.com/develop/background-work/services/alarms).
- Alarms do not survive shutdown by default. Persist reminder intent and, only while reminders exist, reconstruct eligible alarms after boot and on wall-clock/time-zone changes. Android documents `BOOT_COMPLETED`, `TIME_SET`, and `TIMEZONE_CHANGED` cases in its [broadcast guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions).
- Calendar-style reminders need a defined local-time and timezone policy. Daylight-saving transitions can make a local time nonexistent (a gap) or ambiguous (an overlap with two offsets); choose and test a clear policy rather than silently guessing. Relative timers should use elapsed-time semantics where appropriate. See the [Java `ZoneRules` reference](https://docs.oracle.com/javase/8/docs/api/java/time/zone/ZoneRules.html).
- Doze defers ordinary alarms and background activity; even allow-while-idle alarm calls are constrained. Inexact notification timing can be late, so never promise exact delivery on every device. See [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).

The PR #15/#17/#19 stack described in the original planning snapshot is historical, not the current open PR queue. Recheck the current issue tracker before planning reminder work. No reminder device/emulator acceptance is established by this plan.

## Browser, models, providers, and terms

The merged WebView source should remain inside Daymark’s own controlled browser. Permit HTTPS only; do not add a JavaScript-to-native bridge for untrusted content, form automation, background page loads, or control of the user’s other browser. Browser networking now starts Offline. A first Go/search/site request displays the disclosure; only after acceptance does that user-requested navigation continue. Enabling Online alone does not issue a request; later navigation still requires a tap. Clearly distinguish “Daymark page/resource loads are blocked” from “the device makes no network requests.” Chromium’s [Android WebView Safe Browsing implementation note](https://chromium.googlesource.com/chromium/src.git/+/refs/heads/main/android_webview/browser/safe_browsing/README.md) documents version-dependent checks: before WebView M126, V4 uses a periodically updated local blocklist and, on a local prefix match, requests matching full hashes; from M126, V5 real-time checks send a partial URL hash through a proxy, when that path is available and enabled. The full-URL real-time lookup mechanism is not currently supported in Android WebView. These checks are navigation-related; do not assume a URL blocked by Daymark’s Offline-mode policy necessarily triggers one. The live-check path depends on WebView version, device implementation, and user/system settings; it has not been measured for Daymark, so do not promise zero network traffic. WebView usage statistics and crash reports are separate: Android says diagnostics depend on user settings/consent and do not include URLs. See [WebView management](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview) and [WebView reporting privacy](https://developer.android.com/develop/ui/views/layout/webapps/webview-privacy). Keep Safe Browsing on by default unless a deliberate, user-visible choice and security review justify otherwise.

Browser history contains at most 50 HTTPS origins in app-private preferences; it is not encrypted and does not retain page paths, queries, fragments, credentials, or titles. Clearing browser data clears Daymark’s history and WebView-managed site data, not other apps’ browser/autofill data or remote site/provider logs. A query or requested URL and ordinary connection metadata go to its selected destination; pages may contact their own or third-party endpoints.

A future Model Manager should show model identity, license, source, capabilities, context limits, download size, approximate storage/RAM demand, and removal/update controls before download. A provider adapter must show whether a request leaves the device, what content is sent, relevant provider terms/retention, and cost or rate limits before the user opts in. Never imply local and hosted models have identical privacy or costs; never fall back to a hosted provider without a separate user choice. Review third-party SDK terms, code, and license compatibility before adopting them.

## Agent workspace, approvals, audit, and recovery

Keep each workspace scoped to a task and expose a structured capability list rather than arbitrary device control. Every tool should have a typed contract, permission declaration, risk tier, resource/time limits, cancellation behavior, and bounded output. A model proposes tool calls; application code validates parameters and permission scope before execution.

Use proportionate approvals: local read/search can normally proceed within the task scope; creating or editing workspace files should be previewable and recoverable; deleting data, exporting/sharing it, accessing sensitive device resources, installing software, or causing an external/irreversible effect requires explicit approval at the point of action. Keep a visible activity timeline with the current action and target. Redact secrets and task text from diagnostic logs by default. Provide pause/cancel for long work and undo/snapshot/rollback for changes the app controls. Clearly state that rollback cannot undo an external effect already completed.

Use task, workspace, and session memory as separate stores with user-visible inspection, deletion, and reset. Recording should be opt-in, narrowly scoped, and redact sensitive content; defer it until retention and access controls are designed. Multi-agent coordination should wait until per-task capability grants and shared-workspace boundaries are tested.

## Release and verification gates

Separate these evidence levels in every release note and handoff:

1. Source inspection or source-contract tests.
2. Host/JDK tests.
3. Compile-only checks, with the exact API level and toolchain stated.
4. Gradle build/APK assembly and artifact/signature verification.
5. Emulator tests.
6. Real-device testing across relevant Android versions and at least representative vendor power-management behavior.

A passing source script is not a Gradle build; an API 35 build is not an API 36 build; neither proves device behavior. The repository does not track APK/AAB release binaries. Main Android CI runs automated checks and builds debug variants; the exact-tag production workflow builds, signs, and verifies the release APK/AAB before publication. Releases and assets are listed on the Releases page. No emulator or real device was tested during this audit.

Google Play's published guidance requires ordinary mobile new submissions and updates to target Android 16 / API 36 or higher from 2026-08-31, subject to exceptions. This is a **conditional Play-submission requirement**, not a blanket requirement for local review or GitHub sideloading. See [Google Play's target API requirement](https://developer.android.com/google/play/requirements/target-sdk). Current `main` uses API 36 with AGP 8.12.2 / Gradle 8.13; the toolchain and release-workflow SDK gates are merged. Neither a successful build nor targeting API 36 proves runtime/device behavior. Keep Android 16 device acceptance and exact signed-artifact verification before any Play submission.

Before any production release, verify signing/release provenance, backup restore and upgrade migration, permissions and revocation, file-provider recovery, offline behavior, low-storage/resource limits, accessibility, and device behavior. Keep debug artifacts clearly labeled and do not call a build production-ready without the relevant signing, release, and device evidence.

## Staged roadmap

1. **Protect the current core.** Reconcile README claims with current source and release artifacts; preserve task data, encryption, undo, backup, and the minimal UI. Recheck live issue/PR status before each implementation change.
2. **Verify merged network-facing features.** The updater and WebView from PRs #12/#13 are in `main`; confirm the fail-closed updater gate, signer configuration, HTTPS/network controls, Safe Browsing disclosures, history handling, and device behavior before enabling or promoting them. Issue #14 is closed, but real provider/process-death recovery still needs validation. Separately recheck reminder implementation and current issues against live `main`; do not infer current status from the historical PR #15/#19 stack. Keep suggestion navigation distinct from reminder work and require device evidence before describing reminders as reliable.
3. **Reconcile remaining capture/research proposals.** NL quick add is implemented on `main` via PR #292; do not duplicate it. Share-in, research-to-task, and widget ideas remain proposals until the owner adds them to the live queue with acceptance criteria. Keep each migration reversible and offline-first.
4. 
**Design the agent foundation.** Define the task-scoped workspace, capability schemas, permission broker, local audit trail, cancellation, and rollback before connecting a model.
5. **Research optional runtimes.** Evaluate model weights, browser, OCR/speech, user-space terminal, coding tools, and any plugin mechanism against device budgets, policy, license, and maintenance cost before committing to a delivery design.
6. **Defer high-risk breadth.** Do not start arbitrary app containers, unrestricted multi-agent control, persistent background autonomy, or screen recording until the narrower single-agent workspace is proven safe and useful.

## Deferred capabilities

Defer arbitrary Android app cloning, unrestricted cross-app automation, root-like control, remote executable plugin/module injection, silent model downloads, cloud fallback, background autonomous agents, default screen recording, and broad multi-agent access. These are not necessary to make the local task core useful and carry platform, privacy, resource, or maintenance costs that are not justified by the current evidence.

## AI handoff rules

A future contributor or AI agent should read this plan and the [README](https://github.com/AbrarulHaque8541/daymark/blob/main/README.md), then recheck the live `main` SHA, relevant open issues/PRs, current source, and release artifacts before proposing work. Verify every assumption against the checked-out implementation; do not duplicate merged or in-flight work. Never expose secrets, private chat/audit history, personal emails, or unrelated local workspace details in public documentation.

Preserve user data and the minimal core; use small, reversible, modular changes with tests. Make a recoverable snapshot before risky mutations. Do not bypass Android security or claim an API/device test that was not performed on that API/device. Report source checks, host tests, API-level compile/build results, APK/release status, and device tests as separate 
evidence. Keep user approvals, permission scopes, provider/network choices, and cancellation explicit. Recheck exact branch and PR heads because public status may have changed since this draft was written.

---

## Plan expansion (master architecture addendum)

> Added 2026-10-08 (UTC) after re-audit against the master product vision. This part extends the plan above without replacing any of it. Sections map the vision's required topic list that the base plan did not yet cover in detail.

### Modular download-on-demand system

The base APK stays minimal; every capability beyond the core is an installable **module**. Nothing is bundled "just in case."

**Module manifest (signed index entry):**

```json
{
  "id": "lang.ur",
  "version": "1.0.0",
  "coreCompat": ">=2.0 <3",
  "type": "data",
  "sizeBytes": 4400000,
  "minRamMb": 0,
  "sha256": "…",
  "signature": "ed25519(daymark-release-key)",
  "license": "string-resources license",
  "deps": [],
  "uninstallable": true,
  "offlineCapable": true
}
```

**Lifecycle:** discover → verify signature + hash → compatibility check (RAM/storage/ABI/SDK) → explicit user consent (size shown) → resumable download → re-verify → install → update → rollback to previous version → uninstall. The previous version is kept until the new one verifies; removal never breaks the core app.

**Phase order:** data-only modules first (language packs such as Urdu/Hindi, model weights, prompt packs). Code-bearing modules (DexClassLoader) are RESEARCH REQUIRED and security-gated; no promise is made until a signed, sandboxed design is reviewed.

### Model Manager

A registry of open-source, redistributable model candidates with metadata: size, quantization, minimum RAM, context length, capabilities, license. The manager profiles the device (ActivityManager memory class, ABI, free storage, SDK level) and classifies it low/mid/high end, then **recommends** matching quantized models (for example 0.5B–1B q4 for low-end). It never downloads without explicit consent showing size and RAM cost. Lifecycle: download → verify → smoke-test prompt → activate/switch → update → remove. Low-end devices cap concurrent models at one.

### Standard tool interface and capability discovery

Every agent-facing tool exposes a predictable schema: `name, description, input_schema, output_schema, permissions, risk_level, timeout, cancellation, error_codes`. A small model learns WHAT a tool does and HOW to call it — never Android internals. `agent.capabilities()` returns a structured availability report (browser available, terminal installed, python not-installed, microphone denied) so quantized models can reason reliably. The Java implementation may change; this interface is the stability contract.

### Search provider abstraction

No hard-coded provider. A `SearchProvider` interface (query in, ranked structured results out) with user-selectable implementations. Feasibility: API-key providers are READY; scraping-based providers are ToS/fragility-limited and RESEARCH. Offline searches honestly report "network required."

### Risk levels and confirmation policy

- LOW (no prompt): read, search, analyze inside the workspace.
- MEDIUM (configurable): create/edit workspace files, download modules, modify workspace state.
- HIGH (always confirm): delete, install software, external messages, data sharing, sensitive resources, irreversible operations.

Users can configure policies per level; the Permission Broker enforces and audits every decision.

### Memory layers

Scoped and user-clearable: task memory, workspace memory, session memory, user-approved preferences, project memory, tool memory, model context. Agents read/write only within granted scope; each layer has explicit deletion controls. No ambient cross-scope access.

### Update channels

Separate channels for CORE APP, MODULES, MODELS, PLUGINS, TOOLS — each with version, integrity verification, compatibility check, rollback strategy, and update history. New capability never grows the base APK.

### Explicitly not feasible / deferred (unchanged)

- Arbitrary Android app cloning/containers: NOT FEASIBLE under modern Android security; PWAs and sandboxed web workspaces are the alternative.
- Full Linux terminal on low-end devices: DEFERRED pending a restricted-shell research spike.
- Python bundling (~30–60 MB): ANDROID-LIMITED, download-on-demand only if the module system proves code-module safety.

---

## Suggested features — proposal only (not accepted, not implemented)

> Added 2026-10-10 (UTC). These are **suggestions**, not queue items and not implementation claims. Each one needs an owner decision and an entry in the execution queue (with acceptance criteria) before any work starts. "Verified absent" means a grep of the current branch found no matching code; it is not a device or product-usage finding.

Assumed user profile: a developer who uses Daymark for personal task tracking alongside GitHub work, values speed and privacy, and wants capture without leaving the current app. Adjust this table once the owner states the real use case.

| # | Suggestion | Why it helps this profile | Current code (checked 2026-10-10) | Fit with plan |
|---|---|---|---|---|
| S1 | **Inbound share target**: "Share to Daymark" creates a task from shared text or URL | Capture a GitHub issue link or a snippet from any app in one step | **Verified absent**: no `ACTION_SEND` / share `intent-filter` in `AndroidManifest.xml`; only outbound browser-URL share exists | Fits the "quick capture" direction. Keep it local; prefill the title only, never auto-save |
| S2 | **Task link field** for an issue/PR/doc URL, HTTPS-only, with one-tap open in the in-app browser | Ties a task to its GitHub item without copy-paste | Not verified; existing `notes` field can hold a URL but has no link semantics | Must reuse HTTPS-only policy in `BrowserAddress`; migration needs old-snapshot and backup tests (plan §Task evolution) |
| S3 | **Home-screen widget** showing today's open tasks, with a quick-add button | Fastest daily check without opening the app | **Verified absent**: no `appwidget` declarations in the manifest | Needs a new receiver and must read the encrypted store safely. Consider after the P0 device matrix (#160), because a widget adds a new process-death/locked-device path |
| S4 | **Plain-text / Markdown export** (opt-in, user-chosen file) for personal notes or a git repo | Developers often want tasks in a diffable format | Not verified. Current portable backup is encrypted, which is correct for backups but not for reading | Must clearly warn that plaintext leaves encryption. Opt-in only; no automatic writes |
| S5 | **Keyboard and hardware-keyboard shortcuts** (new task, search, complete, next/previous task) | Power users on tablets or Bluetooth keyboards | Partially: keyboard-related code exists in Android sources, but a shortcut map was not verified | Folds into P2 #8 (command palette). Do not build a second command system |
| S6 | **Focus timer on a task** (local, no network, no notifications by default) | Time-boxing dev work | Not verified | Low risk. Must respect Doze and battery limits; any notification uses the existing reminder rules |
| S7 | **Diagnostics export for bug reports** (WebView version, last load error code, redacted URL) | Makes issue #221-style reports actionable | User-facing screen/export remains planned; privacy-safe lifecycle logs are in source | Already a proposal; logs do not provide an owner-visible export or prove the viewport renders |

**Already present in code, so not suggested as new work:** recurrence (`TaskLogic`, `Task`), subtasks (`Subtask.java`), notes, due time with reminder lead time (`Task.java`), task templates, encrypted backup and import, attachments with a CSV/Markdown/PDF MIME allow-list, Reader Mode, and dark theme. The plan's older text saying time-of-day and notes are absent is out of date for those fields. Update the capability table when the owner confirms them on a device.

**Recommended order if the owner approves:** S1 (small, offline, clear value) → S2 (needs a migration test) → S4 (privacy-sensitive, so design first) → S5 (after P2 #8 is designed) → S3 and S6 (after device evidence for #160). S7 remains a proposal for a user-facing diagnostics/export flow; privacy-safe lifecycle logs are already in source but do not fulfill that UI proposal.

**Decision needed from the owner:** (a) which of S1–S7 to queue; (b) the real day-to-day use case, so the profile above can be corrected.


---
Current-source refresh:
Work by: Cue
Model: not disclosed by runtime
Tooling: GitHub connector CLI, git, local source/workflow/documentation review
Timestamp (UTC): 2026-10-13T14:42:14Z

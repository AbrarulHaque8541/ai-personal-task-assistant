# Daymark — current status and agent handoff (2026-10-13)

**Verified at 2026-10-13T13:42:02Z.** This is a dated, source-backed snapshot, not a permanent truth source. Before acting, re-check live `main`, Releases, issues, PR heads, and checks. Dated audit/research reports remain historical snapshots.

## Current release vs approved future identity

| State | Identity/version | Evidence |
|---|---|---|
| **Current `main` and shipped line** | the current identity in [the Gradle config](../android-app/app/build.gradle.kts), versionName `1.0.5`, versionCode `6` | Live `main` SHA `efbc1cce87dc118ffdf959a14ce75199a8f860c4`; latest published [v1.0.5 release](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.5), production APK targets API 35 |
| **Owner-approved future target, not on main or published** | `com.abrar.daymark`, versionName `1.0`, versionCode `7`; same production signer; separate fresh install; no automatic local-data migration | Open owner issue [#285](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/285) |

Current source main targets compile/target SDK 36 with AGP 8.12.2 / Gradle 8.13. Do not equate that source state or a passing main CI run with a published API-36 artifact or device acceptance.

## In-flight migration

- Original [PR #283](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/283) was **closed unmerged** at 2026-10-13T13:26:27Z; it is superseded.
- Replacement [PR #286](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/286) is open and based on current main `efbc1cce87dc118ffdf959a14ce75199a8f860c4`; observed head is `0acc47a6fae6aa65556c27eaef0ab4ce43e4ae7a`. GitHub reports `MERGEABLE` / `UNSTABLE` because both required workflows fail.
- [Android CI run 38405373598](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38405373598) and [device-test host/source run 38405373584](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38405373584) fail at the retired-identity cleanliness guard. It reports `PROJECT_PLAN.md` plus `android-app/tools/patches/ime-keyboard-insets-mainactivity.patch`, `patches/contextual-more-about.patch`, and `patches/ux-declutter-composer-20261010.patch`. The device-test release-asset publish job was skipped; no new release asset/tag is established.
- The proposed docs-only correction to `PROJECT_PLAN.md` in PR #288 removes its stale identity policy and source-path prose without adding retired-package literals. It does not change #286's observed head or the three patch files; #286 remains **failing** until its branch is updated and both required workflows rerun successfully.
- Current `AGENTS.md` still treats the existing application identity as permanent; PR #286 proposes replacing that rule with the owner-approved target. The rule is not changed in current main until merged.
- PR #286 copy still needs correction: the proposed README instructs installing an APK from the unpublished v1.0 release; `RELEASE_SIGNING.md` combines fresh-install/no-migration with “Users update over the previous production install — data preserved”; and `UpdaterPublisherConfig.java` calls the new target “legacy” even though it is the future identity, not the previous app. A detailed, identity-attributed comment is on [PR #286](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/286#issuecomment-6146729937).
- A separate owner-authored documentation PR [#287](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/287) covers getting started, offline consent, task tags/reminders/backup truth, and the 2026-10-10/11 UX snapshots. It is based on the same `efbc…` main and has Android CI [run 38405550333](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38405550333) and device-test source [run 38405550395](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38405550395) green; its release-asset publish job was skipped and it does not claim real-device acceptance. Its `docs/TASK_BROWSER_PAGE_GAPS.md` changes are left to #287 to avoid duplicate edits.

## Issues and verified vs. NOT TESTED

| Area | Verified / current | Still NOT TESTED or open |
|---|---|---|
| API 36 | Target/toolchain changes are on main; main Android CI [run 38399676414](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38399676414) passed | Android 16 runtime/edge-to-edge, production artifact and release qualification: [#257](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/257), [#160](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/160) |
| Offline browser | [#278](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/278) closed after source default became Offline with explicit opt-in | Clean-install no-network device test remains unrun; keep blank viewport [#221](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/221) and device matrix [#160](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/160) open |
| Browser UX | Browser settings separation, local HTTPS-origin bookmarks, filterable tabs (#284), and source changes for #222 exist on main | No device reproduction/visual/TalkBack acceptance for [#221](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/221), [#222](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/222), [#160](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/160) |
| Natural-language quick add | Feature work is in a separate branch | [PR #267](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/267) remains open; this feature is **not on current main** even though #285’s preserved-feature list says otherwise |
| Browser diagnostics | [PR #268](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/268) remains open | Source diagnostics do not close #221 or prove device rendering |
| Performance | Main source has a task render gate, 150 ms search debounce, WebView lifecycle/cache settings, and hardware layer/offscreen pre-raster configuration | No FPS, memory, thermal, blank-page recovery, or other physical-device benchmark is recorded |

## Immediate gates

1. Fix the four exact identity-cleanliness findings in #286 (or narrowly document why any historical patch fixture must remain) and rerun both workflows on the final head. Keep app-ID/build assertions strong.
2. Reconcile #286 README/signing/updater wording and the #285 Natural-language Quick Add status; inspect the entire migrated feature set and exact current-main base.
3. Audit both distribution flavors, production/device-test workflows, updater metadata, `v1.0` tag validation, monotonic version code, and unchanged signer pin. Require green exact-head checks and inspect signed artifact application ID/version/signer before any release tag. **No production tag/release is part of this handoff.**
4. Complete actual-device acceptance under #160/#221/#222, including WebView paint/recovery, Offline/no-network behavior, visual/TalkBack, and Android 16 behavior. Keep device-only outcomes **NOT TESTED** until evidence is recorded.

## Engineering principle

Prefer currently maintained, stable Android/platform technologies. Weigh privacy, security, compatibility (including supported API levels), and lifecycle/support quality; upgrade APIs or dependencies only for a concrete benefit, not novelty alone. This durable rule is also in `AGENTS.md`.

---
Work by: Cue
Model: not disclosed by runtime
Tooling: GitHub connector CLI; live source, PR, workflow-log, and documentation review
Timestamp (UTC): 2026-10-13T13:44:06Z

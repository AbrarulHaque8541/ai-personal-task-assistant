# Daymark v1.0.5 — Cross-Agent Engineering Handoff

Last reconciled: 2026-10-09. This is a review index, not a claim that all release checks are complete.

## Mission for the receiving agent

Review current `main`, this document, `AGENTS.md`, `PROJECT_PLAN.md`, and the pre-release audit before making changes. Preserve working features and the production signing identity. Treat merged PRs as the source of truth; do not copy code from closed, unmerged draft PRs. Create focused PRs with regression coverage for verified gaps. The owner controls the production tag/release step.

## Current verified source/release snapshot

- Repository: `AbrarulHaque8541/ai-personal-task-assistant`
- App/package: Daymark / `com.cue.daymark`
- Last observed main SHA: `88f3fd395babe273085fc48c3607c30c3f3e4e53`
- Candidate version: `1.0.5`, `versionCode 6`
- Latest known public release: `v1.0.4`; do not call v1.0.5 released until the exact tag's production workflow completes and the release assets are verified.
- Main Android CI run #353 passed at the previous source snapshot: https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/37948750662. The newest main commit observed is documentation-only PR #189; the PR-triggered workflow lookup did not return a run for that main commit.
- Successful CI means automated/build validation, not a physical-device install/test.
- Package ID and pinned production signer are release invariants. Never ask the user to uninstall as a normal update path.

## Merged PR inventory — inspect these diffs

| PR | Merged work | Review notes |
|---|---|---|
| [#103](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/103) | Production signing/release gate work | Preserve same-signer verification and monotonic versioning. |
| [#177](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/177) | Browser control layout, tab/history behavior, HTTPS navigation policy, fullscreen video and user-triggered direct HTTPS media download | Direct media only when the page exposes a direct HTTPS URL; not a universal video downloader. |
| [#180](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/180) | Online-by-default browser policy with explicit user-initiated navigation, migration of legacy hidden Offline preference, browser docs | Verify existing-install migration and error recovery on device. |
| [#181](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/181) | Updater release-note metadata contract for future production releases; extension import trust-review confirmation; grouped More/settings UI and short transitions; opt-in network-image blocking; truthful browser labels | Image blocking is not full ad/tracker blocking. v1.0.4's empty release body is historical; test the new release's metadata contract. |
| [#182](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/182) | Local text-only Reader Mode with bounded user-triggered extraction, selectable text and Copy; separate full-screen live-page action | Heuristic extraction; dynamic/non-article pages may be poor. |
| [#183](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/183) | Release and agent baseline docs refresh | Documentation only. |
| [#184](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/184) | Reader Mode font size (14–28sp), Sans/Serif and Light/Sepia/Dark themes | Device contrast, TalkBack, small-screen and scaling checks remain. |
| [#185](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/185) | Final pre-release agent/project/audit docs refresh | Documentation only. |

**Do not treat PRs [#178](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/178) and [#179](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/179) as merged source:** both are closed and not merged. Use the merged #177 and current main instead.

## Release-blocking acceptance checklist

Record each as PASS / FAIL / NOT TESTED with device model, Android API, build variant, commit SHA and APK SHA where applicable.

- [ ] Install current candidate cleanly on a supported device.
- [ ] Install the production-signed update over the previous production app without uninstalling; verify same signer and preserve encrypted tasks/attachments.
- [ ] Task create/edit/complete/delete/undo; notes, due times, subtasks; malformed/old backup migration.
- [ ] Encrypted backup/export/import/restore, recovery-key behavior and attachments via real document providers.
- [ ] Browser Back/Forward, redirects, target=_blank, switching tabs, closing tabs, renderer/process recovery and keyboard/insets.
- [ ] DownloadManager and SAF: success, cancel, redirect, permission/provider failure and no-space paths.
- [ ] Extension import trust prompt, invalid/oversized imports, enable/disable, match/exclude scope, and no privileged native bridge.
- [ ] Reader Mode: extraction quality, long article scroll, copy, font controls, all themes, large system font, TalkBack and close preserving browser history.
- [ ] Network-image blocking across current/new WebViews and reload behavior.
- [ ] Minimum API 26 plus at least one current Android API.
- [ ] Review Google Play API 36 target, edge-to-edge and predictive-Back requirements before saying Play-ready. The latest audit reported target SDK 35.
- [ ] Verify release workflow creates the `daymark-updater-v1` metadata block and pinned signer validation succeeds.

## Known capability boundaries

- Reader Mode is a bounded heuristic text extractor, not a Gecko/Firefox-grade article parser.
- Network-image blocking is a data-saving control, not a complete ad/tracker blocker.
- The current WebView does not provide a full Chrome/Firefox WebExtension runtime. The optional GeckoView engine is a separate proposal; measure APK/RAM/battery and isolate permissions before considering it.
- AI provider shortcuts are websites, not native authenticated API integrations. Provider prefill behavior varies.
- Video download supports only direct HTTPS media exposed to the page. Do not bypass DRM, blob URLs, segmented HLS/DASH, authentication, paywalls or site controls.
- Recurrence and actual Android reminder delivery need their own design, lifecycle handling, migration tests and device verification; a stored reminder field is not proof of delivered notification.

## Additional findings from the deeper audit

- [#190](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/190) — bound decompressed bytes across all extension archive entries.
- [#191](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/191) — reject invalid numeric HTTPS ports consistently.
- [#192](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/192) — disclose/resolve the mismatch between imported extension `runAt` and page-finished injection.
- [#193](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/193) — collision-safe WebExtension IDs and pack replacement semantics.
- [#194](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/194) — reject malformed WebExtension match scopes instead of broadening them to all HTTPS sites.
- [#196](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/196) — make source guards fail CI when critical MainActivity wiring is missing, rather than printing a non-failing PENDING status.
- [#199](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/199) — apply Safe Browsing preference consistently to all open WebView tabs.
- [#200](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/200) — clear per-tab history and SSL exception state across all open tabs.

## Related issues and proposals

- [#186 — Cross-agent handoff, merged work and release blockers](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/186)
- [#187 — API 36 / Play target readiness](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/187)
- [#160](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/160), [#137](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/137), [#35](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/35) — device/runtime acceptance history; reconcile state and avoid duplicate test matrices.
- [#147](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/147) — recurrence/reminder lifecycle.
- [#150](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/150) — optional GeckoView/WebExtension engine.
- [#161](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/161) — imported extension execution/trust boundaries.
- [#133](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/133) and [#135](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/135) — AI provider and browser UX backlog. Compare each acceptance item with current main before reopening or implementing.

## Rules for new work

1. Verify a problem in current main before filing a bug or coding a fix.
2. Search existing issues/PRs first. Reopen a relevant issue when it is the same unresolved scope; do not create duplicates.
3. Split independent fixes into small PRs; include tests and user-facing documentation where appropriate.
4. Keep security/privacy, encrypted data, backup compatibility, HTTPS-only navigation, signer continuity and package identity as hard invariants.
5. Keep device-only verification clearly marked NOT TESTED unless it actually ran on a physical device or emulator.
6. Never create/push a production tag or claim a release was published unless the owner-authorized workflow has completed and assets are verified.

---
Work by: ChatGPT
Model: GPT-6
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-09T16:07Z

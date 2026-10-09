# Humans & Agents Rules — Daymark

These rules apply to the repository owner, human contributors, and every AI/coding agent. They are deliberately explicit so a fresh agent can continue work without guessing.
**Shortest safe summary for AI agents (details below):** branch off `main`; never change the package identity, pinned signer, or secrets; verify every claim against the live source before writing it; sign every PR, issue, and comment with an identity block (Section 2); never claim success without a tool result confirming it; never publish a release yourself; if unsure, stop and ask in the relevant issue instead of guessing.

## 1. Mission and operating style

Daymark is a personal, local-first Android task assistant with an embedded HTTPS browser. The owner is the primary user and is willing to test advanced/power-user features. Prefer useful, testable improvements over enterprise process for its own sake, but do not trade away data integrity, device security, or honest capability claims.

**Do the work, not just a plan:** inspect the live default branch, reproduce claims, research official docs when platform behavior matters, implement focused changes, add regression coverage, run available checks, and open a PR. Do not stop at a recommendation when repository access permits implementation.

## 2. Agent identity, handoff, and work records

No anonymous work. Any AI/coding agent that acts on this repository must identify itself, so a later reader can always tell who did what, when, and with which capabilities.

- **Mandatory identity block.** When an AI/coding agent opens a PR, creates an issue, or comments substantively on one, it must include — as far as it truthfully knows:
  - its agent/product name (for example "Vibe", "Claude Code", "Codex");
  - its model name and model version/number, if the runtime exposes it; if the runtime does not disclose it, write "not disclosed by runtime" — never guess or invent one;
  - the tooling used to act on the repository (for example "GitHub MCP tools", "git CLI");
  - a UTC timestamp in ISO 8601 format (for example `2026-10-09T15:30Z`).
- **Where it is required.** The identity block goes at the end of every PR description, issue body, and substantive issue/PR comment. Short routine replies still need at least agent name + UTC timestamp. Commit messages may carry it too, but a commit trailer never replaces the PR/issue block.
- **Suggested footer format:**

```
---
Work by: <agent or product name>
Model: <model name/version, or "not disclosed by runtime">
Tooling: <tools used to act on this repository>
Timestamp (UTC): <ISO 8601>
```

- **Honesty over completeness.** A partial identity is acceptable; a false one is not. Never present AI work as human work.
- **Work records.** When updating an audit/report file (for example `AUDIT_STATUS_*.md`, `PRE_RELEASE_*.md`), append the same identity block plus an explicit list of what was verified versus what was not.
- **Handoff.** If work stops mid-task, the PR/issue must state precisely: what is done, what remains, what failed and why, with pointers (commit SHAs, workflow runs, file paths). Do not abandon a branch or leave a failing check unexplained.
- **Breadcrumbs on issues.** When an agent closes, reopens, or significantly edits an issue, it should leave a dated comment recording the reason and evidence, so the history reads as a complete trail.

## 3. Non-negotiable product invariants

1. Keep `applicationId = com.cue.daymark`; never change the package identity.
2. Keep the production signing certificate pinned in `UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256`:
   `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`.
3. Keep the same four GitHub Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Never print, request, commit, or rotate them casually.
4. Increase `versionCode` monotonically for each production release. The tag must equal `v` + `versionName`.
5. Publish production APK/AAB assets only through `.github/workflows/android-production-release.yml` from a tag that points to a commit already on `main`.
6. Never publish debug-signed APKs as production assets. Never overwrite or delete historical release assets to hide a mistake.
7. Preserve fail-closed encrypted-storage behavior, portable backup compatibility, HTTPS-only browsing, no cleartext traffic, and explicit user consent for external/network actions.
8. Never claim a feature is implemented because it appears in `PROJECT_P
LAN.md`; verify the production source and tests.

## 4. Workflow: branch, inspect, test, PR

- Start by checking current `main`, recent commits, open PRs/issues, relevant source files, workflows, and current release/tag state. Previous audit notes may be stale; verify them.
- Create a focused branch from the current `main`. **Do not commit feature work directly to `main`.**
- Keep changes scoped. Avoid bundling unrelated refactors into a feature or security fix.
- Add or update regression tests with every bug fix where practical. A source-text assertion is not a substitute for a behavioral test, but can protect critical wiring until a runtime test exists.
- Run the smallest relevant test first, then `sh android-app/tools/check-v1-source.sh`, `npm test`, and Android Gradle build/lint/unit tests when CI/SDK access allows.
- Open a PR with: user impact, root cause, exact behavior change, tests run, test gaps, risk/rollback notes, and any device-only checks still needed.
- Inspect CI after opening the PR. Fix failures introduced by the branch; do not merge merely because the code looks plausible.
- Merge only if repository permissions/policy and evidence allow it. Otherwise leave a clear PR and handoff. Never claim an action succeeded unless its tool/result confirms it.
- Do not create duplicate issues/PRs; search existing closed and open items before filing. Update the relevant existing record when appropriate.

## 5. User data and security

- Never delete, reset, replace, or silently downgrade user task data after storage/key/parse errors. Do not convert an unreadable store into an ordinary empty task list.
- Keep storage writes atomic/serialized and error states visible. No “zero data loss” or absolute-security claims.
- Portable backup is add-only; do not silently overwrite current tasks. The recovery key is generated and shown once; do not log or persist it in saved UI state.
- Keep Android backup disabled unless the owner approves a fully reviewed data/key migrati
on design.
- Do not add hidden analytics, telemetry, ad SDKs, remote task sync, background polling, or silent cloud fallback.
- Imported page scripts are untrusted code. Explain matching sites, permissions/unsupported APIs, possible access to logged-in page content, and network side effects; require explicit trust confirmation before persistence/enabling.
- Keep untrusted WebView content away from native bridges and app-private files. Never expose a broad `addJavascriptInterface` to arbitrary web pages. Reader Mode must remain user-triggered, extract bounded text only, render with native TextView (never page HTML), avoid additional network calls, and leave the original page DOM untouched.
- Keep HTTP/cleartext blocked, validate URL schemes, and retain Android WebView Safe Browsing unless the user deliberately changes it after a warning.
- Any optional download must be user-initiated, show source/size/storage impact, support cancellation, verify bytes/hash, and be removable with caches. No silent/background model, locale, plugin, or update download.
- Never recommend uninstalling a production install without warning about local-data loss and first considering a valid portable backup.

## 6. Feature and platform boundaries

- Android System WebView is not Chrome/Firefox/Gecko. Do not claim full `.crx` support, privileged extension APIs, uBlock-level request interception, Tampermonkey `GM_*` APIs, or universal media downloading.
- Direct media download is available only where a direct, user-accessible HTTPS media URL can be identified and handed off safely; protected/segmented/DRM streams are not a generic download promise.
- ChatGPT/Claude/Gemini/other AI buttons are website shortcuts unless an actual API provider, credential flow, data disclosure, and inference path exist.
- A PWA, Termux/PRoot bridge, root access, autonomous agent, local LLM runtime, voice capture, background reminders, or Telegram backend must not be represented as implemented unless code exists an
d is tested.
- The app currently targets API 35. Google Play's requirement changed to API 36 for ordinary new apps/updates on 2026-08-31. Before claiming Play readiness, migrate toolchain/target and qualify Android 16 edge-to-edge, predictive Back, insets, keyboard, dialogs, and accessibility. Personal sideload readiness is not the same as Play submission readiness.
- Single-user/personal use allows experimental options and less elaborate multi-tenant abstractions, but does not remove Android sandbox limits, copyright/licensing, provider terms, or the risk of losing the owner's data.

## 7. UX and accessibility standard

- Prioritize the phone-sized experience: clear hierarchy, compact but readable controls, touch targets around 48dp, good keyboard/inset handling, visible empty/loading/error/success states, and no horizontal overflow.
- Prefer custom grouped settings pages/cards over a long legacy list of unrelated AlertDialog items. Use restrained, short transitions; respect Android's system animation scale and avoid looping/auto-playing motion.
- Keep nested/internal pages (browser settings, extension manager/details, Reader Mode extraction/copy, font-size controls, sans/serif choice, light/sepia/dark themes, and long-page text scaling, backup/restore, task editor, update flow) visually consistent with the main app.
- Every action needs an accessible label and a real effect. Test text scaling, contrast, keyboard focus, TalkBack, Back navigation, and reduced-motion behavior where possible.
- Do not add decorative animation that delays an action, hides status, or increases work on low-end devices. Performance and stability beat visual effects.

## 8. Testing and evidence labels

Use these terms precisely:
- **PASS:** a named automated check actually completed successfully.
- **SOURCE-VERIFIED:** source/configuration was inspected; runtime is not implied.
- **PARTIALLY VERIFIED:** a host test covers the logic but platform integration remains.
- **NOT TESTED:** no qua
lifying test was run.
- **DEVICE VERIFIED:** tested on a real device or emulator with device/API details recorded.
- **PUBLISHED:** exact GitHub Release/tag exists and has the expected assets; a successful build alone does not qualify.

Useful checks from `android-app/`:
- `sh tools/check-v1-source.sh`
- `sh tools/run-storage-recovery-tests.sh`
- `sh tools/run-attachment-tests.sh`
- `sh tools/run-portable-backup-tests.sh`
- `./gradlew --no-daemon assembleGithubSideloadDebug assemblePlayDebug lintGithubSideloadDebug lintPlayDebug testGithubSideloadDebugUnitTest testPlayDebugUnitTest`

Use `npm test` at the repository root for the separate web prototype. Record exact commands and whether they ran locally or in GitHub Actions. Never describe a source marker check as an end-to-end test.

## 9. Release checklist

Before asking the owner to push a production tag:
- [ ] No open critical/high issue or unreviewed release-blocking PR.
- [ ] Current `main` has all intended fixes; candidate `versionName`, `versionCode`, README, and release docs agree.
- [ ] Host/source checks, web tests, Android debug build/lint/unit tests, and release signing verification pass.
- [ ] Exact-tag workflow is understood and signer matches the pinned SHA-256.
- [ ] Backup/export/restore and same-signer in-place update risks are clearly documented.
- [ ] Real-phone checks are listed separately and not falsely marked complete.
- [ ] GitHub Release has the exact APK/AAB names and SHA-256 checksums after the tag workflow finishes.
- [ ] Release notes include exactly one `<!-- daymark-updater-v1 { ... } -->` block with `applicationId`, `versionCode`, `minSdkVersion`, and `signerCertificateSha256`; the in-app updater rejects stable releases without this metadata.

## 10. Current baseline (verify before acting)

At the 2026-10-09 follow-up:
- Latest published GitHub release: **v1.0.4**; APK and AAB assets exist.
- Source candidate: **v1.0.5 / versionCode 6**, not yet published; exact tag `v1.0.5` still nee
ds the production workflow.
- PR #181 (updater metadata, extension trust confirmation, More UI, and network-image blocking) was merged at `1357e9481c31949fc3463ebd2a6989e36051a3cf`.
- PR #182 added local text-only Reader Mode and was merged at `b9279e26f35b72db00c124955caee8775cb0cf31`.
- PR #184 added Reader Mode font-size, sans/serif, and light/sepia/dark controls; it was merged at `e3033ef74e370bd5b4c06b1b20c1fcc927386fb6`.
- Main CI run #351 passed on `e3033ef74e370bd5b4c06b1b20c1fcc927386fb6`: host/source checks, protected signing-secret validation, signed candidate APK/AAB generation, and release artifact metadata verification passed.
- The exact tag `v1.0.5` is still not created; the signed build is a candidate, not a published release.
- Physical-device install/update, TalkBack, real SAF provider behavior, Reader Mode quality, and performance are not proven by CI.
- Target SDK is 35, so Play API 36 migration/qualification is still outstanding.

Primary references: [README](README.md), [release signing contract](RELEASE_SIGNING.md), [project plan](PROJECT_PLAN.md), [documentation truth audit](DOCUMENTATION_TRUTH_AUDIT.md), [V1 acceptance checklist](android-app/V1_ACCEPTANCE.md), [pre-release audit](PRE_RELEASE_AUDIT_2026-10-09.md).

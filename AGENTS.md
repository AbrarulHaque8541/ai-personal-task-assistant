# Humans & Agents Rules — Daymark

Read before code, secrets questions, or release actions. Keep this file short so any new agent can start fast.

## 0. Start here (60 seconds)

1. **App:** Daymark — local-first Android tasks + HTTPS WebView. Package: `com.cue.daymark`.
2. **Repo:** https://github.com/AbrarulHaque8541/ai-personal-task-assistant
3. **Live status:** [Releases](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/latest) · `android-app/app/build.gradle.kts` · open Issues/PRs — never invent versions from memory.
4. **Secrets already in Actions:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. **Never ask the owner for keystore files or passwords.** Never print them. Never put write tokens in the APK.
5. **Signer pin:** `UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256` = `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`
6. **Ship path:** branch → PR → green CI → merge to `main` → tag `v` + `versionName` → `.github/workflows/android-production-release.yml` publishes signed APK/AAB + `daymark-updater-v1` notes.

## 1. Do the work

Prefer a focused, testable change over a long plan. Verify claims against **live** `main` and CI. Search open Issues/PRs before opening duplicates.

## 2. Agent identity (mandatory)

Every PR description, issue body, and substantive issue/PR comment ends with:

```
---
Work by: <agent or product name>
Model: <name/version, or "not disclosed by runtime">
Tooling: <e.g. GitHub MCP tools>
Timestamp (UTC): <YYYY-MM-DDTHH:MM:SSZ>
```

- Do not invent a model number. Partial identity beats a false one.
- Identity is for **traceability**, not status. Do not claim GitHub “collaborator”, co-owner, or permanent team membership unless the owner granted that on GitHub.
- Short “ack” replies: at least Work by + UTC timestamp.

## 3. Multi-agent coordination (lightweight)

Many agents may work in parallel. Stay useful, not bureaucratic:

1. **Before coding:** skim open PRs and recent commits for the same area (browser, tasks, More menu, signing, docs).
2. **One concern per branch/PR.** Prefer extending or reviewing an open PR over opening a near-duplicate.
3. **Hot files** (`MainActivity.java`, signing/workflows): smaller diffs; avoid placeholder or wipe commits.
4. **Handoff in the PR/issue body**, not only in chat: what changed, what was verified, what was not.
5. **Success = tool result.** A PR/issue exists only when GitHub returns a number/URL. An in-chat form that was not submitted is not a PR.
6. If two agents overlap, prefer merge/rebase on current `main` and keep the better fix — do not fight over credit.

## 4. Product invariants

1. `applicationId = com.cue.daymark` forever.
2. Same production signer pin (above); monotonic `versionCode`; tag = `v` + `versionName`.
3. Production APK/AAB only via the production release workflow from a tag on `main`. No debug-signed “production” assets.
4. Fail-closed encrypted storage; do not empty user tasks on error. Portable backup stays add-only compatible.
5. HTTPS-only browsing; no broad `addJavascriptInterface` to untrusted pages; no analytics/ad SDKs.
6. WebView ≠ full Chrome/Firefox extensions. AI site buttons are shortcuts unless a real API path exists.

## 5. Release (owner or agent)

- **Who may tag/release:** the **owner**, or an **agent after the owner’s explicit approval** for that release (chat/issue/PR is enough).
- Without that approval, prepare `main` and docs only — do not push `vX.Y.Z`.
- After a tag: confirm Release assets exist; use evidence label **PUBLISHED** only then.
- Device QA is separate from CI. Never mark DEVICE VERIFIED without a real device/emulator run.

## 6. Evidence labels

Use precisely: **PASS** · **SOURCE-VERIFIED** · **PARTIALLY VERIFIED** · **NOT TESTED** · **DEVICE VERIFIED** · **PUBLISHED**.

## 7. UX bar

Phone-first, honest labels, ~48dp targets, nested screens consistent. Prefer useful defaults over enterprise ceremony.

## 8. Status sources (do not freeze versions here)

| Question | Source |
|----------|--------|
| Published APK | GitHub Releases / latest |
| Tree version | `android-app/app/build.gradle.kts` |
| Open work | Issues + PRs |
| Signing detail | `RELEASE_SIGNING.md` |

Also: [README](README.md), [PROJECT_PLAN.md](PROJECT_PLAN.md).

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-09T19:27:00Z

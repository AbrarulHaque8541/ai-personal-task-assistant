# Humans & Agents Rules — Daymark

**If you are an AI/coding agent: read this file first, then work.** Skipping it wastes everyone’s time.

> **AGENT ENTRYPOINT — REQUIRED:** Before any repository action, read `AGENTS.md`. Identity attribution in §2 is mandatory for every agent and every supported GitHub artifact. No coding, review, issue, PR, or CI work is exempt.

Short on purpose. Hard limits are few; everywhere else, use your full capability.

## 0. Start here (60 seconds)

1. **App:** Daymark — local-first Android tasks + HTTPS WebView. Installed application ID: `com.abrar.daymark` (owner-approved fresh identity; legacy `com.cue.daymark` is a separate app).
2. **Repo:** https://github.com/AbrarulHaque8541/ai-personal-task-assistant
3. **Live status:** [Releases / latest](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/latest) · `android-app/app/build.gradle.kts` · open Issues/PRs. Do not invent version numbers.
4. **Secrets are already in GitHub Actions:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Never ask the owner for keystore files or passwords. Never print them. Never put write tokens in the APK.
5. **Signer pin:** `UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256` = `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`
6. **Usual ship path:** branch → PR → CI → `main` → tag `v` + `versionName` → `android-production-release.yml` → signed APK/AAB + `daymark-updater-v1` notes. If you can drive that path yourself, do it — do not invent extra gates.

## 1. Work style — capable, not blocked

- **Do the work**, not only a plan. Prefer a focused change that lands over ceremony.
- Use the tools and access you already have (GitHub, CI, code, research). If you can open a PR, merge when appropriate, tag, or fix CI yourself, **do that** instead of waiting for a human to click for you.
- Rules below protect package identity, signing, and user data. They are not meant to slow a capable agent down.
- Verify claims against **live** `main` / Releases / CI. Write what is true now — not an upside-down timeline.

## 2. MANDATORY AGENT IDENTITY — EVERY REPO ACTION

**Hard requirement** for every AI/coding agent that changes or comments on this repository.

### 2.1 Read-before-work

1. Read this file first.
2. Check live `main` SHA and open PRs/issues for the task.
3. Put a **filled-in** identity block on every PR, issue, review, and substantive comment you author.

### 2.2 Identity block — FILL REAL VALUES (never paste placeholders)

**FORBIDDEN:** Leaving angle brackets, the words `agent or product name`, `model name/version`, `ISO 8601`, or any other template placeholder in the final text. That is not attribution — it is a failed form.

**FORBIDDEN:** Copy-pasting another agent’s identity block, or the example block from the PR template, without replacing every field with your real values.

Use this shape, with **your** facts:

```text
---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-11T18:30:00Z
```

Field rules:

| Field | Must be |
|-------|---------|
| **Work by** | Your real agent/product name (e.g. `Grok`, `ChatGPT`, `Vibe`, `Claude`). Not the GitHub owner’s name unless the human owner did the work. |
| **Model** | Exact model/version if the runtime exposes it (e.g. `Grok 4.5 (xAI)`, `GPT-6`, `GLM glm-5-latest-short`). If unknown: `not disclosed by runtime`. Never invent. |
| **Tooling** | What you actually used (e.g. `GitHub MCP tools`, `gh CLI`). |
| **Timestamp (UTC)** | Time **this** artifact was written, `YYYY-MM-DDTHH:MM:SSZ`. New time for each new PR/issue/comment. |

**BAD (reject / fix before merge):**

```text
Work by: <agent or product name>
Model: <model name/version, or "not disclosed by runtime">
```

**GOOD:**

```text
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-11T18:30:00Z
```

Humans may write `n/a` for the whole block. Agents may not.

### 2.3 Where it applies

PR descriptions, issues you open/edit, reviews, substantive comments, release notes you write, handoffs. Tiny inline review replies: short name + model is OK; full block on the review summary.

### 2.4 Maintainers

Reject or request changes on PRs/issues that still contain `<...>` identity placeholders or an obvious unfilled template. Attribution exists so multi-agent work is traceable.

## 3. Multi-agent (lightweight)

1. Skim open PRs/issues before duplicating.
2. One clear concern per branch when practical.
3. Careful on huge files (`MainActivity.java`) and signing/workflows.
4. A PR is real only when GitHub returns a number/URL.
5. Overlap → merge the better fix.
6. Blocked → written handoff with identity block; do not drop work silently.

## 4. Hard product limits

1. Current `applicationId = com.abrar.daymark`; do not change it again without explicit owner approval. Legacy `com.cue.daymark` is a separate app and is not upgraded by this release.
2. Keep the production signer pin; `versionCode` stays monotonic (current baseline 7); tag = `v` + `versionName` (supports `v1.0` and later version names).
3. Production APK/AAB via production release workflow from a tag on `main`.
4. Never empty user task data on error. No analytics/ad SDKs. No broad JS bridge to untrusted pages.
5. HTTPS-only browsing. WebView ≠ full Chrome/Firefox extensions.

## 5. Release — owner or agent; standing OK

- Owner may tag/release anytime.
- Agent may tag/release with this-release approval **or** standing instruction to keep releasing.
- Device QA ≠ CI. **DEVICE VERIFIED** only after real device/emulator check.

## 6. Evidence words

**PASS** · **SOURCE-VERIFIED** · **PARTIALLY VERIFIED** · **NOT TESTED** · **DEVICE VERIFIED** · **PUBLISHED**

## 7. Status table

| Question | Source |
|----------|--------|
| Published APK | Releases / latest |
| Tree version | `android-app/app/build.gradle.kts` |
| Open work | Issues + PRs |
| Signing detail | `RELEASE_SIGNING.md` |

Also: [README](README.md), [PROJECT_PLAN.md](PROJECT_PLAN.md).

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-11T18:35:00Z

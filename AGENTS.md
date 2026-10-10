# Humans & Agents Rules — Daymark

**If you are an AI/coding agent: read this file first, then work.** Skipping it wastes everyone’s time.

> **AGENT ENTRYPOINT — REQUIRED:** Before any repository action, read `AGENTS.md`. Identity attribution in §2 is mandatory for every agent and every supported GitHub artifact. No coding, review, issue, PR, or CI work is exempt.

Short on purpose. Hard limits are few; everywhere else, use your full capability.

## 0. Start here (60 seconds)

1. **App:** Daymark — local-first Android tasks + HTTPS WebView. Package: `com.cue.daymark`.
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

**This is a hard requirement for every AI agent, coding agent, bot, and automated assistant that changes or comments on this repository. Read and follow it before doing any work.** It applies regardless of vendor, model, IDE, GitHub App, MCP server, or whether the agent believes the change is small. No agent gets an exemption.

### 2.1 Read-before-work protocol

1. Read this root `AGENTS.md` before inspecting, editing, committing, reviewing, or commenting.
2. Check the live `main` SHA and current open PRs/issues relevant to the task. Do not trust stale chat summaries or old plan snapshots over GitHub.
3. Identify yourself truthfully in the first substantive GitHub artifact you create, and repeat the identity block in every applicable artifact listed below.
4. If you cannot comply because the tool/UI does not support a field or comment, disclose that limitation in the nearest supported description/comment and in your handoff. Do not silently omit attribution.

### 2.2 Required identity block

Append this block to **every PR description, issue body created or materially edited, review submission, substantive issue/PR/review comment, handoff, and other GitHub text artifact where a description/comment is supported**. For commits, use accurate commit author/committer metadata when the tooling permits; also include a concise trailer in the commit message when supported. For workflow/CI updates, attribute comments or summaries you author.

```text
---
Work by: <actual agent/product identity>
Model: <exact model/version if exposed; otherwise "not disclosed by runtime">
Tooling: <actual tool/interface used, e.g. GitHub MCP tools, gh CLI, IDE agent>
Timestamp (UTC): <actual ISO-8601 UTC timestamp, e.g. 2026-10-10T16:00:00Z>
```

Rules for the fields:

- **Work by:** use the actual agent/product name, not the repository owner’s name unless the owner personally performed that action. If the platform exposes only an integration/app identity, state that identity and, if known, the agent that initiated the work.
- **Model:** report the exact model/version only when the runtime actually exposes it. Otherwise write `not disclosed by runtime`. Never guess, embellish, or copy another agent's model/version.
- **Tooling:** list the tools actually used for that action. Do not claim tools, tests, device access, permissions, or collaborators that were not used/available.
- **Timestamp:** use the actual time the artifact is created/updated in UTC, formatted `YYYY-MM-DDTHH:MM:SSZ`. Do not reuse a timestamp from an earlier artifact.
- **Personal/contact information:** do **not** invent, infer, or expose a human’s home/address, phone number, email, account IDs, credentials, or other private details. These are not agent identity fields. Include contact details only when the account owner explicitly provided them for public publication and the task requires them. A truthful agent/product name, model disclosure, tools, and timestamp are the required provenance.
- **No impersonation:** never present an AI agent as the repository owner or claim a human personally performed agent work. Do not claim GitHub collaborator/co-owner status unless GitHub grants it.
- **No false attribution:** do not append another agent’s identity block to your work. Preserve prior authorship and credit; attribute only the changes/actions you actually made.

### 2.3 What counts as an action

Use the identity block in all supported places, including:

- Creating/updating PRs and PR descriptions; opening/updating issues; substantive issue or PR comments.
- Review comments, review summaries, approval/request-changes explanations, and cross-agent handoff notes.
- Commits and release notes/changelogs where attribution text is supported; commits must not falsely use a human owner as author.
- CI/workflow failure explanations, test reports, and follow-up status comments authored by an agent.
- Documentation or project-plan changes when made through a PR, issue, or comment.

For inline code review comments with a tiny text limit, use a compact truthful attribution in the comment when possible and place the full block in the review summary. Do not spam repeated comments just to add identity; attach the block to the same artifact.

### 2.4 Accountability and handoff

Every handoff must say: current goal; what is done and not done; exact branch/PR/issue and relevant SHAs; checks run with PASS/FAIL/NOT TESTED; blockers and evidence; next concrete steps; and the handoff author’s identity block. Never state that CI proves device behavior. Never say merged, released, or published until live GitHub confirms it.

If blocked by a tool, permission, quota, missing capability, conflict, or flaky check, record the exact blocker and a ready-to-run handoff on the open cross-agent handoff issue (currently #186, verify it is still open) or create a new issue if needed. Do not leave half-finished work without a traceable handoff.

**Compliance is expected for every agent, not only ChatGPT.** These rules are repository workflow requirements; they cannot technically force an external agent that ignores repository instructions, so owners/maintainers should reject unattributed or falsely attributed work and request correction before merge.

## 3. Multi-agent (lightweight)

Many agents may work at once:

1. Skim open PRs/issues for the same area before duplicating.
2. One clear concern per branch when practical.
3. Be careful on huge files (`MainActivity.java`) and signing/workflows — no wipe/placeholder commits.
4. A PR/issue is real only when GitHub returns a number/URL (an unsubmitted chat form is not a PR).
5. Overlap → merge the better fix; do not fight over credit.
6. **Blocked → write a collaborator handoff prompt; do not stall silently.** If any problem blocks your work (tool failure, API rate limit, missing capability, flaky check), immediately post a handoff prompt as a comment on the open cross-agent handoff issue (currently #186) — or a new issue if none is open — addressed to the next collaborating agent. The prompt must state: (a) the in-progress task and why it matters; (b) the exact blocker (error text, tool, quota, missing function); (c) the SHAs, PRs, and issues involved; (d) the exact remaining steps, checks, or commands; (e) your identity block per §2. Either finish the work or hand it off in writing — never drop it quietly.

## 4. Hard product limits (short list)

1. `applicationId = com.cue.daymark` forever.
2. Same production signer pin; monotonic `versionCode`; tag = `v` + `versionName`.
3. Production APK/AAB via the production release workflow from a tag on `main` — not a random debug APK labeled “release”.
4. Never empty user task data on error. No analytics/ad SDKs. No broad JS bridge to untrusted pages.
5. HTTPS-only browsing. WebView ≠ full Chrome/Firefox extensions.

Everything else: ship useful improvements.

## 5. Release — owner or agent; standing OK

- **Owner** may tag/release anytime.
- **Agent** may tag/release when:
  - the owner approved **this** release, **or**
  - the owner already gave a **standing instruction** (e.g. “release without asking each time”, “keep working and releasing”, “don’t wait for approval on release”).
- If standing approval exists, **do not** ping the owner again for each tag. Execute, then report what shipped.
- If there is no approval and no standing instruction, prepare `main` and open/describe the release path — do not guess.
- After a tag: confirm Release assets; say **PUBLISHED** only when the GitHub Release exists.
- Device QA ≠ CI. Say **DEVICE VERIFIED** only after a real device/emulator check.

## 6. Evidence words

**PASS** · **SOURCE-VERIFIED** · **PARTIALLY VERIFIED** · **NOT TESTED** · **DEVICE VERIFIED** · **PUBLISHED** — use them accurately.

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
Timestamp (UTC): 2026-10-09T19:35:00Z

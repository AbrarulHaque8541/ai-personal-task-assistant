# Humans & Agents Rules — Daymark

**If you are an AI/coding agent: read this file first, then work.** Skipping it wastes everyone’s time.

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

## 2. Agent identity (mandatory, light)

End PR descriptions, issue bodies, and substantive comments with:

```
---
Work by: <agent or product name>
Model: <name/version, or "not disclosed by runtime">
Tooling: <e.g. GitHub MCP tools>
Timestamp (UTC): <YYYY-MM-DDTHH:MM:SSZ>
```

Traceability only. Do not invent model numbers. Do not claim GitHub “collaborator” / co-owner status unless the owner granted it on GitHub.

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

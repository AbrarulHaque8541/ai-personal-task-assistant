# Daymark — Owner feature suggestions (hardcore-developer use case)

Date: 2026-10-10 · Status: **historical proposal snapshot; recheck current `main` before treating any item as pending.**

**Status update (2026-10-13):** D1.1 is partly superseded: task tags are implemented in the editor, task summary, and search; dedicated tag-filter chips remain only a proposal. D1.3 is implemented and no longer pending: current `main` uses DMM3 to preserve tags, repeat rules, and the monthly anchor in portable backup/restore; see [`TASK_REMINDERS.md`](TASK_REMINDERS.md). The documentation-truth note below describes an earlier `PROJECT_PLAN.md` snapshot, which has since been corrected. Other entries remain proposals unless verified against live source.

## Scope and method

- Inventory taken from live `main` @ `7df2acac` (after PR #242): README, `docs/TASK_ADD_GAPS.md`, `docs/TASK_REMINDERS.md`, `docs/FUTURE_MILESTONES.md`, `docs/PERFORMANCE_ROADMAP.md`, `PROJECT_PLAN.md`, and open issues (#221, #222, #160).
- Only items **not already covered** by the `PROJECT_PLAN.md` execution queue (selected-text actions, per-site preferences, browser diagnostics, command palette, research-to-task, model research), `docs/FUTURE_MILESTONES.md` (engine grid / re-search row), `docs/PERFORMANCE_ROADMAP.md` (P01–P12), or open issues are listed.
- Every item respects the standing policy: local-first, no telemetry, no accounts, HTTPS-only browsing, minimal permissions, no cloud fallback.

## D1 — Developer quick wins (small, focused PRs)

| ID | Feature | Why it matters for a hardcore developer | Effort | Data impact |
|---|---|---|---|---|
| D1.1 | **Dedicated tag filter chips** (task tags, editor, visible labels, search, and backup already exist in current `main`) | Dev work splits by repo/context (daymark, work, learning); search alone does not facet | M | Filter UI only; no new task/backup fields expected |
| D1.2 | **Natural-language time in quick add** ("9:30pm", "at 3pm tomorrow", "in 2h") — gap already flagged in `docs/TASK_ADD_GAPS.md` | Capture speed is the point of quick add; retyping the time in the editor breaks flow | S | None (parser only, host-tested) |
| D1.3 | **Repeat rules in portable backup — implemented; not pending**. Current DMM3 stores/restores the repeat rule and monthly anchor; DMM2 remains a historical format without those fields. | Backup/restore is the safety net; silently losing repeat state is data loss | Done in source; device acceptance remains separate | DMM3 |
| D1.4 | **Code-friendly notes rendering**: monospace for `inline code` and fenced blocks, clickable links in the note dialog | Dev notes are snippets, commands, and URLs; rendering them as plain prose hurts readability | S–M | None (render only; storage unchanged) |

## D2 — Power-user features

| ID | Feature | Why it matters for a hardcore developer | Effort | Data impact |
|---|---|---|---|---|
| D2.1 | **Hardware keyboard shortcuts** (Ctrl+F find-in-page, Ctrl+T / Ctrl+W tabs, Ctrl+K focus quick add, Esc close) | Devs use tablets/DeX with keyboards; touch-only chrome is friction | S–M | None |
| D2.2 | **Browser origin-history encryption + tab session restore** (audit first: today ≤50 unencrypted origins live in plain app-private preferences; verify what tab state survives a restart, then persist/encrypt what is missing) | Session restore is table stakes for a browsing developer; unencrypted history contradicts the encrypted-everything story | M | Encrypt the history blob; keep the ≤50-origin cap |
| D2.3 | **Scheduled auto-backup to a user-chosen SAF folder** (daily/weekly, keep last N; manual backup/restore stays) | Devs automate; remembering manual backups fails exactly when needed | M | SAF document access only; no new permissions |

## D3 — Owner-decision points (policy tradeoffs — do NOT start without an explicit owner call)

| ID | Feature | Tradeoff |
|---|---|---|
| D3.1 | **Plain JSON/Markdown export (opt-in)** of tasks for scripting/automation | Encrypted backup stays the default; plaintext export is data at rest outside Keystore protection — must be explicit, disclosed, and labeled |
| D3.2 | **HTTP loopback dev-mode** (localhost / 127.0.0.1 only, behind an explicit toggle) | Breaks the HTTPS-only invariant; useful for testing local servers, but widens attack surface if left enabled |
| D3.3 | **Task dependencies** (blocked-by + a "Waiting" filter) | Real project tracking vs. scope creep for a personal task list; schema + UI cost |

## Documentation-truth note (for the next PROJECT_PLAN.md revision)

Historical note from 2026-10-10: at the cited earlier `main` snapshot, the capability map lacked notes, due time, subtasks, and reminder/repeat status. That note is now superseded: current `main` and `PROJECT_PLAN.md` describe those source features, while device notification acceptance remains unverified. It is retained here as dated proposal context, not as current backlog guidance.

## Delivery rules (inherited)

- One focused PR per picked item; recheck open PRs/issues first.
- CI green ≠ device tested. Reminders and composer declutter remain **DEVICE VERIFIED: NO**.
- No release tag (v1.0.6) and no version bump until the owner's device pass.

---
Work by: Vibe
Model: GLM (glm-5-latest-short)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-10T16:06:17Z

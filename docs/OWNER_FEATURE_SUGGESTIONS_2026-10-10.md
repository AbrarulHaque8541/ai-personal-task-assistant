# Daymark — Owner feature suggestions (hardcore-developer use case)

Date: 2026-10-10 · Status: **proposal only — nothing below is implemented. The owner picks items before any work starts.**

## Scope and method

- Inventory taken from live `main` @ `7df2acac` (after PR #242): README, `docs/TASK_ADD_GAPS.md`, `docs/TASK_REMINDERS.md`, `docs/FUTURE_MILESTONES.md`, `docs/PERFORMANCE_ROADMAP.md`, `PROJECT_PLAN.md`, and open issues (#221, #222, #160).
- Only items **not already covered** by the `PROJECT_PLAN.md` execution queue (selected-text actions, per-site preferences, browser diagnostics, command palette, research-to-task, model research), `docs/FUTURE_MILESTONES.md` (engine grid / re-search row), `docs/PERFORMANCE_ROADMAP.md` (P01–P12), or open issues are listed.
- Every item respects the standing policy: local-first, no telemetry, no accounts, HTTPS-only browsing, minimal permissions, no cloud fallback.

## D1 — Developer quick wins (small, focused PRs)

| ID | Feature | Why it matters for a hardcore developer | Effort | Data impact |
|---|---|---|---|---|
| D1.1 | **Task tags** (per `docs/FUTURE_MILESTONES.md` #168 candidate: ≤8 tags, ≤24 chars) + tag filter chips | Dev work splits by repo/context (daymark, work, learning); search alone does not facet | M | Task schema v5 + backup manifest bump |
| D1.2 | **Natural-language time in quick add** ("9:30pm", "at 3pm tomorrow", "in 2h") — gap already flagged in `docs/TASK_ADD_GAPS.md` | Capture speed is the point of quick add; retyping the time in the editor breaks flow | S | None (parser only, host-tested) |
| D1.3 | **Repeat rules in portable backup** — known v1.0.6 limitation: restore resets repeat to "No repeat" | Backup/restore is the safety net; silently losing repeat state is data loss | S | Backup manifest v3 (DMM2 → DMM3) |
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

The capability map in `PROJECT_PLAN.md` (added in #242) currently says the task model "does not establish time-of-day, timezone, recurrence, notes, or subtasks" and lists offline timed reminders as "Roadmap / needs current-source verification". That is stale versus live `main`: notes, due time, and subtasks shipped in v1.0.3 (`docs/TASK_ADD_GAPS.md`), and best-effort OS reminders plus daily/weekly/monthly repeat rules merged in v1.0.6 (`d44d909`, `4033cf1`; see `docs/TASK_REMINDERS.md` — source-verified on `main` at `7df2acac`: `TaskReminderScheduler.java`, `TaskReminderReceiver.java`, `tools/check-reminder-lifecycle.py` present). Device acceptance is still pending (issues #160 / #221 remain owner-device gates). Those rows should be corrected in a follow-up docs pass; this file deliberately does not rewrite `PROJECT_PLAN.md`.

## Delivery rules (inherited)

- One focused PR per picked item; recheck open PRs/issues first.
- CI green ≠ device tested. Reminders and composer declutter remain **DEVICE VERIFIED: NO**.
- No release tag (v1.0.6) and no version bump until the owner's device pass.

---
Work by: Vibe
Model: GLM (glm-5-latest-short)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-10T16:06:17Z

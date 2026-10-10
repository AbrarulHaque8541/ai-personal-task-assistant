# Daymark — PLAN (hardcore personal / builder use)

Audience: owner building and living in this app daily (tasks + research browser), not Play Store scale.
Status: **suggestions + ordered plan**. Implement only after owner picks a slice.
Last updated: 2026-10-10 (Grok).

## Already strong (do not re-build)
- Encrypted local tasks, attachments, portable backup
- Sideload updater + signing discipline
- HTTPS-only WebView browser, multi-engine search, AI shortcuts
- Reminders path (OS notifications) — still needs **device** proof
- Privacy defaults (no accounts, no telemetry)

## Gaps that hurt a hardcore developer day-to-day

### P0 — trust and speed (ship before more features)
1. **Browser actually loads every time** (#221) — device matrix; WebView version; session-keep (pause not destroy on background).
2. **Chrome that does not fight you** (#222) — address + menu only; engines as compact chips while typing; no permanent Reader row.
3. **Reminder delivery proof** (#160 / #226) — real device: lock screen, reboot, exact alarm permission UX.
4. **Performance floor** — session-keep, HTTP cache (on main), no full Activity rebuild for small edits; task list incremental refresh.

### P1 — capture and retrieval (daily leverage)
5. **Natural-language quick add** — call dentist tomorrow 4pm high maps to due + priority without a second screen.
6. **Tags / projects** — lightweight labels, filter chips; still fully encrypted local.
7. **Global search** — one box over title, notes, tags, subtasks (local only).
8. **Share-in / share-out** — Android Share to new task with URL/title; share task text out.
9. **Research to task** — from browser: add selection or page title as task.
10. **Templates as muscle-memory** — long-press template to create; optional default due offset.

### P2 — browser as workbench
11. **Tab session restore** — after process death, restore tab URLs (encrypted prefs).
12. **Per-site prefs** — desktop site, image block, zoom remembered per origin.
13. **Download reliability** — queue UI, retry, open with SAF.
14. **Find-in-page + Reader** only in overflow menu.
15. **Keyboard** — Enter = Go (device-verified); optional hardware shortcuts.

### P3 — power / builder (opt-in)
16. **Scheduled encrypted backup** to SAF folder (no cloud).
17. **Bug report to GitHub issue** from in-app.
18. **Command palette** — new task, search, AI site, backup, settings.
19. **Markdown notes** (render only; store inside ciphertext).
20. **HTTP localhost exception** for dev (default off).

### Explicit non-goals
- Cloud sync / accounts / Play pressure
- GeckoView / full extension store before P0 is solid
- Request interception that fights privacy policy

## Implementation order (opinion)

- Slice A: P0 browser trust + session-keep + chrome declutter
- Slice B: P1 NL quick-add + tags + global search
- Slice C: P2 tab restore + research-to-task + downloads
- Slice D: P3 backup schedule + GitHub issue + command palette

Opinion: do not expand AI providers or GeckoView until P0 browser is device-green.

## Related
- docs/PERFORMANCE_ROADMAP.md
- PR #244 parallel suggestions — prefer one plan, not two
- Issues #221 #222 #160

Owner picks a slice; agents implement only that slice.

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-10T16:12:00Z

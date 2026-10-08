# Daymark — Add Task: what exists vs gaps

Audit of the **task capture / Add task details** flow on `main` (schema v1–v3, local encrypted store).

## What already works

| Feature | Where |
|---------|--------|
| Quick add (title only) | `addQuickTask()` → medium priority, no due date |
| Add with details dialog | `showTaskEditor` — title, optional **due date**, **priority** |
| Due date picker | `DatePickerDialog` + Clear |
| Edit existing task | Same editor |
| Templates | Create / use template with title, due date, priority |
| Attachments | Separate attach flow on task row |
| Filters | All / Today / Upcoming / Completed |
| Suggestions | Deterministic due-date + priority ranking (not AI) |
| Encrypted local save | AtomicFile + Keystore |
| Undo delete | Undo bar |

## Missing / weak (user-facing)

| Gap | Severity | Notes |
|-----|----------|--------|
| **Due time** (e.g. 3:30 PM), not only date | High | Schema is **date-only** (`YYYY-MM-DD`). No `dueTime` field. |
| **Reminders / alarm / sound** | High (requested) | No notification channel, no `AlarmManager` / WorkManager. Shared manifest allows only `INTERNET` (sideload adds network state). Sound/reminders need new permissions + background work — conflicts with current “minimal permission” policy. |
| **Quick due chips** (Today / Tomorrow / Next week) | Medium | Editor only has picker + Clear; one extra tap each time. |
| **Notes / description** under title | Medium | Title max 160 chars; no separate notes field in schema. |
| **Recurring tasks** | Medium | Not in schema or UI. |
| **Subtasks / checklist** | Low–medium | Not in schema. |
| **Location / place** | Low | Out of scope for local task core. |
| **Quick add with priority** without full dialog | Low | Always medium unless “Add details”. |
| **Natural language** (“tomorrow 5pm”) | Low | Would be UX sugar on top of structured fields. |

## Should have (recommended order)

1. **Due time (optional HH:mm)** + display on list — needs careful schema migration (v4) and portable backup codec updates.  
2. **Due chips: Today / Tomorrow / +7 days** in editor — UI-only, no schema change.  
3. **Optional notes** (capped length) — schema v4.  
4. **Local reminder** (notification at due date/time) — only if product accepts `POST_NOTIFICATIONS` (+ exact alarm on newer Android) and a small scheduled job; must stay offline (no cloud).  
5. **Custom sound** — only after (4); use system default channel sound first, custom URI later.  
6. Recurrence — after time + reminders are solid.

## Explicit non-goals (for now)

- Cloud sync / calendar Google sync  
- AI-generated task breakdown in the add form  
- Heavy reminder engines that fight the fail-closed storage model  

## Implementation status in this commit

- This document (audit).  
- `TaskDuePresets` pure helper for Today / Tomorrow / Next week ISO dates (ready for UI wiring).  
- Full schema+UI for due **time**, notes, and sound is **not** landed yet (needs coordinated store/backup/UI change).

## Browser extensions

Separate track — discuss with other models using `docs/DISCUSSION_PROMPT_BROWSER_EXTENSIONS.md` before more engine work.

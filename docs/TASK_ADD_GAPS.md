# Daymark — Add Task: what exists vs gaps

Audit of the **task capture / Add task details** flow on `main` (schema v1–v3, local encrypted store).

## What already works

| Feature | Where |
|---------|--------|
| Quick add (title only) | `addQuickTask()` → medium priority, no due date |
| Add with details dialog | `showTaskEditor` — title, optional **due date**, **priority** |
| Due date picker | `DatePickerDialog` + Clear |
| **Due chips Today / Tomorrow / +7 days** | `TaskDuePresets` + UI patch `patches/2026-10-08-task-due-chips.patch.md` (wire into `MainActivity` if not yet merged in binary) |
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
| **Due time** (e.g. 3:30 PM), not only date | High | Schema is **date-only** (`YYYY-MM-DD`). `TaskDuePresets.isTimeOnly` ready; field not in store yet. |
| **Reminders / alarm / sound** | High | Needs notification permission + scheduler; conflicts with current minimal-permission policy. |
| **Notes / description** under title | Medium | Title max 160 chars; no separate notes field. |
| **Recurring tasks** | Medium | Not in schema or UI. |
| **Subtasks / checklist** | Low–medium | Not in schema. |
| **Quick add with priority** without full dialog | Low | Always medium unless Add details. |

## Recommended order

1. Wire due chips in `MainActivity` (patch file) if not already present.  
2. Schema v4 optional `dueTime` + list display.  
3. Optional notes.  
4. Local notification reminder (product decision on permissions).  
5. Recurrence.

## Browser extensions

See `docs/DISCUSSION_PROMPT_BROWSER_EXTENSIONS.md` — discuss with other models before more engine work.

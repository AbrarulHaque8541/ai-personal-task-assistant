# Daymark — Add Task: shipped in v1.0.3

Status of the task capture / Add task details flow after the v1.0.3 task-system work
(schema v4, local encrypted store, portable backup manifest v2).

## What works

| Feature | Where |
|---------|--------|
| Quick add (title only, smart tokens: today/tomorrow/next week, high/urgent/important, low) | `addQuickTask()` → `parseSmartTaskDraft` |
| Add with details dialog | `showTaskEditor` — title, **notes**, due date, **due time**, priority, **reminder**, **subtasks** |
| Due date picker + Today / Tomorrow / Next week chips | `TaskDuePresets` + editor quick dates |
| Optional due time (HH:mm, requires a due date) | `TaskDuePresets.isTimeOnly`; shown in due labels; same-date ordering |
| **Notes** (≤4000 chars) under the title; note chip + dialog on rows | `Task.notes`, schema v4 |
| **In-app reminders** (at due time / 30 min / 1 h / 1 day before) | `Task.reminderLeadMinutes` + `reminderShownFire`; dialog on app open, shown once per fire moment. **No notification permission, no background component** — the editor hint says so. |
| **Subtasks** (≤20, titled, toggleable) | editor "More options" checklist, row chip `Subtasks · 2/5`, quick-toggle dialog |
| Edit existing task | Same editor |
| Templates | Create / use template with title, due date, priority (templates do not carry notes/time/reminder/subtasks) |
| Attachments | Separate attach flow on task row |
| Filters | All / Today / Upcoming / Completed / Overdue / No date |
| Search | Title **and notes**, case-insensitive |
| Suggestions | Deterministic due-date + due-time + priority ranking (not AI) |
| Undo delete | Undo bar |
| Encrypted local save | AtomicFile + Android Keystore, schema v4 (v1–v3 migrate) |
| Portable backup | Manifest v2 (DMM2) carries notes/due time/reminder/subtasks; DMM1 backups restore with defaults |

## Deliberately not in v1.0.3

| Gap | Reason |
|-----|--------|
| System notifications / alarms for reminders | Would need POST_NOTIFICATIONS + a receiver — conflicts with the minimal-permission, no-background-components policy and the v1 source guard. In-app reminders are the truthful v1.0.3 behavior. |
| Recurring tasks | Recurrence generation logic is a larger state machine; not added just to have the field. |
| Tags | Filter dimension adds form + filter UI complexity; search already covers title+notes. Candidate for v1.1.0. |
| Estimated duration / start date | Low user value for a personal task list; skipped rather than added for completeness. |
| Natural-language time parsing in quick add ("at 3pm") | Only date/priority tokens today; time requires a date and adds parser ambiguity. |

## Verification

- `tools/TaskSnapshotCodecSmoke` — v4 round-trip + v1/v2/v3 migration + malformed rejection (52 assertions).
- `tools/TaskLogicSmoke` — 106 assertions incl. notes/due-time/reminder/subtask rules and reminder fire-instant math.
- `tools/PortableBackupSmoke` — 330 assertions incl. DMM2 round-trip and legacy DMM1 decode.
- Web prototype `tests/task-logic.test.js` — 12/12 incl. mirrored extended-field validation.

Device rendering of the new editor/row/dialogs is **not** host-testable; the CI device-test workflow builds the instrumentation APK, and on-device behavior remains a manual check.

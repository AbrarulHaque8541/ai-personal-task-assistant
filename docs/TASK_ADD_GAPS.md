# Daymark — Add Task: v1.0.3 historical snapshot

Status of the task capture / Add task details flow after the v1.0.3 task-system work
(now schema v5, local encrypted store, portable backup manifest v3).

> The gap table below describes the v1.0.3 baseline only; it is not the current backlog. Later reminder, recurrence, tags, and natural-language quick-add work is summarized below. Current source is not the published v1.0.5 release.

## What works

| Feature | Where |
|---------|--------|
| Quick add (current source: supported dates, due times, priorities, and `#tags`; unknown words stay in the title) | `addQuickTask()` → `NaturalLanguageTaskParse`; see [`NL_QUICK_ADD.md`](NL_QUICK_ADD.md) |
| Add with details dialog | `showTaskEditor` — title, **notes**, due date, **due time**, priority, **reminder**, **subtasks** |
| Due date picker + Today / Tomorrow / Next week chips | `TaskDuePresets` + editor quick dates |
| Optional due time (HH:mm, requires a due date) | `TaskDuePresets.isTimeOnly`; shown in due labels; same-date ordering |
| **Notes** (≤4000 chars) under the title; note chip + dialog on rows | `Task.notes`, schema v5 |
| **In-app reminders** (at due time / 30 min / 1 h / 1 day before) | `Task.reminderLeadMinutes` + `reminderShownFire`; dialog on app open, shown once per fire moment. **No notification permission, no background component** — the editor hint says so. |
| **Subtasks** (≤20, titled, toggleable) | editor "More options" checklist, row chip `Subtasks · 2/5`, quick-toggle dialog |
| Edit existing task | Same editor |
| Templates | Create / use template with title, due date, priority (templates do not carry notes/time/reminder/subtasks) |
| Attachments | Separate attach flow on task row |
| Filters | All / Today / Upcoming / Completed / Overdue / No date |
| Search | Title, **notes, and tags**, case-insensitive |
| Suggestions | Deterministic due-date + due-time + priority ranking (not AI) |
| Undo delete | Undo bar |
| Encrypted local save | AtomicFile + Android Keystore, schema v5 (v1–v4 migrate) |
| Portable backup | Manifest v3 (DMM3) carries notes/due time/reminder/subtasks/tags/repeat rule/monthly anchor; DMM1/DMM2 backups remain importable with their historical defaults |

## Gaps at the v1.0.3 baseline (historical)

| Gap | Reason |
|-----|--------|
| System notifications / alarms for reminders | Would need POST_NOTIFICATIONS + a receiver — conflicts with the minimal-permission, no-background-components policy and the v1 source guard. In-app reminders are the truthful v1.0.3 behavior. |
| Recurring tasks | Recurrence generation logic is a larger state machine; not added just to have the field. |
| Tags | Filter dimension adds form + filter UI complexity; search already covers title+notes. Candidate for v1.1.0. |
| Estimated duration / start date | Low user value for a personal task list; skipped rather than added for completeness. |
| Natural-language time parsing in quick add ("at 3pm") | Only date/priority tokens today; time requires a date and adds parser ambiguity. |

## Verification

- `tools/TaskSnapshotCodecSmoke` — v5 round-trip + v1/v2/v3/v4 migration + malformed rejection (67 assertions).
- `tools/TaskLogicSmoke` — 131 assertions incl. anchored monthly recurrence, tags, notes/due-time/reminder/subtask rules, and reminder fire-instant math.
- `tools/PortableBackupSmoke` — 341 assertions incl. DMM3 tag/repeat round-trip and legacy DMM1/DMM2 decoding.
- Web prototype `tests/task-logic.test.js` — 12/12 incl. mirrored extended-field validation.

Device rendering of the new editor/row/dialogs is **not** host-testable; the CI device-test workflow builds the instrumentation APK, and on-device behavior remains a manual check.

## Update — v1.0.6 reminders work

Two of the v1.0.3 gaps above are addressed by the v1.0.6 task-reminders work (see [TASK_REMINDERS.md](TASK_REMINDERS.md)):

- **System notifications / alarms**: best-effort OS notifications through a non-exported boot receiver; `POST_NOTIFICATIONS` is requested only when a reminder is enabled. The minimal-permission, no-services policy is preserved and the v1 source guards were updated to assert it.
- **Recurring tasks**: daily / weekly / monthly repeat rules on tasks with a due date, advanced on app open or when the reminder is shown. Monthly tasks retain their original day-of-month when shorter months clamp; current DMM3 backups preserve the rule, anchor, and tags, while DMM1/DMM2 imports keep their historical defaults.

## Update — natural-language quick add in current main

[PR #292](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/292) merged natural-language quick add on 2026-10-13. `NaturalLanguageTaskParse` supports today/tomorrow/next week and weekdays; `4pm`, `4:30pm`, and 24-hour times; high/urgent/important/medium/low priority tokens; and `#tag` tokens. Unknown words remain in the task title, and the regular task editor is unchanged. The parser smoke test passed 16 assertions on the exact PR head; see [`NL_QUICK_ADD.md`](NL_QUICK_ADD.md) for examples.

This is current-source functionality, not proof that it exists in the published v1.0.5 APK or that device UX has been accepted. Current status is in [`AGENT_STATUS.md`](AGENT_STATUS.md); source identity/version is in `android-app/app/build.gradle.kts`.

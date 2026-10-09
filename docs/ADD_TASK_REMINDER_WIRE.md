# Add task → full editor + OS reminders

## Already on this branch (code)
- `TaskReminderScheduler.java` — AlarmManager + notification channel with **default notification ringtone**
- `TaskReminderReceiver.java` — shows notification; reschedules after boot
- `AndroidManifest.xml` — POST_NOTIFICATIONS, BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, VIBRATE + receiver

## MainActivity wires (must be present for end-to-end)

1. **Add task button** opens the full task editor (date, time, priority, reminder spinner already exist):

```java
addTaskButton.setOnClickListener(view -> {
    String draft = quickCaptureInput.getText() == null
            ? "" : quickCaptureInput.getText().toString();
    showTaskEditor(null, draft);
});
```

Do **not** use a separate “When · priority · details” expand layer.

2. **After load / when checking reminders**, reschedule OS alarms:

```java
try { TaskReminderScheduler.rescheduleAll(this, tasks); } catch (RuntimeException ignored) { }
```

Call this inside `checkDueReminders()` (start) and after successful `saveTasks`.

3. When user picks a reminder in the editor, call:

```java
TaskReminderScheduler.requestNotificationPermissionIfNeeded(this);
```

## What is real vs not
- **Real:** due date, due time, reminder lead minutes in Task model + editor UI; OS notification + default ringtone when alarm fires.
- **Not yet:** custom ringtone picker UI (uses system default notification sound).
- **Fallback:** in-app `checkDueReminders` dialog if alarm permission denied.

## Conflict note
PR #224 diverged from main — use **this** branch (`fix/add-task-reminder-flow-20261010`) based on current `main` instead of merging the dirty UA-only branch blindly.

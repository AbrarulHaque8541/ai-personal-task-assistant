# Daymark — Task reminders (v1.0.6)

How reminders work after the v1.0.6 OS-reminders work, including the honest limits.

## What reminders do

- Reminders fire **at due time**, or **30 minutes / 1 hour / 1 day before** the due moment (a due date is required; due time optional).
- At the reminder moment Daymark posts a **best-effort Android notification** (title, due label, tap opens the app) and also shows the **in-app reminder dialog** the next time you open the app.
- A reminder is shown **once per fire moment** (stored as `reminderShownFire`).
- **Repeat rules**: a task can repeat **Daily / Weekly / Monthly**. On app open a repeating task without a reminder rolls forward to its next occurrence; when a repeating task's reminder is shown, the task advances to the next occurrence instead of being marked done. Repeat requires a due date; clearing the due date clears the repeat.
- **Notification sound**: chosen from the system ringtone picker (notification type). Default is the system notification sound. Changing it recreates the reminder channel so the new sound applies.

## How it is scheduled

- Alarms use `AlarmManager.setAndAllowWhileIdle` (RTC_WAKEUP) — **no foreground service, no polling**.
- Alarms are (re)scheduled when the app opens, after every successful task save, and after device boot (a non-exported receiver listening only for `BOOT_COMPLETED` / `QUICKBOOT_POWERON`).
- If a notification is missed (device off, Doze), the in-app dialog on the next app open still covers it.
- If boot rescheduling fails, a flag is stored and the next app open retries.

## Permissions (honest scope)

- `POST_NOTIFICATIONS` (Android 13+): requested **only when you select a reminder option**, never at app open. If denied, notifications are skipped but in-app reminder dialogs still work.
- `RECEIVE_BOOT_COMPLETED` + `VIBRATE`: exist only for task reminders.
- The reminder receiver is `exported="false"`, listens only for boot actions, and starts no UI.
- **No reminder text leaves the device.** Nothing is synced or uploaded.

## Honest limitations

- Alarms are **inexact**: `setAndAllowWhileIdle` still allows Doze / battery-saver batching, so a notification can arrive late.
- Reminders reschedule **on app open, on save, and on boot only** — no background polling between those points.
- **Portable backup compatibility**: new exports use DMM3 and preserve tags, the repeat rule, and the original monthly day-of-month anchor. DMM1/DMM2 archives remain importable with their historical defaults; DMM2 cannot restore tags or repeat state it never stored.
- On some Android versions, per-channel overrides made in system Settings may outlive an in-app sound change; the channel is recreated to apply the new sound.

## Where the code lives

| Area | Files |
|------|-------|
| Model / rules | `Task.java` (`repeatDayOfMonth`), `TaskLogic.java` (`REPEAT_DAILY`/`REPEAT_WEEKLY`/`REPEAT_MONTHLY`, anchored `advanceRepeat`) |
| Encrypted storage | `TaskSnapshotCodec.java` (schema v5 stores `repeatRule` and `repeatDayOfMonth`; v1–v4 snapshots still load, with legacy monthly rules anchored to their current due-date day) |
| Scheduling | `TaskReminderScheduler.java` (alarms, request codes, channel + sound), `TaskReminderReceiver.java` (boot + notify) |
| Wiring | `MainActivity.java` (reschedule on open/save, repeat roll-forward, editor Repeat + sound UI, permission prompt on reminder selection) |
| Guards | `tools/check-reminder-lifecycle.py`, `tools/check-v1-source.sh`, `tools/check-merged-manifests.py` |

Device notification behavior is **not** host-testable; on-device checks remain manual (see the repo device QA issue).

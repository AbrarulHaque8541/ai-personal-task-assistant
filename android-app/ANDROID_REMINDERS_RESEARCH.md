# Offline reminder platform notes

Checked 2026-10-06 against the current Android developer documentation. This codebase currently uses `minSdk 26`, `targetSdk 35`, and the already-installed API 35 toolchain. This feature does not change the SDK target or require API 36.

## Alarms and exact access

Android describes exact alarms as time-sensitive user-facing interruptions and recommends inexact alarms whenever they meet the use case. `setExact()` can be delayed by battery-saving measures. `setExactAndAllowWhileIdle()` can run in Doze but spends more device resources. The implementation uses `setExactAndAllowWhileIdle()` only when the user-granted special access is available; otherwise it uses `setAndAllowWhileIdle()` and reports inexact timing. It does not use `setAlarmClock()`, a foreground service, a wake lock, or recurring work. Android still controls delivery; no exact-time promise is made.

On Android 14 and higher, fresh installs targeting Android 13 or higher generally do not receive `SCHEDULE_EXACT_ALARM` by default. Android directs apps to check `canScheduleExactAlarms()`, request the Settings access in the context of a user action, check again after return, and degrade gracefully if the user declines. `USE_EXACT_ALARM` is automatically granted but has limited use cases and Google Play policy implications, so this feature uses the user-granted `SCHEDULE_EXACT_ALARM` instead. Android documents that revoking `SCHEDULE_EXACT_ALARM` stops the app and cancels future exact alarms, and that the permission-state broadcast is sent on grant but not revocation. The exact path therefore also schedules a distinct inexact allow-while-idle backup for nine minutes after the chosen deadline; the two alarms share an encrypted delivered-state gate and cancel each other. This reduces loss after later revocation but remains best-effort under Doze and requires physical-device verification.

Sources: [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms), [Android 14 exact-alarm changes](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), [Request special permissions](https://developer.android.com/training/permissions/requesting-special), [Google Play exact-alarm policy link referenced by Android](https://support.google.com/googleplay/android-developer/answer/12253906).

## Notifications and sound

For target/API 33+, notifications are off by default for new installs until `POST_NOTIFICATIONS` is granted. Android recommends asking in context after a user action; a denial blocks ordinary notifications. Daymark asks only when a user saves a reminder. A denied notification permission means the reminder is not created, while task use remains available.

Android notification-channel sound is set before the channel is submitted. Channels persist, and user-controlled channel changes must be respected. Android also recommends a stable sound URI. Daymark creates a channel ID from the selected sound URI and never rewrites an existing channel; choosing a different sound selects a distinct channel. Android's `ACTION_RINGTONE_PICKER` returns the selected URI, the system default URI, or `null` for Silent. The reminder form requests notification sounds only and does not request audio-storage read access.

Sources: [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission), [NotificationChannel API](https://developer.android.com/reference/android/app/NotificationChannel), [RingtoneManager API](https://developer.android.com/reference/android/media/RingtoneManager).

## Doze and restoration

Doze defers standard alarms, jobs, and syncs. Android's Doze guide says `setAndAllowWhileIdle()` and `setExactAndAllowWhileIdle()` cannot fire more than once per nine minutes per app. The broader `AlarmManager` reference says idle intervals may be significantly longer (for example, about 15 minutes) and the system may reschedule allow-while-idle alarms out of order. The nine-minute rule is a frequency limit, not a delivery promise. The UI states that Android may delay delivery.

Android documents boot, wall-clock change, date change, and time-zone change as standard broadcast actions. The private receiver reloads encrypted reminders and restores pending one-shot alarms. Date/time reminders preserve their saved local wall-clock value and are reinterpreted in the current device time zone; relative timers preserve their stored epoch deadline. DST gaps follow `java.time` zone resolution (gap moves forward); overlapping local times use the platform's default earlier offset.

Sources: [Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby), [AlarmManager API](https://developer.android.com/reference/android/app/AlarmManager), [Intent standard broadcast actions](https://developer.android.com/reference/android/content/Intent).

## Verification boundary

Host tests exercise permission fallback decisions, the documented Doze interval, cancellation, snooze, timezone rebasing, and reboot-restoration policy. Source checks validate the manifest, private receiver, encrypted reminder file, API 35 target, and channel handling. Physical-device behavior remains unverified, including permission dialogs/settings, alarm delivery under real Doze, reboot/clock/time-zone broadcasts, ringtone URI access, channel sound changes, and notification display. No Android build, APK creation, device install, release, merge, API 36 installation, or SDK-license acceptance is part of this change.

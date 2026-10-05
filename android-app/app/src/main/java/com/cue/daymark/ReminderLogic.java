package com.cue.daymark;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** Pure policy and time calculations shared by the Android scheduler and host tests. */
final class ReminderLogic {
    static final int MIN_TIMER_MINUTES = 1;
    static final int MAX_TIMER_MINUTES = 7 * 24 * 60;
    static final int SNOOZE_MINUTES = 10;
    static final long MIN_IDLE_ALARM_INTERVAL_MILLIS = 9L * 60L * 1000L;

    enum SchedulePlan {
        NO_SCHEDULE,
        EXACT_ALLOW_WHILE_IDLE,
        INEXACT_ALLOW_WHILE_IDLE
    }

    private ReminderLogic() { }

    static SchedulePlan schedulePlan(boolean notificationsEnabled, boolean exactAlarmAccess) {
        if (!notificationsEnabled) return SchedulePlan.NO_SCHEDULE;
        return exactAlarmAccess
                ? SchedulePlan.EXACT_ALLOW_WHILE_IDLE
                : SchedulePlan.INEXACT_ALLOW_WHILE_IDLE;
    }

    static boolean needsNotificationRuntimePermission(int sdkInt) {
        return sdkInt >= 33;
    }

    static boolean needsExactAlarmSpecialAccess(int sdkInt) {
        return sdkInt >= 31;
    }

    static boolean shouldOfferExactAccess(int sdkInt, boolean exactAlarmAccess) {
        return needsExactAlarmSpecialAccess(sdkInt) && !exactAlarmAccess;
    }

    static boolean needsInexactRevocationFallback(int sdkInt, boolean exactAlarmAccess) {
        return sdkInt >= 31 && exactAlarmAccess;
    }

    static boolean usesAllowWhileIdle(SchedulePlan plan) {
        return plan == SchedulePlan.EXACT_ALLOW_WHILE_IDLE
                || plan == SchedulePlan.INEXACT_ALLOW_WHILE_IDLE;
    }

    static Reminder atLocalDateTime(String taskId, String taskTitle, LocalDateTime localDateTime,
                                    ZoneId zoneId, long nowMillis, String soundUri) {
        if (localDateTime == null || zoneId == null) throw new IllegalArgumentException("Choose a date and time.");
        long triggerAtMillis = localDateTime.atZone(zoneId).toInstant().toEpochMilli();
        if (triggerAtMillis <= nowMillis) throw new IllegalArgumentException("Choose a future date and time.");
        Reminder reminder = new Reminder(taskId, taskTitle, Reminder.MODE_LOCAL_DATE_TIME,
                localDateTime.toString(), triggerAtMillis, soundUri, false);
        requireValid(reminder);
        return reminder;
    }

    static Reminder afterMinutes(String taskId, String taskTitle, int minutes,
                                 long nowMillis, String soundUri) {
        if (minutes < MIN_TIMER_MINUTES || minutes > MAX_TIMER_MINUTES) {
            throw new IllegalArgumentException("Choose a timer from 1 minute to 7 days.");
        }
        long triggerAtMillis;
        try {
            triggerAtMillis = Math.addExact(nowMillis, Math.multiplyExact((long) minutes, 60_000L));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Timer is too long.");
        }
        Reminder reminder = new Reminder(taskId, taskTitle, Reminder.MODE_TIMER,
                null, triggerAtMillis, soundUri, false);
        requireValid(reminder);
        return reminder;
    }

    /** A local date/time stays at the same wall-clock time in the new device zone. */
    static Reminder afterTimezoneChange(Reminder reminder, ZoneId newZone) {
        if (reminder == null || !reminder.isValid() || newZone == null) {
            throw new IllegalArgumentException("A valid reminder and time zone are required.");
        }
        if (reminder.delivered || !Reminder.MODE_LOCAL_DATE_TIME.equals(reminder.mode)) return reminder;
        LocalDateTime local = LocalDateTime.parse(reminder.localDateTime);
        long nextTrigger = local.atZone(newZone).toInstant().toEpochMilli();
        return reminder.withTrigger(nextTrigger, Reminder.MODE_LOCAL_DATE_TIME, reminder.localDateTime);
    }

    /** Relative timers retain their persisted wall-clock deadline over reboot and zone changes. */
    static boolean shouldRestoreAfterReboot(Reminder reminder) {
        return reminder != null && reminder.isValid() && !reminder.delivered;
    }

    static boolean removeForTask(List<Reminder> reminders, String taskId) {
        if (reminders == null || taskId == null) return false;
        return reminders.removeIf(reminder -> taskId.equals(reminder.taskId));
    }

    static boolean belongsToOpenTask(Reminder reminder, Task task) {
        return reminder != null && reminder.isValid() && task != null
                && !task.completed && reminder.taskId.equals(task.id);
    }

    static boolean shouldDeliverForTask(Reminder reminder, Task task) {
        return belongsToOpenTask(reminder, task) && !reminder.delivered;
    }

    static Reminder snooze(Reminder reminder, long nowMillis) {
        if (reminder == null || !reminder.isValid() || !reminder.delivered) {
            throw new IllegalArgumentException("Only a delivered reminder can be snoozed.");
        }
        long nextTrigger;
        try {
            nextTrigger = Math.addExact(nowMillis, SNOOZE_MINUTES * 60_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Snooze time is invalid.");
        }
        return reminder.withTrigger(nextTrigger, Reminder.MODE_TIMER, null);
    }

    static String notificationChannelId(String soundUri) {
        String stableValue = soundUri == null ? "silent" : soundUri;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(stableValue.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("daymark_reminder_");
            for (int i = 0; i < 12; i++) result.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void requireValid(Reminder reminder) {
        if (!reminder.isValid()) throw new IllegalArgumentException("Reminder details are invalid.");
    }
}

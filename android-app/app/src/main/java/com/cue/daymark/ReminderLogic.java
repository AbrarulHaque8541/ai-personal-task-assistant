package com.cue.daymark;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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

    enum DeliveryRecoveryAction {
        REARM_ALARM,
        POST_NOTIFICATION,
        NONE
    }

    /** One explicit local-time/offset resolution; its instant is exactly what the UI previews. */
    static final class ResolvedDateTime {
        final LocalDateTime localDateTime;
        final ZoneId zoneId;
        final ZoneOffset offset;
        final long triggerAtMillis;
        final int occurrenceIndex;
        final int occurrenceCount;

        private ResolvedDateTime(LocalDateTime localDateTime, ZoneId zoneId, ZoneOffset offset,
                                 long triggerAtMillis, int occurrenceIndex, int occurrenceCount) {
            this.localDateTime = localDateTime;
            this.zoneId = zoneId;
            this.offset = offset;
            this.triggerAtMillis = triggerAtMillis;
            this.occurrenceIndex = occurrenceIndex;
            this.occurrenceCount = occurrenceCount;
        }

        String choiceLabel(DateTimeFormatter formatter) {
            String occurrence = occurrenceCount == 2
                    ? (occurrenceIndex == 0 ? "First occurrence · " : "Second occurrence · ")
                    : "";
            return occurrence + formatter.format(localDateTime) + " · "
                    + formatUtcOffset(offset) + " (" + zoneId.getId() + ")";
        }
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

    /** Returns zero candidates for a gap, one normally, or two ordered candidates for an overlap. */
    static List<ResolvedDateTime> resolveLocalDateTime(LocalDateTime localDateTime, ZoneId zoneId) {
        if (localDateTime == null || zoneId == null) {
            throw new IllegalArgumentException("Choose a date, time, and time zone.");
        }
        List<ZoneOffset> offsets = zoneId.getRules().getValidOffsets(localDateTime);
        if (offsets.isEmpty()) return Collections.emptyList();
        if (offsets.size() > 2) {
            throw new IllegalArgumentException("This time-zone transition cannot be resolved safely. Choose another date and time.");
        }
        List<ResolvedDateTime> result = new ArrayList<>(offsets.size());
        for (int index = 0; index < offsets.size(); index++) {
            ZoneOffset offset = offsets.get(index);
            long instant = absoluteEpochMillis(localDateTime, offset);
            result.add(new ResolvedDateTime(localDateTime, zoneId, offset, instant, index, offsets.size()));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Resolve only with an explicit offset when an overlap offers two valid occurrences.
     * A spring-forward gap is rejected; it is never shifted silently.
     */
    static Reminder atLocalDateTime(String taskId, String taskTitle, LocalDateTime localDateTime,
                                    ZoneId zoneId, ZoneOffset selectedOffset,
                                    long nowMillis, String soundUri) {
        List<ResolvedDateTime> candidates = resolveLocalDateTime(localDateTime, zoneId);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("That local time does not exist because clocks move forward in "
                    + zoneId.getId() + ". Choose a different time; Daymark will not shift it automatically.");
        }
        if (candidates.size() == 2 && selectedOffset == null) {
            throw new IllegalArgumentException("That local time occurs twice in " + zoneId.getId()
                    + ". Choose the first or second occurrence and its UTC offset.");
        }
        ResolvedDateTime selected = null;
        for (ResolvedDateTime candidate : candidates) {
            if (selectedOffset == null || candidate.offset.equals(selectedOffset)) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("The selected UTC offset is not valid for that local time.");
        }
        return fromResolvedDateTime(taskId, taskTitle, selected, nowMillis, soundUri);
    }

    static Reminder fromResolvedDateTime(String taskId, String taskTitle, ResolvedDateTime selected,
                                         long nowMillis, String soundUri) {
        if (selected == null) throw new IllegalArgumentException("Choose a date and time.");
        if (selected.triggerAtMillis <= nowMillis) {
            throw new IllegalArgumentException("Choose a future date and time.");
        }
        if (absoluteEpochMillis(selected.localDateTime, selected.offset) != selected.triggerAtMillis) {
            throw new IllegalArgumentException("The selected time no longer matches its scheduled instant. Choose it again.");
        }
        Reminder reminder = new Reminder(taskId, taskTitle, Reminder.MODE_LOCAL_DATE_TIME,
                selected.localDateTime.toString(), selected.zoneId.getId(),
                selected.offset.getTotalSeconds(), selected.triggerAtMillis, soundUri, false, false);
        requireValid(reminder);
        return reminder;
    }

    /** Convert only when the resulting instant fits Android's signed epoch-millisecond alarm value. */
    static long absoluteEpochMillis(LocalDateTime localDateTime, ZoneOffset offset) {
        if (localDateTime == null || offset == null) {
            throw new IllegalArgumentException("Choose a supported date and UTC offset.");
        }
        try {
            return localDateTime.toInstant(offset).toEpochMilli();
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException("That date and time is outside Android's supported scheduling range. "
                    + "Choose a value whose instant fits in epoch milliseconds.", exception);
        }
    }

    static String formatUtcOffset(ZoneOffset offset) {
        if (offset == null) throw new IllegalArgumentException("A UTC offset is required.");
        String id = offset.getId();
        return "UTC" + ("Z".equals(id) ? "+00:00" : id);
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
            throw new IllegalArgumentException("Timer is outside Android's supported scheduling range.");
        }
        Reminder reminder = new Reminder(taskId, taskTitle, Reminder.MODE_TIMER,
                null, null, null, triggerAtMillis, soundUri, false, false);
        requireValid(reminder);
        return reminder;
    }

    /**
     * Date/time reminders retain their originally selected instant, IANA zone, and UTC offset.
     * A device-zone change re-arms the same instant; it does not reinterpret the user's choice.
     */
    static Reminder afterTimezoneChange(Reminder reminder, ZoneId newDeviceZone) {
        if (reminder == null || !reminder.isValid() || newDeviceZone == null) {
            throw new IllegalArgumentException("A valid reminder and time zone are required.");
        }
        return reminder;
    }

    static boolean shouldRestoreAfterReboot(Reminder reminder) {
        return deliveryRecoveryAction(reminder) == DeliveryRecoveryAction.REARM_ALARM;
    }

    static DeliveryRecoveryAction deliveryRecoveryAction(Reminder reminder) {
        if (reminder == null || !reminder.isValid() || reminder.delivered) return DeliveryRecoveryAction.NONE;
        return reminder.deliveryPending
                ? DeliveryRecoveryAction.POST_NOTIFICATION
                : DeliveryRecoveryAction.REARM_ALARM;
    }

    static Reminder beginDelivery(Reminder reminder) {
        if (reminder == null || !reminder.isValid() || reminder.delivered) return null;
        return reminder.deliveryPending ? reminder : reminder.withDeliveryPending();
    }

    static Reminder completeDelivery(Reminder reminder) {
        if (reminder == null || !reminder.isValid() || !reminder.deliveryPending || reminder.delivered) return null;
        return reminder.withDelivered(true);
    }

    static Long revocationFallbackAtMillis(long triggerAtMillis) {
        if (triggerAtMillis <= 0 || triggerAtMillis > Long.MAX_VALUE - MIN_IDLE_ALARM_INTERVAL_MILLIS) {
            return null;
        }
        return triggerAtMillis + MIN_IDLE_ALARM_INTERVAL_MILLIS;
    }

    static long remainingMinutesCeiling(long triggerAtMillis, long nowMillis) {
        if (triggerAtMillis <= nowMillis) return 0L;
        long remaining;
        try {
            remaining = Math.subtractExact(triggerAtMillis, nowMillis);
        } catch (ArithmeticException overflow) {
            remaining = Long.MAX_VALUE;
        }
        long wholeMinutes = remaining / 60_000L;
        return wholeMinutes + (remaining % 60_000L == 0 ? 0L : 1L);
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
            throw new IllegalArgumentException("Snooze time is outside Android's supported scheduling range.");
        }
        return reminder.withTrigger(nextTrigger, Reminder.MODE_TIMER, null, null, null);
    }

    static int notificationId(String taskId) {
        int value = taskId == null ? 1 : taskId.hashCode() & 0x7fffffff;
        return value == 0 ? 1 : value;
    }

    static String notificationTag(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) throw new IllegalArgumentException("A task ID is required.");
        return "daymark.reminder:" + taskId;
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

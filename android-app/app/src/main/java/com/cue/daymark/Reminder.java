package com.cue.daymark;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Encrypted local reminder metadata, kept separate from the existing task schema. */
final class Reminder {
    static final String MODE_LOCAL_DATE_TIME = "local_date_time";
    static final String MODE_TIMER = "timer";

    final String taskId;
    final String taskTitle;
    final String mode;
    final String localDateTime;
    final String zoneId;
    final Integer offsetSeconds;
    final long triggerAtMillis;
    final String soundUri;
    final boolean deliveryPending;
    final boolean delivered;

    Reminder(String taskId, String taskTitle, String mode, String localDateTime,
             String zoneId, Integer offsetSeconds, long triggerAtMillis, String soundUri,
             boolean deliveryPending, boolean delivered) {
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.mode = mode;
        this.localDateTime = localDateTime;
        this.zoneId = zoneId;
        this.offsetSeconds = offsetSeconds;
        this.triggerAtMillis = triggerAtMillis;
        this.soundUri = soundUri;
        this.deliveryPending = deliveryPending;
        this.delivered = delivered;
    }

    Reminder withTrigger(long nextTriggerAtMillis, String nextMode, String nextLocalDateTime,
                         String nextZoneId, Integer nextOffsetSeconds) {
        return new Reminder(taskId, taskTitle, nextMode, nextLocalDateTime,
                nextZoneId, nextOffsetSeconds, nextTriggerAtMillis, soundUri, false, false);
    }

    Reminder withDeliveryPending() {
        return new Reminder(taskId, taskTitle, mode, localDateTime, zoneId, offsetSeconds,
                triggerAtMillis, soundUri, true, false);
    }

    Reminder withDelivered(boolean nextDelivered) {
        return new Reminder(taskId, taskTitle, mode, localDateTime, zoneId, offsetSeconds,
                triggerAtMillis, soundUri, false, nextDelivered);
    }

    Reminder withTitle(String nextTitle) {
        return new Reminder(taskId, nextTitle, mode, localDateTime, zoneId, offsetSeconds,
                triggerAtMillis, soundUri, deliveryPending, delivered);
    }

    boolean isValid() {
        if (taskId == null || taskId.trim().isEmpty() || taskId.length() > 128
                || taskTitle == null || taskTitle.trim().isEmpty() || taskTitle.length() > 160
                || triggerAtMillis <= 0 || (soundUri != null && soundUri.length() > 2048)
                || (deliveryPending && delivered)) {
            return false;
        }
        if (MODE_TIMER.equals(mode)) {
            return localDateTime == null && zoneId == null && offsetSeconds == null;
        }
        if (!MODE_LOCAL_DATE_TIME.equals(mode) || localDateTime == null || zoneId == null
                || zoneId.trim().isEmpty() || zoneId.length() > 128 || offsetSeconds == null
                || offsetSeconds < -18 * 60 * 60 || offsetSeconds > 18 * 60 * 60) {
            return false;
        }
        try {
            LocalDateTime local = LocalDateTime.parse(localDateTime);
            if (!local.toString().equals(localDateTime)) return false;
            ZoneId.of(zoneId);
            long resolved = ReminderLogic.absoluteEpochMillis(local,
                    ZoneOffset.ofTotalSeconds(offsetSeconds));
            return resolved == triggerAtMillis;
        } catch (DateTimeException | IllegalArgumentException exception) {
            return false;
        }
    }
}

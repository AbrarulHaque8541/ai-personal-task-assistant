package com.cue.daymark;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Encrypted reminder metadata, kept separate from the existing task schema. */
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
    /** Monotonic generation; old alarms/actions cannot mutate a newer reminder for this task. */
    final long version;

    Reminder(String taskId, String taskTitle, String mode, String localDateTime,
             String zoneId, Integer offsetSeconds, long triggerAtMillis, String soundUri,
             boolean deliveryPending, boolean delivered) {
        this(taskId, taskTitle, mode, localDateTime, zoneId, offsetSeconds, triggerAtMillis,
                soundUri, deliveryPending, delivered, 0L);
    }

    Reminder(String taskId, String taskTitle, String mode, String localDateTime,
             String zoneId, Integer offsetSeconds, long triggerAtMillis, String soundUri,
             boolean deliveryPending, boolean delivered, long version) {
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
        this.version = version;
    }

    Reminder withVersion(long nextVersion) {
        return new Reminder(taskId, taskTitle, mode, localDateTime, zoneId, offsetSeconds,
                triggerAtMillis, soundUri, deliveryPending, delivered, nextVersion);
    }

    Reminder withTrigger(long nextTriggerAtMillis, String nextMode, String nextLocalDateTime,
                         String nextZoneId, Integer nextOffsetSeconds) {
        return new Reminder(taskId, taskTitle, nextMode, nextLocalDateTime,
                nextZoneId, nextOffsetSeconds, nextTriggerAtMillis, soundUri, false, false, version);
    }

    Reminder withDeliveryPending() {
        return new Reminder(taskId, taskTitle, mode, localDateTime, zoneId, offsetSeconds,
                triggerAtMillis, soundUri, true, false, version);
    }

    Reminder withDelivered(boolean nextDelivered) {
        return new Reminder(taskId, taskTitle, mode, localDateTime, zoneId, offsetSeconds,
                triggerAtMillis, soundUri, false, nextDelivered, version);
    }

    Reminder withTitle(String nextTitle) {
        return new Reminder(taskId, nextTitle, mode, localDateTime, zoneId, offsetSeconds,
                triggerAtMillis, soundUri, deliveryPending, delivered, version);
    }

    boolean isValid() {
        if (taskId == null || taskId.trim().isEmpty() || taskId.length() > 128
                || taskTitle == null || taskTitle.trim().isEmpty() || taskTitle.length() > 160
                || triggerAtMillis <= 0 || (soundUri != null && soundUri.length() > 2048)
                || (deliveryPending && delivered) || version < 0) {
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
            if (!local.toString().equals(localDateTime)
                    || local.toLocalDate().isAfter(ReminderLogic.MAX_DATE_TIME_DATE)) return false;
            ZoneId zone = ZoneId.of(zoneId);
            ZoneOffset offset = ZoneOffset.ofTotalSeconds(offsetSeconds);
            if (!zone.getRules().getValidOffsets(local).contains(offset)) return false;
            long resolved = ReminderLogic.absoluteEpochMillis(local, offset);
            return resolved == triggerAtMillis;
        } catch (DateTimeException | IllegalArgumentException exception) {
            return false;
        }
    }
}

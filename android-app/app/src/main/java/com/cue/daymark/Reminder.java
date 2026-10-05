package com.cue.daymark;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Encrypted local reminder metadata, kept separate from the existing task schema. */
final class Reminder {
    static final String MODE_LOCAL_DATE_TIME = "local_date_time";
    static final String MODE_TIMER = "timer";

    final String taskId;
    final String taskTitle;
    final String mode;
    final String localDateTime;
    final long triggerAtMillis;
    final String soundUri;
    final boolean delivered;

    Reminder(String taskId, String taskTitle, String mode, String localDateTime,
             long triggerAtMillis, String soundUri, boolean delivered) {
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.mode = mode;
        this.localDateTime = localDateTime;
        this.triggerAtMillis = triggerAtMillis;
        this.soundUri = soundUri;
        this.delivered = delivered;
    }

    Reminder withTrigger(long nextTriggerAtMillis, String nextMode, String nextLocalDateTime) {
        return new Reminder(taskId, taskTitle, nextMode, nextLocalDateTime,
                nextTriggerAtMillis, soundUri, false);
    }

    Reminder withDelivered(boolean nextDelivered) {
        return new Reminder(taskId, taskTitle, mode, localDateTime,
                triggerAtMillis, soundUri, nextDelivered);
    }

    Reminder withTitle(String nextTitle) {
        return new Reminder(taskId, nextTitle, mode, localDateTime,
                triggerAtMillis, soundUri, delivered);
    }

    boolean isValid() {
        if (taskId == null || taskId.trim().isEmpty() || taskId.length() > 128
                || taskTitle == null || taskTitle.trim().isEmpty() || taskTitle.length() > 160
                || triggerAtMillis <= 0 || (soundUri != null && soundUri.length() > 2048)) {
            return false;
        }
        if (MODE_TIMER.equals(mode)) return localDateTime == null;
        if (!MODE_LOCAL_DATE_TIME.equals(mode) || localDateTime == null) return false;
        try {
            return LocalDateTime.parse(localDateTime).toString().equals(localDateTime);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}

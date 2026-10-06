package com.cue.daymark;

/** Durable cancellation marker retained until that reminder generation's alarm and notification are reconciled. */
final class ReminderTombstone {
    final String taskId;
    final long cancelledVersion;
    final long revision;

    ReminderTombstone(String taskId, long cancelledVersion, long revision) {
        this.taskId = taskId;
        this.cancelledVersion = cancelledVersion;
        this.revision = revision;
    }

    boolean isValid() {
        return taskId != null && !taskId.trim().isEmpty() && taskId.length() <= 128
                && cancelledVersion > 0 && revision > cancelledVersion;
    }

    boolean matches(String expectedTaskId, long expectedCancelledVersion, long expectedRevision) {
        return taskId != null && taskId.equals(expectedTaskId)
                && cancelledVersion == expectedCancelledVersion && revision == expectedRevision;
    }

    String key() {
        return taskId + "\u0000" + cancelledVersion;
    }
}

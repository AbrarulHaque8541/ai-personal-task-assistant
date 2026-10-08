package com.cue.daymark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Task {
    final String id;
    final String title;
    final String dueDate;
    final String priority;
    final boolean completed;
    final String createdAt;
    final String updatedAt;
    final List<AttachmentRef> attachments;
    /** Optional free-form notes. Empty string when unset. */
    final String notes;
    /** Optional due time HH:mm (24h). Requires dueDate; null when unset. */
    final String dueTime;
    /** Minutes before the due moment to remind: null = none, 0 = at due time, 30/60/1440 = before. */
    final Integer reminderLeadMinutes;
    /** ISO instant of the reminder fire moment already presented, so it is not shown twice. */
    final String reminderShownFire;
    final List<Subtask> subtasks;

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt) {
        this(id, title, dueDate, priority, completed, createdAt, updatedAt,
                Collections.emptyList());
    }

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt, List<AttachmentRef> attachments) {
        this(id, title, dueDate, priority, completed, createdAt, updatedAt, attachments,
                "", null, null, null, Collections.emptyList());
    }

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt, List<AttachmentRef> attachments,
         String notes, String dueTime, Integer reminderLeadMinutes, String reminderShownFire,
         List<Subtask> subtasks) {
        this.id = id;
        this.title = title;
        this.dueDate = dueDate;
        this.priority = priority;
        this.completed = completed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.attachments = attachments == null ? null
                : Collections.unmodifiableList(new ArrayList<>(attachments));
        this.notes = notes == null ? "" : notes;
        this.dueTime = dueTime == null || dueTime.isEmpty() ? null : dueTime;
        this.reminderLeadMinutes = reminderLeadMinutes;
        this.reminderShownFire = reminderShownFire == null || reminderShownFire.isEmpty() ? null : reminderShownFire;
        this.subtasks = subtasks == null ? null
                : Collections.unmodifiableList(new ArrayList<>(subtasks));
    }

    Task withDetails(String nextTitle, String nextDueDate, String nextPriority, String timestamp) {
        return new Task(id, nextTitle, nextDueDate, nextPriority, completed, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, reminderShownFire, subtasks);
    }

    Task withFullDetails(String nextTitle, String nextNotes, String nextDueDate, String nextDueTime,
                         String nextPriority, Integer nextReminderLead, List<Subtask> nextSubtasks,
                         String timestamp) {
        return new Task(id, nextTitle, nextDueDate, nextPriority, completed, createdAt, timestamp,
                attachments, nextNotes, nextDueTime, nextReminderLead, reminderShownFire, nextSubtasks);
    }

    Task withCompleted(boolean nextCompleted, String timestamp) {
        return new Task(id, title, dueDate, priority, nextCompleted, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, reminderShownFire, subtasks);
    }

    Task withSubtasks(List<Subtask> nextSubtasks, String timestamp) {
        return new Task(id, title, dueDate, priority, completed, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, reminderShownFire, nextSubtasks);
    }

    Task withReminderShown(String fireInstant, String timestamp) {
        return new Task(id, title, dueDate, priority, completed, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, fireInstant, subtasks);
    }

    Task withAttachments(List<AttachmentRef> nextAttachments) {
        return new Task(id, title, dueDate, priority, completed, createdAt, updatedAt,
                nextAttachments, notes, dueTime, reminderLeadMinutes, reminderShownFire, subtasks);
    }
}

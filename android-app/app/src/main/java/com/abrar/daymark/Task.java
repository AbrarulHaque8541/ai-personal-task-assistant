package com.abrar.daymark;

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
    /** Optional repeat rule that rolls the due date forward: null, "daily", "weekly", or "monthly". Requires dueDate. */
    final String repeatRule;
    /** Original calendar day for a monthly series (1–31); short months clamp without changing this anchor. */
    final Integer repeatDayOfMonth;
    final List<Subtask> subtasks;
    /** Optional tags for search and grouping: lowercase, hyphenated, at most 5. Empty when unset. */
    final List<String> tags;

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
        this(id, title, dueDate, priority, completed, createdAt, updatedAt, attachments,
                notes, dueTime, reminderLeadMinutes, reminderShownFire, null, subtasks);
    }

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt, List<AttachmentRef> attachments,
         String notes, String dueTime, Integer reminderLeadMinutes, String reminderShownFire,
         String repeatRule, List<Subtask> subtasks) {
        this(id, title, dueDate, priority, completed, createdAt, updatedAt, attachments,
                notes, dueTime, reminderLeadMinutes, reminderShownFire, repeatRule,
                defaultRepeatDayOfMonth(dueDate, repeatRule), subtasks, Collections.emptyList());
    }

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt, List<AttachmentRef> attachments,
         String notes, String dueTime, Integer reminderLeadMinutes, String reminderShownFire,
         String repeatRule, List<Subtask> subtasks, List<String> tags) {
        this(id, title, dueDate, priority, completed, createdAt, updatedAt, attachments,
                notes, dueTime, reminderLeadMinutes, reminderShownFire, repeatRule,
                defaultRepeatDayOfMonth(dueDate, repeatRule), subtasks, tags);
    }

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt, List<AttachmentRef> attachments,
         String notes, String dueTime, Integer reminderLeadMinutes, String reminderShownFire,
         String repeatRule, Integer repeatDayOfMonth, List<Subtask> subtasks) {
        this(id, title, dueDate, priority, completed, createdAt, updatedAt, attachments,
                notes, dueTime, reminderLeadMinutes, reminderShownFire, repeatRule,
                repeatDayOfMonth, subtasks, Collections.emptyList());
    }

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt, List<AttachmentRef> attachments,
         String notes, String dueTime, Integer reminderLeadMinutes, String reminderShownFire,
         String repeatRule, Integer repeatDayOfMonth, List<Subtask> subtasks, List<String> tags) {
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
        this.repeatRule = repeatRule == null || repeatRule.isEmpty() ? null : repeatRule;
        this.repeatDayOfMonth = repeatDayOfMonth;
        this.subtasks = subtasks == null ? null
                : Collections.unmodifiableList(new ArrayList<>(subtasks));
        this.tags = tags == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(tags));
    }

    private static Integer defaultRepeatDayOfMonth(String dueDate, String repeatRule) {
        if (!"monthly".equals(repeatRule) || dueDate == null || dueDate.length() != 10) return null;
        try {
            return Integer.valueOf(dueDate.substring(8));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer repeatDayAfterEdit(String nextDueDate, String nextRepeatRule) {
        if (!"monthly".equals(nextRepeatRule)) return null;
        if ("monthly".equals(repeatRule) && dueDate != null && dueDate.equals(nextDueDate)) {
            return repeatDayOfMonth;
        }
        return defaultRepeatDayOfMonth(nextDueDate, nextRepeatRule);
    }

    Task withDetails(String nextTitle, String nextDueDate, String nextPriority, String timestamp) {
        return new Task(id, nextTitle, nextDueDate, nextPriority, completed, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, reminderShownFire, repeatRule,
                repeatDayAfterEdit(nextDueDate, repeatRule), subtasks, tags);
    }

    Task withFullDetails(String nextTitle, String nextNotes, String nextDueDate, String nextDueTime,
                         String nextPriority, Integer nextReminderLead, List<Subtask> nextSubtasks,
                         String timestamp) {
        return withFullDetails(nextTitle, nextNotes, nextDueDate, nextDueTime, nextPriority,
                nextReminderLead, repeatRule, nextSubtasks, tags, timestamp);
    }

    Task withFullDetails(String nextTitle, String nextNotes, String nextDueDate, String nextDueTime,
                         String nextPriority, Integer nextReminderLead, String nextRepeatRule,
                         List<Subtask> nextSubtasks, List<String> nextTags, String timestamp) {
        return new Task(id, nextTitle, nextDueDate, nextPriority, completed, createdAt, timestamp,
                attachments, nextNotes, nextDueTime, nextReminderLead, reminderShownFire,
                nextRepeatRule, repeatDayAfterEdit(nextDueDate, nextRepeatRule), nextSubtasks, nextTags);
    }

    Task withCompleted(boolean nextCompleted, String timestamp) {
        return new Task(id, title, dueDate, priority, nextCompleted, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, reminderShownFire, repeatRule,
                repeatDayOfMonth, subtasks, tags);
    }

    Task withSubtasks(List<Subtask> nextSubtasks, String timestamp) {
        return new Task(id, title, dueDate, priority, completed, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, reminderShownFire, repeatRule,
                repeatDayOfMonth, nextSubtasks, tags);
    }

    Task withReminderShown(String fireInstant, String timestamp) {
        return new Task(id, title, dueDate, priority, completed, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, fireInstant, repeatRule,
                repeatDayOfMonth, subtasks, tags);
    }

    /** Rolls a repeating task to its next occurrence: moves the due date, clears the shown marker, keeps rule and anchor. */
    Task withRepeatAdvanced(String nextDueDate, String timestamp) {
        return new Task(id, title, nextDueDate, priority, completed, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, null, repeatRule,
                repeatDayOfMonth, subtasks, tags);
    }

    Task withAttachments(List<AttachmentRef> nextAttachments) {
        return new Task(id, title, dueDate, priority, completed, createdAt, updatedAt,
                nextAttachments, notes, dueTime, reminderLeadMinutes, reminderShownFire, repeatRule,
                repeatDayOfMonth, subtasks, tags);
    }

    /** Replaces the tag list; normalization happens in TaskLogic before this is called. */
    Task withTags(List<String> nextTags, String timestamp) {
        return new Task(id, title, dueDate, priority, completed, createdAt, timestamp,
                attachments, notes, dueTime, reminderLeadMinutes, reminderShownFire, repeatRule,
                repeatDayOfMonth, subtasks, nextTags);
    }
}

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

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt) {
        this(id, title, dueDate, priority, completed, createdAt, updatedAt,
                Collections.emptyList());
    }

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt, List<AttachmentRef> attachments) {
        this.id = id;
        this.title = title;
        this.dueDate = dueDate;
        this.priority = priority;
        this.completed = completed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.attachments = attachments == null ? null
                : Collections.unmodifiableList(new ArrayList<>(attachments));
    }

    Task withDetails(String nextTitle, String nextDueDate, String nextPriority, String timestamp) {
        return new Task(id, nextTitle, nextDueDate, nextPriority, completed, createdAt, timestamp, attachments);
    }

    Task withCompleted(boolean nextCompleted, String timestamp) {
        return new Task(id, title, dueDate, priority, nextCompleted, createdAt, timestamp, attachments);
    }

    Task withAttachments(List<AttachmentRef> nextAttachments) {
        return new Task(id, title, dueDate, priority, completed, createdAt, updatedAt, nextAttachments);
    }
}

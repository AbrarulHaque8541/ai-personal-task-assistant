package com.cue.daymark;

final class Task {
    final String id;
    final String title;
    final String dueDate;
    final String priority;
    final boolean completed;
    final String createdAt;
    final String updatedAt;

    Task(String id, String title, String dueDate, String priority, boolean completed,
         String createdAt, String updatedAt) {
        this.id = id;
        this.title = title;
        this.dueDate = dueDate;
        this.priority = priority;
        this.completed = completed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    Task withDetails(String nextTitle, String nextDueDate, String nextPriority, String timestamp) {
        return new Task(id, nextTitle, nextDueDate, nextPriority, completed, createdAt, timestamp);
    }

    Task withCompleted(boolean nextCompleted, String timestamp) {
        return new Task(id, title, dueDate, priority, nextCompleted, createdAt, timestamp);
    }
}

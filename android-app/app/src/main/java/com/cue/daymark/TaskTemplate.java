package com.cue.daymark;

/** A reusable, task-shaped draft stored with the encrypted local task snapshot. */
final class TaskTemplate {
    final String id;
    final String title;
    final String dueDate;
    final String priority;

    TaskTemplate(String id, String title, String dueDate, String priority) {
        this.id = id;
        this.title = title;
        this.dueDate = dueDate;
        this.priority = priority;
    }
}

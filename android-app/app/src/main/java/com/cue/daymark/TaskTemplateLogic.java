package com.cue.daymark;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validation and conversion rules for local task templates. */
final class TaskTemplateLogic {
    static final int MAX_TEMPLATES = 100;
    private TaskTemplateLogic() { }

    static TaskTemplate create(String title, String dueDate, String priority) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isEmpty()) throw new IllegalArgumentException("Enter a task title.");
        if (normalizedTitle.length() > 160) {
            throw new IllegalArgumentException("Task titles can be at most 160 characters.");
        }
        if (dueDate != null && !TaskLogic.isDateOnly(dueDate)) {
            throw new IllegalArgumentException("Choose a valid due date.");
        }
        if (!TaskLogic.isPriority(priority)) throw new IllegalArgumentException("Choose a valid priority.");
        return new TaskTemplate(UUID.randomUUID().toString(), normalizedTitle, dueDate, priority);
    }

    static Task instantiate(TaskTemplate template) {
        if (!isValid(template)) throw new IllegalArgumentException("Choose a valid task template.");
        return instantiate(template, template.title, template.dueDate, template.priority);
    }

    static Task instantiate(TaskTemplate template, String reviewedTitle, String reviewedDueDate,
                            String reviewedPriority) {
        if (!isValid(template)) throw new IllegalArgumentException("Choose a valid task template.");
        // Templates provide editable values only; every created task receives a new identity,
        // fresh timestamps, an open completion state, and no copied attachment references.
        return TaskLogic.create(reviewedTitle, reviewedDueDate, reviewedPriority);
    }

    static boolean isValid(TaskTemplate template) {
        if (template == null || !isCanonicalUuid(template.id)
                || template.title == null || template.title.isEmpty()
                || !template.title.equals(template.title.trim()) || template.title.length() > 160
                || (template.dueDate != null && !TaskLogic.isDateOnly(template.dueDate))
                || !TaskLogic.isPriority(template.priority)) return false;
        return true;
    }

    static boolean isValidList(List<TaskTemplate> templates) {
        if (templates == null || templates.size() > MAX_TEMPLATES) return false;
        Set<String> ids = new HashSet<>();
        for (TaskTemplate template : templates) {
            if (!isValid(template) || !ids.add(template.id)) return false;
        }
        return true;
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}

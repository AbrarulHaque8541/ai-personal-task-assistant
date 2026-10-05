package com.cue.daymark;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class TaskLogic {
    static final String FILTER_ALL = "all";
    static final String FILTER_TODAY = "today";
    static final String FILTER_UPCOMING = "upcoming";
    static final String FILTER_COMPLETED = "completed";

    private TaskLogic() { }

    static boolean isDateOnly(String value) {
        if (value == null || value.length() != 10) return false;
        try {
            LocalDate parsed = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return parsed.toString().equals(value);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    static boolean isValid(Task task) {
        if (task == null || task.id == null || task.id.trim().isEmpty()
                || task.title == null || task.title.trim().isEmpty() || task.title.length() > 160
                || task.priority == null || !isPriority(task.priority)
                || (task.dueDate != null && !isDateOnly(task.dueDate))
                || !AttachmentLogic.isValidTaskAttachments(task.attachments)) {
            return false;
        }
        try {
            Instant.parse(task.createdAt);
            Instant.parse(task.updatedAt);
            return true;
        } catch (DateTimeParseException | NullPointerException exception) {
            return false;
        }
    }

    static boolean isValidTaskList(List<Task> tasks) {
        if (tasks == null) return false;
        Set<String> ids = new HashSet<>();
        for (Task task : tasks) {
            if (!isValid(task) || !ids.add(task.id)) return false;
        }
        return AttachmentLogic.isValidTaskListAttachments(tasks);
    }

    static boolean isPriority(String priority) {
        return "low".equals(priority) || "medium".equals(priority) || "high".equals(priority);
    }

    static Task create(String title, String dueDate, String priority) {
        String normalizedTitle = requireTitle(title);
        validateDetails(dueDate, priority);
        String now = Instant.now().toString();
        return new Task(UUID.randomUUID().toString(), normalizedTitle, dueDate, priority,
                false, now, now);
    }

    static Task update(Task existing, String title, String dueDate, String priority) {
        if (existing == null) throw new IllegalArgumentException("Task is required.");
        String normalizedTitle = requireTitle(title);
        validateDetails(dueDate, priority);
        return existing.withDetails(normalizedTitle, dueDate, priority, Instant.now().toString());
    }

    static Task toggleCompleted(Task task) {
        if (task == null) throw new IllegalArgumentException("Task is required.");
        return task.withCompleted(!task.completed, Instant.now().toString());
    }

    private static String requireTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Enter a task title.");
        if (normalized.length() > 160) throw new IllegalArgumentException("Task titles can be at most 160 characters.");
        return normalized;
    }

    private static void validateDetails(String dueDate, String priority) {
        if (dueDate != null && !isDateOnly(dueDate)) throw new IllegalArgumentException("Choose a valid due date.");
        if (!isPriority(priority)) throw new IllegalArgumentException("Choose a valid priority.");
    }

    static List<Task> filter(List<Task> tasks, String filter, String query, LocalDate today) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Task> result = new ArrayList<>();
        for (Task task : tasks) {
            if (FILTER_COMPLETED.equals(filter) && !task.completed) continue;
            if (FILTER_TODAY.equals(filter) && !today.toString().equals(task.dueDate)) continue;
            if (FILTER_UPCOMING.equals(filter)
                    && (task.dueDate == null || !LocalDate.parse(task.dueDate).isAfter(today))) continue;
            if (!normalizedQuery.isEmpty()
                    && !task.title.toLowerCase(Locale.ROOT).contains(normalizedQuery)) continue;
            result.add(task);
        }
        result.sort(taskOrder());
        return result;
    }

    static List<Suggestion> suggestions(List<Task> tasks, LocalDate today) {
        List<Task> open = new ArrayList<>();
        for (Task task : tasks) if (!task.completed) open.add(task);
        open.sort(suggestionOrder());
        List<Suggestion> result = new ArrayList<>();
        for (int index = 0; index < Math.min(3, open.size()); index++) {
            Task task = open.get(index);
            result.add(new Suggestion(task, suggestionReason(task, today)));
        }
        return result;
    }

    static String suggestionReason(Task task, LocalDate today) {
        if (task.dueDate == null) return "No due date · " + task.priority + " priority";
        LocalDate due = LocalDate.parse(task.dueDate);
        if (due.isBefore(today)) return "Overdue · " + formatDate(due) + " · " + task.priority + " priority";
        if (due.equals(today)) return "Due today · " + task.priority + " priority";
        return "Due " + formatDate(due) + " · " + task.priority + " priority";
    }

    static String formatDate(LocalDate date) {
        return DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(date);
    }

    private static Comparator<Task> taskOrder() {
        return Comparator.comparing((Task task) -> task.completed)
                .thenComparing(task -> task.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(task -> priorityRank(task.priority))
                .thenComparing(task -> Instant.parse(task.createdAt))
                .thenComparing(task -> task.id);
    }

    private static Comparator<Task> suggestionOrder() {
        return Comparator.comparing((Task task) -> task.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(task -> priorityRank(task.priority))
                .thenComparing(task -> Instant.parse(task.createdAt))
                .thenComparing(task -> task.id);
    }

    private static int priorityRank(String priority) {
        if ("high".equals(priority)) return 0;
        if ("medium".equals(priority)) return 1;
        return 2;
    }

    static final class Suggestion {
        final Task task;
        final String reason;

        Suggestion(Task task, String reason) {
            this.task = task;
            this.reason = reason;
        }
    }
}

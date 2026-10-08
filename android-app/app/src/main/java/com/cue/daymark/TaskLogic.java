package com.cue.daymark;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
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
    static final String FILTER_OVERDUE = "overdue";
    static final String FILTER_NO_DATE = "no_date";

    static final int MAX_NOTES_CHARS = 4000;
    static final int MAX_SUBTASKS = 20;
    static final int MAX_SUBTASK_TITLE = 120;
    /** Reminder lead options offered by the editor, in minutes before the due moment. */
    static final int[] REMINDER_LEADS = { 0, 30, 60, 1440 };
    /** Default clock time used to compute a reminder fire moment when a task has no due time. */
    static final LocalTime DEFAULT_REMINDER_TIME = LocalTime.of(9, 0);

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
        if (task.notes != null && task.notes.length() > MAX_NOTES_CHARS) return false;
        if (task.dueTime != null && (task.dueDate == null || !TaskDuePresets.isTimeOnly(task.dueTime))) {
            return false;
        }
        if (task.reminderLeadMinutes != null && !isReminderLead(task.reminderLeadMinutes)) return false;
        if (task.reminderShownFire != null) {
            try {
                Instant.parse(task.reminderShownFire);
            } catch (DateTimeParseException | NullPointerException exception) {
                return false;
            }
        }
        if (!isValidSubtasks(task.subtasks)) return false;
        try {
            Instant.parse(task.createdAt);
            Instant.parse(task.updatedAt);
            return true;
        } catch (DateTimeParseException | NullPointerException exception) {
            return false;
        }
    }

    static boolean isValidSubtasks(List<Subtask> subtasks) {
        if (subtasks == null) return false;
        if (subtasks.size() > MAX_SUBTASKS) return false;
        Set<String> ids = new HashSet<>();
        for (Subtask subtask : subtasks) {
            if (subtask == null || subtask.id == null || subtask.id.trim().isEmpty()
                    || !ids.add(subtask.id)) return false;
            String title = subtask.title == null ? "" : subtask.title.trim();
            if (title.isEmpty() || title.length() > MAX_SUBTASK_TITLE) return false;
        }
        return true;
    }

    static boolean isReminderLead(Integer leadMinutes) {
        if (leadMinutes == null) return false;
        for (int allowed : REMINDER_LEADS) {
            if (allowed == leadMinutes) return true;
        }
        return false;
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
        return create(title, dueDate, priority, "", null, null, null);
    }

    static Task create(String title, String dueDate, String priority, String notes,
                       String dueTime, Integer reminderLeadMinutes, List<Subtask> subtasks) {
        String normalizedTitle = requireTitle(title);
        String normalizedNotes = requireNotes(notes);
        validateDetails(dueDate, priority);
        validateDueTime(dueDate, dueTime);
        validateReminder(reminderLeadMinutes);
        List<Subtask> normalizedSubtasks = normalizeSubtasks(subtasks);
        String now = Instant.now().toString();
        return new Task(UUID.randomUUID().toString(), normalizedTitle, dueDate, priority,
                false, now, now, Collections.<AttachmentRef>emptyList(), normalizedNotes,
                dueTime, reminderLeadMinutes, null, normalizedSubtasks);
    }

    static Task update(Task existing, String title, String dueDate, String priority) {
        if (existing == null) throw new IllegalArgumentException("Task is required.");
        // Clearing the due date also clears the due time and reminder: both are meaningless
        // (and invalid) without a date, and silently keeping them would surprise the user.
        String dueTime = dueDate == null ? null : existing.dueTime;
        Integer reminderLead = dueDate == null ? null : existing.reminderLeadMinutes;
        return update(existing, title, existing.notes, dueDate, dueTime, priority,
                reminderLead, existing.subtasks);
    }

    static Task update(Task existing, String title, String notes, String dueDate, String dueTime,
                       String priority, Integer reminderLeadMinutes, List<Subtask> subtasks) {
        if (existing == null) throw new IllegalArgumentException("Task is required.");
        String normalizedTitle = requireTitle(title);
        String normalizedNotes = requireNotes(notes);
        validateDetails(dueDate, priority);
        validateDueTime(dueDate, dueTime);
        validateReminder(reminderLeadMinutes);
        List<Subtask> normalizedSubtasks = normalizeSubtasks(subtasks);
        return existing.withFullDetails(normalizedTitle, normalizedNotes, dueDate, dueTime,
                priority, reminderLeadMinutes, normalizedSubtasks, Instant.now().toString());
    }

    static Task toggleCompleted(Task task) {
        if (task == null) throw new IllegalArgumentException("Task is required.");
        return task.withCompleted(!task.completed, Instant.now().toString());
    }

    static Task toggleSubtask(Task task, String subtaskId) {
        if (task == null) throw new IllegalArgumentException("Task is required.");
        if (subtaskId == null) throw new IllegalArgumentException("Subtask is required.");
        List<Subtask> updated = new ArrayList<>();
        boolean found = false;
        for (Subtask subtask : task.subtasks) {
            if (subtask.id.equals(subtaskId)) {
                updated.add(subtask.withDone(!subtask.done));
                found = true;
            } else {
                updated.add(subtask);
            }
        }
        if (!found) throw new IllegalArgumentException("Subtask was not found.");
        return task.withSubtasks(updated, Instant.now().toString());
    }

    static Task addSubtask(Task task, String title) {
        if (task == null) throw new IllegalArgumentException("Task is required.");
        if (task.subtasks.size() >= MAX_SUBTASKS) {
            throw new IllegalArgumentException("A task can have at most " + MAX_SUBTASKS + " subtasks.");
        }
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Enter a subtask name.");
        if (normalized.length() > MAX_SUBTASK_TITLE) {
            throw new IllegalArgumentException("Subtasks can be at most " + MAX_SUBTASK_TITLE + " characters.");
        }
        List<Subtask> updated = new ArrayList<>(task.subtasks);
        updated.add(new Subtask(UUID.randomUUID().toString(), normalized, false));
        return task.withSubtasks(updated, Instant.now().toString());
    }

    static Task removeSubtask(Task task, String subtaskId) {
        if (task == null) throw new IllegalArgumentException("Task is required.");
        List<Subtask> updated = new ArrayList<>();
        boolean found = false;
        for (Subtask subtask : task.subtasks) {
            if (subtask.id.equals(subtaskId)) {
                found = true;
            } else {
                updated.add(subtask);
            }
        }
        if (!found) throw new IllegalArgumentException("Subtask was not found.");
        return task.withSubtasks(updated, Instant.now().toString());
    }

    static int completedSubtaskCount(Task task) {
        if (task == null || task.subtasks == null) return 0;
        int count = 0;
        for (Subtask subtask : task.subtasks) {
            if (subtask.done) count++;
        }
        return count;
    }

    /**
     * Absolute fire moment for a task's reminder, or null when no reminder can fire.
     * The due moment is the due date at the due time (or 09:00 when no time is set);
     * the lead minutes shift it earlier.
     */
    static Instant reminderFireInstant(Task task, ZoneId zone) {
        if (task == null || task.reminderLeadMinutes == null || task.dueDate == null) return null;
        if (!isDateOnly(task.dueDate)) return null;
        LocalTime time = TaskDuePresets.isTimeOnly(task.dueTime)
                ? LocalTime.parse(task.dueTime, DateTimeFormatter.ISO_LOCAL_TIME)
                : DEFAULT_REMINDER_TIME;
        LocalDateTime dueMoment = LocalDateTime.of(LocalDate.parse(task.dueDate), time);
        return dueMoment.minusMinutes(task.reminderLeadMinutes).atZone(zone).toInstant();
    }

    private static String requireTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Enter a task title.");
        if (normalized.length() > 160) throw new IllegalArgumentException("Task titles can be at most 160 characters.");
        return normalized;
    }

    private static String requireNotes(String notes) {
        String normalized = notes == null ? "" : notes;
        if (normalized.length() > MAX_NOTES_CHARS) {
            throw new IllegalArgumentException("Notes can be at most " + MAX_NOTES_CHARS + " characters.");
        }
        return normalized;
    }

    private static void validateDetails(String dueDate, String priority) {
        if (dueDate != null && !isDateOnly(dueDate)) throw new IllegalArgumentException("Choose a valid due date.");
        if (!isPriority(priority)) throw new IllegalArgumentException("Choose a valid priority.");
    }

    private static void validateDueTime(String dueDate, String dueTime) {
        if (dueTime != null && (dueDate == null || !TaskDuePresets.isTimeOnly(dueTime))) {
            throw new IllegalArgumentException("A due time needs a valid due date.");
        }
    }

    private static void validateReminder(Integer reminderLeadMinutes) {
        if (reminderLeadMinutes != null && !isReminderLead(reminderLeadMinutes)) {
            throw new IllegalArgumentException("Choose a valid reminder option.");
        }
    }

    private static List<Subtask> normalizeSubtasks(List<Subtask> subtasks) {
        if (subtasks == null) return Collections.emptyList();
        if (subtasks.size() > MAX_SUBTASKS) {
            throw new IllegalArgumentException("A task can have at most " + MAX_SUBTASKS + " subtasks.");
        }
        List<Subtask> normalized = new ArrayList<>(subtasks.size());
        Set<String> ids = new HashSet<>();
        for (Subtask subtask : subtasks) {
            if (subtask == null) throw new IllegalArgumentException("Subtasks are malformed.");
            String title = subtask.title == null ? "" : subtask.title.trim();
            if (title.isEmpty()) throw new IllegalArgumentException("Every subtask needs a name.");
            if (title.length() > MAX_SUBTASK_TITLE) {
                throw new IllegalArgumentException("Subtasks can be at most " + MAX_SUBTASK_TITLE + " characters.");
            }
            if (!ids.add(subtask.id)) throw new IllegalArgumentException("Subtasks need unique ids.");
            normalized.add(new Subtask(subtask.id, title, subtask.done));
        }
        return normalized;
    }

    static List<Task> filter(List<Task> tasks, String filter, String query, LocalDate today) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Task> result = new ArrayList<>();
        for (Task task : tasks) {
            if (FILTER_COMPLETED.equals(filter) && !task.completed) continue;
            if (FILTER_OVERDUE.equals(filter) && (task.completed || task.dueDate == null || !LocalDate.parse(task.dueDate).isBefore(today))) continue;
            if (FILTER_NO_DATE.equals(filter) && task.dueDate != null) continue;
            if (FILTER_TODAY.equals(filter) && !today.toString().equals(task.dueDate)) continue;
            if (FILTER_UPCOMING.equals(filter)
                    && (task.dueDate == null || !LocalDate.parse(task.dueDate).isAfter(today))) continue;
            if (!normalizedQuery.isEmpty()) {
                String searchable = task.title.toLowerCase(Locale.ROOT)
                        + " " + task.notes.toLowerCase(Locale.ROOT)
                        + " " + task.priority.toLowerCase(Locale.ROOT)
                        + " " + (task.dueDate == null ? "no date" : task.dueDate);
                if (!searchable.contains(normalizedQuery)) continue;
            }
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

    static String formatTime(String dueTime) {
        if (!TaskDuePresets.isTimeOnly(dueTime)) return "";
        LocalTime time = LocalTime.parse(dueTime, DateTimeFormatter.ISO_LOCAL_TIME);
        return time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()));
    }

    private static Comparator<Task> taskOrder() {
        return Comparator.comparing((Task task) -> task.completed)
                .thenComparing(task -> task.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(task -> task.dueTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(task -> priorityRank(task.priority))
                .thenComparing(task -> Instant.parse(task.createdAt))
                .thenComparing(task -> task.id);
    }

    private static Comparator<Task> suggestionOrder() {
        return Comparator.comparing((Task task) -> task.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(task -> task.dueTime, Comparator.nullsLast(Comparator.naturalOrder()))
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

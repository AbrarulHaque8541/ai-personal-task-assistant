package com.cue.daymark;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TaskLogicSmoke {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static int assertions;

    private TaskLogicSmoke() { }

    public static void main(String[] args) {
        datesAreStrictAndDateOnly();
        CRUDKeepsStableIdentityAndTimestamps();
        storedTaskSnapshotsMatchWriterRules();
        filtersAndSearchCompose();
        suggestionsUseOnlyTransparentLocalRules();
        System.out.println("PASS Android core logic smoke tests: " + assertions + " assertions");
    }

    private static void datesAreStrictAndDateOnly() {
        check(TaskLogic.isDateOnly("2024-02-29"), "valid leap date accepted");
        check(!TaskLogic.isDateOnly("2026-02-29"), "impossible date rejected");
        check(!TaskLogic.isDateOnly("2026-2-09"), "non-padded date rejected");
        check(TaskLogic.isValid(task("1", "Review", "0001-01-01", "high", false, 1)), "year 0001 supported");
    }

    private static void CRUDKeepsStableIdentityAndTimestamps() {
        Task created = TaskLogic.create("  Call the dentist  ", null, "medium");
        check("Call the dentist".equals(created.title), "new titles are trimmed");
        check(TaskLogic.isValid(created) && !created.completed, "new task has valid model fields");
        Task edited = TaskLogic.update(created, "Call clinic", "2026-10-05", "high");
        check(created.id.equals(edited.id), "edits preserve task ID");
        check(created.createdAt.equals(edited.createdAt), "edits preserve createdAt");
        check(!Instant.parse(edited.updatedAt).isBefore(Instant.parse(created.updatedAt)), "edit timestamp is valid and does not move backwards");
        Task completed = TaskLogic.toggleCompleted(edited);
        check(completed.completed && completed.id.equals(edited.id), "completion toggles without changing identity");
        expectIllegalArgument(() -> TaskLogic.create("  ", null, "medium"), "blank title rejected");
        expectIllegalArgument(() -> TaskLogic.create("Bad date", "2026-02-30", "medium"), "invalid due date rejected");
    }

    private static void storedTaskSnapshotsMatchWriterRules() {
        Task first = task("one", "Write report", null, "medium", false, 1);
        Task second = task("two", "Call dentist", null, "low", false, 2);
        check(TaskLogic.isValidTaskList(Arrays.asList(first, second)), "distinct valid tasks form a valid stored snapshot");
        check(!TaskLogic.isValidTaskList(Arrays.asList(first, task("one", "Duplicate ID", null, "low", false, 2))),
                "duplicate IDs are rejected before persistence");
        check(TaskLogic.isValid(task("max-title", "x".repeat(160), null, "medium", false, 3)),
                "160-character title remains valid");
        check(!TaskLogic.isValid(task("long-title", "x".repeat(161), null, "medium", false, 4)),
                "overlong persisted title is rejected");
        expectIllegalArgument(() -> TaskLogic.create("x".repeat(161), null, "medium"),
                "overlong task creation is rejected");
    }

    private static void filtersAndSearchCompose() {
        List<Task> tasks = Arrays.asList(
                task("today-open", "Write report", TODAY.toString(), "high", false, 1),
                task("today-done", "Send report", TODAY.toString(), "medium", true, 2),
                task("future", "Plan next week", "2026-10-12", "low", false, 3),
                task("undated", "Tidy desk", null, "low", false, 4));
        check(TaskLogic.filter(tasks, TaskLogic.FILTER_ALL, "", TODAY).size() == 4, "All includes undated tasks");
        check(TaskLogic.filter(tasks, TaskLogic.FILTER_TODAY, "", TODAY).size() == 2, "Today includes matching completed tasks");
        check(TaskLogic.filter(tasks, TaskLogic.FILTER_UPCOMING, "", TODAY).size() == 1, "Upcoming excludes undated and today tasks");
        check(TaskLogic.filter(tasks, TaskLogic.FILTER_COMPLETED, "", TODAY).size() == 1, "Completed filter isolates completed tasks");
        check(TaskLogic.filter(tasks, TaskLogic.FILTER_ALL, "  REPORT ", TODAY).size() == 2, "search is trimmed and case-insensitive");
        check("Write report".equals(tasks.get(0).title), "queries leave the shared task records unchanged");
    }

    private static void suggestionsUseOnlyTransparentLocalRules() {
        List<Task> tasks = new ArrayList<>(Arrays.asList(
                task("later", "Plan launch", "2026-10-12", "high", false, 1),
                task("undated", "Sort inbox", null, "high", false, 2),
                task("today-low", "Water plants", TODAY.toString(), "low", false, 3),
                task("overdue", "Pay invoice", "2026-10-01", "low", false, 4),
                task("today-high", "Call dentist", TODAY.toString(), "high", false, 5),
                task("done", "Already finished", "2026-10-01", "high", true, 6)));
        List<TaskLogic.Suggestion> suggestions = TaskLogic.suggestions(tasks, TODAY);
        check(suggestions.size() == 3, "only three suggestions shown");
        check("overdue".equals(suggestions.get(0).task.id), "overdue task ranks first");
        check("today-high".equals(suggestions.get(1).task.id), "high priority breaks a same-date tie");
        check("today-low".equals(suggestions.get(2).task.id), "lower priority follows on the same date");
        check(suggestions.get(0).reason.startsWith("Overdue"), "reason explains local ranking");
        check(suggestions.stream().noneMatch(item -> "done".equals(item.task.id)), "completed tasks are excluded");
        check(TaskLogic.suggestions(new ArrayList<>(), TODAY).isEmpty(), "empty list has no suggestions");
    }

    private static Task task(String id, String title, String dueDate, String priority, boolean completed, int second) {
        String timestamp = "2026-10-05T00:00:" + String.format("%02d", second) + "Z";
        return new Task(id, title, dueDate, priority, completed, timestamp, timestamp);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectIllegalArgument(Runnable operation, String message) {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }
}

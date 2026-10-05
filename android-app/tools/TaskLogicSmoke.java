package com.cue.daymark;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class TaskLogicSmoke {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static int assertions;

    private TaskLogicSmoke() { }

    public static void main(String[] args) {
        datesAreStrictAndDateOnly();
        CRUDKeepsStableIdentityAndTimestamps();
        storedTaskSnapshotsMatchWriterRules();
        snapshotSchemaRejectsCoercibleWrongTypes();
        attachmentMetadataAndQuotasAreStrict();
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

    private static void snapshotSchemaRejectsCoercibleWrongTypes() {
        check(TaskSnapshotSchema.isVersionOne(1), "numeric schema version one is accepted");
        check(TaskSnapshotSchema.isVersionTwo(2) && TaskSnapshotSchema.isSupportedVersion(1),
                "v1 remains readable and v2 is supported for attachment metadata");
        check(!TaskSnapshotSchema.isSupportedVersion(3), "unknown future schema versions are rejected");
        check(!TaskSnapshotSchema.isVersionOne("1"), "string schema version is not coerced to a number");
        check(!TaskSnapshotSchema.isVersionOne(1.5), "fractional schema version is not truncated to one");
        check("title".equals(TaskSnapshotSchema.requireString("title")), "actual JSON string values are accepted");
        expectIllegalArgument(() -> TaskSnapshotSchema.requireString(42),
                "numeric task fields are not coerced to strings");
        expectIllegalArgument(() -> TaskSnapshotSchema.requireString(null),
                "missing required task strings are rejected");
    }

    private static void attachmentMetadataAndQuotasAreStrict() {
        check("_report_.pdf".equals(AttachmentLogic.sanitizeDisplayName("../report\n.pdf")),
                "path separators and control characters are sanitized before display or persistence");
        check("application/pdf".equals(AttachmentLogic.normalizeMimeType("Application/PDF")),
                "valid MIME metadata is normalized");
        check("application/octet-stream".equals(AttachmentLogic.normalizeMimeType("../evil")),
                "invalid MIME metadata falls back to a generic type");

        AttachmentRef first = attachment(12);
        AttachmentRef second = attachment(20);
        Task firstTask = task("attach-one", "Task one", null, "medium", false, 11)
                .withAttachments(Arrays.asList(first));
        Task secondTask = task("attach-two", "Task two", null, "low", false, 12)
                .withAttachments(Arrays.asList(second));
        check(TaskLogic.isValidTaskList(Arrays.asList(firstTask, secondTask)),
                "distinct valid attachment references are accepted across tasks");
        check(AttachmentLogic.remainingBytes(Arrays.asList(firstTask, secondTask))
                        == AttachmentLogic.MAX_TOTAL_BYTES - 32,
                "remaining aggregate quota is computed from persisted measured sizes");
        check(AttachmentLogic.isSafeToOpenExternally(first), "PDF has a confirmed external-open route");
        check(!AttachmentLogic.isSafeToOpenExternally(new AttachmentRef(UUID.randomUUID().toString(),
                        "script.html", "text/html", 1)), "active HTML content cannot be externally opened");
        check(!AttachmentLogic.isSafeToOpenExternally(new AttachmentRef(UUID.randomUUID().toString(),
                        "package.apk", "application/vnd.android.package-archive", 1)),
                "installer packages cannot be externally opened");
        check(!AttachmentLogic.isSafeToOpenExternally(new AttachmentRef(UUID.randomUUID().toString(),
                        "legacy.doc", "application/msword", 1)),
                "legacy binary Word formats stay outside the external-open allowlist");
        check(!AttachmentLogic.isSafeToOpenExternally(new AttachmentRef(UUID.randomUUID().toString(),
                        "unknown.bin", "application/octet-stream", 1)),
                "unknown formats remain attachable but have no external-open action");
        check(!TaskLogic.isValidTaskList(Arrays.asList(firstTask,
                        task("attach-three", "Task three", null, "low", false, 13)
                                .withAttachments(Arrays.asList(first)))),
                "the same app-owned payload cannot be referenced twice");

        List<AttachmentRef> six = Arrays.asList(attachment(1), attachment(1), attachment(1),
                attachment(1), attachment(1), attachment(1));
        check(!TaskLogic.isValid(task("too-many", "Too many", null, "medium", false, 14)
                        .withAttachments(six)), "more than five attachments on one task are rejected");
        List<Task> overTotalCount = new ArrayList<>();
        for (int index = 0; index <= AttachmentLogic.MAX_TOTAL_COUNT; index++) {
            overTotalCount.add(task("global-count-" + index, "Global count " + index,
                    null, "low", false, 20 + index).withAttachments(Arrays.asList(attachment(1))));
        }
        check(!TaskLogic.isValidTaskList(overTotalCount), "more than 100 attachments overall are rejected");
        List<Task> overTotal = Arrays.asList(
                task("quota-one", "Quota one", null, "medium", false, 15)
                        .withAttachments(Arrays.asList(attachment(AttachmentLogic.MAX_FILE_BYTES),
                                attachment(AttachmentLogic.MAX_FILE_BYTES), attachment(AttachmentLogic.MAX_FILE_BYTES))),
                task("quota-two", "Quota two", null, "medium", false, 16)
                        .withAttachments(Arrays.asList(attachment(AttachmentLogic.MAX_FILE_BYTES),
                                attachment(AttachmentLogic.MAX_FILE_BYTES), attachment(AttachmentLogic.MAX_FILE_BYTES))));
        check(!TaskLogic.isValidTaskList(overTotal), "aggregate attachment bytes cannot exceed 100 MiB");
        check(!AttachmentLogic.canAddToTask(firstTask, Arrays.asList(firstTask.withAttachments(
                        Arrays.asList(attachment(AttachmentLogic.MAX_TOTAL_BYTES))))),
                "adding is disabled at the total attachment byte limit");
        check(!AttachmentLogic.isValid(new AttachmentRef("not-a-uuid", "x.pdf", "application/pdf", 1)),
                "provider-controlled paths cannot be accepted as attachment IDs");
        check(!AttachmentLogic.isValid(attachment(AttachmentLogic.MAX_FILE_BYTES + 1)),
                "metadata above the per-file cap is rejected");
    }

    private static AttachmentRef attachment(long bytes) {
        return new AttachmentRef(UUID.randomUUID().toString(), "report.pdf", "application/pdf", bytes);
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

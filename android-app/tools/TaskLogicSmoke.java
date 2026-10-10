package com.cue.daymark;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TaskLogicSmoke {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static int assertions;

    private TaskLogicSmoke() { }

    public static void main(String[] args) {
        datesAreStrictAndDateOnly();
        CRUDKeepsStableIdentityAndTimestamps();
        templatesValidateAndCreateFreshTasks();
        templateListsRejectDuplicatesAndExcess();
        storedTaskSnapshotsMatchWriterRules();
        snapshotSchemaRejectsCoercibleWrongTypes();
        notesDueTimeAndReminderValidate();
        subtasksValidateAndToggle();
        attachmentMetadataAndQuotasAreStrict();
        filtersAndSearchCompose();
        suggestionsUseOnlyTransparentLocalRules();
        repeatRulesValidateAndAdvance();
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

    private static void templatesValidateAndCreateFreshTasks() {
        TaskTemplate template = TaskTemplateLogic.create("  Weekly review  ", "2026-10-12", "high");
        check("Weekly review".equals(template.title), "template titles are trimmed before saving");
        check(TaskTemplateLogic.isValid(template), "new task template has valid bounded fields");
        Task task = TaskTemplateLogic.instantiate(template);
        check(TaskLogic.isValid(task), "template converts to a valid task draft");
        check(!template.id.equals(task.id), "template reuse creates a fresh task identity");
        check("Weekly review".equals(task.title) && "2026-10-12".equals(task.dueDate)
                        && "high".equals(task.priority),
                "template values populate the editable task fields");
        check(!task.completed && task.attachments.isEmpty(),
                "template-created task starts open and does not copy attachments");
        Task edited = TaskTemplateLogic.instantiate(template, "Weekly review for launch", null, "medium");
        check(TaskLogic.isValid(edited) && !edited.id.equals(template.id)
                        && "Weekly review for launch".equals(edited.title) && edited.dueDate == null,
                "populated task values remain editable before task creation");
        expectIllegalArgument(() -> TaskTemplateLogic.create("  ", null, "medium"),
                "blank template title rejected");
        expectIllegalArgument(() -> TaskTemplateLogic.create("x".repeat(161), null, "medium"),
                "overlong template title rejected");
        expectIllegalArgument(() -> TaskTemplateLogic.create("Review", "2026-02-30", "medium"),
                "invalid template date rejected");
        expectIllegalArgument(() -> TaskTemplateLogic.create("Review", null, "urgent"),
                "invalid template priority rejected");
        check(TaskSnapshotSchema.isVersionThree(3) && TaskSnapshotSchema.isSupportedVersion(1)
                        && TaskSnapshotSchema.isSupportedVersion(2),
                "new template schema is supported without dropping v1 or v2 readers");
        check(TaskSnapshotSchema.isVersionFour(4) && TaskSnapshotSchema.isSupportedVersion(4),
                "schema v4 (notes, due time, reminders, subtasks) is supported");
        check(!TaskSnapshotSchema.isSupportedVersion(5), "unknown future snapshot schema is rejected");
    }

    private static void templateListsRejectDuplicatesAndExcess() {
        TaskTemplate one = TaskTemplateLogic.create("First", null, "medium");
        TaskTemplate two = TaskTemplateLogic.create("Second", null, "low");
        check(TaskTemplateLogic.isValidList(Arrays.asList(one, two)),
                "distinct templates form a valid stored template list");
        check(!TaskTemplateLogic.isValidList(Arrays.asList(one,
                        new TaskTemplate(one.id, "Duplicate ID", null, "medium"))),
                "duplicate template IDs are rejected before persistence");
        List<TaskTemplate> overLimit = new ArrayList<>();
        for (int index = 0; index <= TaskTemplateLogic.MAX_TEMPLATES; index++) {
            overLimit.add(TaskTemplateLogic.create("Template " + index, null, "medium"));
        }
        check(!TaskTemplateLogic.isValidList(overLimit), "template storage count is bounded");
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
        check(TaskSnapshotSchema.isVersionFour(4) && TaskSnapshotSchema.isSupportedVersion(4),
                "schema v4 is supported for notes, due time, reminders, and subtasks");
        check(!TaskSnapshotSchema.isSupportedVersion(5), "unknown future schema versions are rejected");
        check(!TaskSnapshotSchema.isVersionOne("1"), "string schema version is not coerced to a number");
        check(!TaskSnapshotSchema.isVersionOne(1.5), "fractional schema version is not truncated to one");
        check("title".equals(TaskSnapshotSchema.requireString("title")), "actual JSON string values are accepted");
        expectIllegalArgument(() -> TaskSnapshotSchema.requireString(42),
                "numeric task fields are not coerced to strings");
        expectIllegalArgument(() -> TaskSnapshotSchema.requireString(null),
                "missing required task strings are rejected");
    }

    private static void notesDueTimeAndReminderValidate() {
        Task withTime = TaskLogic.create("Call clinic", "2026-10-05", "high",
                "Bring insurance card", "14:30", 30, Collections.<Subtask>emptyList());
        check(TaskLogic.isValid(withTime), "notes, due time, and reminder create a valid task");
        check("Bring insurance card".equals(withTime.notes) && "14:30".equals(withTime.dueTime)
                && Integer.valueOf(30).equals(withTime.reminderLeadMinutes),
                "extended create keeps the provided values");
        expectIllegalArgument(() -> TaskLogic.create("No date time", null, "medium", "", "14:30", null, null),
                "due time without a due date is rejected");
        expectIllegalArgument(() -> TaskLogic.create("Bad time", "2026-10-05", "medium", "", "9:30", null, null),
                "non-padded due time is rejected");
        expectIllegalArgument(() -> TaskLogic.create("Bad time 2", "2026-10-05", "medium", "", "24:00", null, null),
                "out-of-range due time is rejected");
        expectIllegalArgument(() -> TaskLogic.create("Bad reminder", "2026-10-05", "medium", "", null, 45, null),
                "unsupported reminder lead is rejected");
        expectIllegalArgument(() -> TaskLogic.create("Long notes", null, "medium",
                "x".repeat(TaskLogic.MAX_NOTES_CHARS + 1), null, null, null),
                "overlong notes are rejected");
        Task longNotes = task("notes", "Notes", null, "medium", false, 30);
        check(!TaskLogic.isValid(longNotes.withFullDetails(longNotes.title, "x".repeat(4001),
                null, null, "medium", null, Collections.<Subtask>emptyList(), longNotes.updatedAt)),
                "persisted overlong notes fail validation");

        Task existing = TaskLogic.create("Edit me", "2026-10-05", "medium", "note", "09:00", 60,
                Collections.<Subtask>emptyList());
        Task cleared = TaskLogic.update(existing, "Edit me", null, "medium");
        check(cleared.dueDate == null && cleared.dueTime == null && cleared.reminderLeadMinutes == null,
                "clearing the due date also clears due time and reminder");
        check("note".equals(cleared.notes), "simple edits keep existing notes");
        Task fullEdit = TaskLogic.update(existing, "Edited", "extra notes", "2026-10-06", "18:45",
                "low", 1440, Collections.<Subtask>emptyList());
        check("Edited".equals(fullEdit.title) && "extra notes".equals(fullEdit.notes)
                && "18:45".equals(fullEdit.dueTime) && "low".equals(fullEdit.priority)
                && Integer.valueOf(1440).equals(fullEdit.reminderLeadMinutes),
                "full edits update every extended field");

        ZoneId zone = ZoneId.of("UTC");
        Instant atDue = TaskLogic.reminderFireInstant(
                TaskLogic.create("Fire", "2026-10-05", "medium", "", "14:30", 0, null), zone);
        check(Instant.parse("2026-10-05T14:30:00Z").equals(atDue), "reminder at due time fires at the due moment");
        Instant before = TaskLogic.reminderFireInstant(
                TaskLogic.create("Fire", "2026-10-05", "medium", "", "14:30", 60, null), zone);
        check(Instant.parse("2026-10-05T13:30:00Z").equals(before), "reminder lead shifts the fire moment earlier");
        Instant defaultTime = TaskLogic.reminderFireInstant(
                TaskLogic.create("Fire", "2026-10-05", "medium", "", null, 1440, null), zone);
        check(Instant.parse("2026-10-04T09:00:00Z").equals(defaultTime),
                "reminder without a due time uses the 09:00 default and a day lead");
        check(TaskLogic.reminderFireInstant(
                TaskLogic.create("No reminder", "2026-10-05", "medium"), zone) == null,
                "tasks without a reminder have no fire moment");
        check(TaskLogic.reminderFireInstant(
                TaskLogic.create("No date", null, "medium", "", null, 30, null), zone) == null,
                "reminders without a due date never fire");
        check("2:30 PM".equals(TaskLogic.formatTime("14:30")), "due time formats for display");
        check("".equals(TaskLogic.formatTime(null)), "missing due time formats as empty");
    }

    private static void subtasksValidateAndToggle() {
        Task base = TaskLogic.create("Checklist", "2026-10-05", "medium");
        check(base.subtasks.isEmpty() && TaskLogic.completedSubtaskCount(base) == 0,
                "new tasks start without subtasks");
        Task withSubs = TaskLogic.create("Checklist", "2026-10-05", "medium", "",
                null, null, Arrays.asList(new Subtask("s1", "Step one", false),
                        new Subtask("s2", "Step two", true)));
        check(TaskLogic.isValid(withSubs) && TaskLogic.completedSubtaskCount(withSubs) == 1,
                "subtask completion counts are computed");
        Task toggled = TaskLogic.toggleSubtask(withSubs, "s1");
        check(toggled.subtasks.get(0).done && toggled.subtasks.get(1).done
                && TaskLogic.completedSubtaskCount(toggled) == 2, "toggling flips one subtask and keeps the other");
        check(withSubs.subtasks.get(0).done == false, "toggling does not mutate the source task");
        expectIllegalArgument(() -> TaskLogic.toggleSubtask(withSubs, "missing"), "unknown subtask toggle is rejected");
        Task added = TaskLogic.addSubtask(withSubs, "  Step three  ");
        check(added.subtasks.size() == 3 && "Step three".equals(added.subtasks.get(2).title),
                "subtasks can be added with trimmed titles");
        expectIllegalArgument(() -> TaskLogic.addSubtask(withSubs, "   "), "blank subtask names are rejected");
        expectIllegalArgument(() -> TaskLogic.addSubtask(withSubs, "x".repeat(121)),
                "overlong subtask names are rejected");
        Task removed = TaskLogic.removeSubtask(withSubs, "s1");
        check(removed.subtasks.size() == 1 && "s2".equals(removed.subtasks.get(0).id),
                "subtasks can be removed by id");
        List<Subtask> tooMany = new ArrayList<>();
        for (int index = 0; index <= TaskLogic.MAX_SUBTASKS; index++) {
            tooMany.add(new Subtask("s" + index, "Step " + index, false));
        }
        expectIllegalArgument(() -> TaskLogic.create("Too many", null, "medium", "", null, null, tooMany),
                "more than the subtask limit is rejected");
        expectIllegalArgument(() -> TaskLogic.create("Dup ids", null, "medium", "", null, null,
                Arrays.asList(new Subtask("same", "One", false), new Subtask("same", "Two", false))),
                "duplicate subtask ids are rejected");
        check(!TaskLogic.isValid(task("bad-sub", "Bad", null, "medium", false, 31)
                        .withSubtasks(Arrays.asList(new Subtask("s1", "   ", false)),
                                "2026-10-05T00:00:00Z")),
                "persisted blank subtask titles fail validation");
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

        Task withNotes = task("notes-search", "Plain title", null, "medium", false, 5)
                .withFullDetails("Plain title", "remember the \u00e9clair receipt", null, null,
                        "medium", null, Collections.<Subtask>emptyList(), "2026-10-05T00:00:05Z");
        List<Task> searchable = Arrays.asList(tasks.get(0), withNotes);
        check(TaskLogic.filter(searchable, TaskLogic.FILTER_ALL, "éclair", TODAY).size() == 1,
                "search also matches task notes");
        check(TaskLogic.filter(searchable, TaskLogic.FILTER_ALL, "plain", TODAY).size() == 1,
                "search still matches titles when notes differ");

        List<Task> timed = Arrays.asList(
                task("late", "Late today", TODAY.toString(), "low", false, 6)
                        .withFullDetails("Late today", "", TODAY.toString(), "18:00", "low", null,
                                Collections.<Subtask>emptyList(), "2026-10-05T00:00:06Z"),
                task("early", "Early today", TODAY.toString(), "low", false, 7)
                        .withFullDetails("Early today", "", TODAY.toString(), "08:00", "low", null,
                                Collections.<Subtask>emptyList(), "2026-10-05T00:00:07Z"),
                task("untimed", "Untimed today", TODAY.toString(), "low", false, 8));
        List<Task> ordered = TaskLogic.filter(timed, TaskLogic.FILTER_ALL, "", TODAY);
        check("early".equals(ordered.get(0).id) && "late".equals(ordered.get(1).id)
                && "untimed".equals(ordered.get(2).id),
                "same-date tasks order by due time, untimed last");
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


    private static void repeatRulesValidateAndAdvance() {
        Task repeating = TaskLogic.create("Water the plants", "2026-10-05", "medium", "",
                "09:00", 30, Collections.<Subtask>emptyList(), TaskLogic.REPEAT_DAILY);
        check(TaskLogic.isValid(repeating) && TaskLogic.REPEAT_DAILY.equals(repeating.repeatRule),
                "a daily repeat creates a valid repeating task");
        expectIllegalArgument(() -> TaskLogic.create("Repeat without date", null, "medium", "",
                null, null, Collections.<Subtask>emptyList(), TaskLogic.REPEAT_WEEKLY),
                "repeat rule without a due date is rejected");
        expectIllegalArgument(() -> TaskLogic.create("Bad repeat rule", "2026-10-05", "medium", "",
                null, null, Collections.<Subtask>emptyList(), "hourly"),
                "unknown repeat rules are rejected");
        Task persistedBadRule = task("bad-repeat", "Bad", null, "medium", false, 40)
                .withFullDetails("Bad", "", "2026-10-05", "09:00", "medium", null, "hourly",
                        Collections.<Subtask>emptyList(), "2026-10-05T00:00:40Z");
        check(!TaskLogic.isValid(persistedBadRule), "persisted unknown repeat rules fail validation");
        Task persistedNoDate = task("bad-repeat-date", "Bad", null, "medium", false, 41)
                .withFullDetails("Bad", "", null, null, "medium", null, TaskLogic.REPEAT_MONTHLY,
                        Collections.<Subtask>emptyList(), "2026-10-05T00:00:41Z");
        check(!TaskLogic.isValid(persistedNoDate), "persisted repeat without a due date fails validation");

        ZoneId zone = ZoneId.of("UTC");
        Instant now = Instant.parse("2026-10-08T11:00:00Z");
        Task daily = TaskLogic.create("Daily", "2026-10-05", "medium", "", null, null,
                Collections.<Subtask>emptyList(), TaskLogic.REPEAT_DAILY);
        check("2026-10-09".equals(TaskLogic.advanceRepeat(daily, zone, now).dueDate),
                "daily repeat rolls forward past every missed occurrence");
        Task weekly = TaskLogic.create("Weekly", "2026-10-01", "medium", "", null, null,
                Collections.<Subtask>emptyList(), TaskLogic.REPEAT_WEEKLY);
        check("2026-10-15".equals(TaskLogic.advanceRepeat(weekly, zone, now).dueDate),
                "weekly repeat advances a whole week past the due moment");
        Task monthly = TaskLogic.create("Monthly", "2026-09-01", "medium", "", null, null,
                Collections.<Subtask>emptyList(), TaskLogic.REPEAT_MONTHLY);
        check("2026-11-01".equals(TaskLogic.advanceRepeat(monthly, zone, now).dueDate),
                "monthly repeat advances by calendar months");
        Task future = TaskLogic.create("Future", "2026-10-10", "medium", "", null, null,
                Collections.<Subtask>emptyList(), TaskLogic.REPEAT_DAILY);
        check(TaskLogic.advanceRepeat(future, zone, now) == future,
                "future occurrences are not advanced early");
        Task plain = TaskLogic.create("Plain", "2026-10-05", "medium");
        check(TaskLogic.advanceRepeat(plain, zone, now) == plain,
                "non-repeating tasks are returned unchanged");
        Task advanced = TaskLogic.advanceRepeat(daily, zone, now);
        check(advanced.reminderShownFire == null && TaskLogic.REPEAT_DAILY.equals(advanced.repeatRule),
                "advancing clears the reminder shown marker and keeps the repeat rule");

        Task keptRepeat = TaskLogic.update(daily, "Daily", "2026-10-05", "medium");
        check(TaskLogic.REPEAT_DAILY.equals(keptRepeat.repeatRule),
                "simple edits keep an existing repeat rule");
        Task clearedRepeat = TaskLogic.update(daily, "Daily", null, "medium");
        check(clearedRepeat.repeatRule == null && clearedRepeat.dueDate == null,
                "clearing the due date also clears the repeat rule");
        check("2026-10-06".equals(TaskLogic.nextDueDate("2026-10-05", TaskLogic.REPEAT_DAILY))
                        && "2026-10-12".equals(TaskLogic.nextDueDate("2026-10-05", TaskLogic.REPEAT_WEEKLY))
                        && "2026-11-05".equals(TaskLogic.nextDueDate("2026-10-05", TaskLogic.REPEAT_MONTHLY)),
                "repeat rules compute the next occurrence date");
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

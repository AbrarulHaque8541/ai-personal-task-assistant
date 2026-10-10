package com.cue.daymark;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Host-side tests for the encrypted task snapshot codec: v4 round-trip and v1–v3 migration. */
public final class TaskSnapshotCodecSmoke {
    private static int assertions;

    private TaskSnapshotCodecSmoke() { }

    public static void main(String[] args) throws Exception {
        versionFourRoundTripsEveryField();
        repeatRuleRoundTrips();
        legacyV1SnapshotMigratesWithDefaults();
        legacyV2SnapshotKeepsAttachmentsAndDefaults();
        legacyV3SnapshotKeepsTemplatesAndDefaults();
        legacySnapshotsKeepHandAddedExtendedFields();
        versionFourRejectsMalformedExtendedFields();
        codecRejectsUnsupportedAndBrokenDocuments();
        encodeRejectsInvalidTaskLists();
        writerEscapesQuotesNewlinesAndUnicode();
        System.out.println("PASS task snapshot codec smoke tests: " + assertions + " assertions");
    }

    private static void versionFourRoundTripsEveryField() throws Exception {
        Task task = new Task("task-1", "Write report", "2026-10-05", "high", false,
                "2026-10-01T08:00:00Z", "2026-10-02T09:30:00Z",
                Collections.singletonList(new AttachmentRef(UUID.randomUUID().toString(), "notes.pdf", "application/pdf", 42)),
                "Line one\nLine two with \"quotes\" and unicode: café ☕",
                "14:30", 60, "2026-10-05T13:30:00Z",
                Arrays.asList(new Subtask("sub-1", "Draft outline", true),
                        new Subtask("sub-2", "Review with team", false)));
        TaskTemplate template = new TaskTemplate(UUID.randomUUID().toString(), "Weekly review", "2026-10-12", "medium");
        byte[] encoded = TaskSnapshotCodec.encode(Arrays.asList(task), Arrays.asList(template));
        String json = new String(encoded, StandardCharsets.UTF_8);
        check(json.contains("\"version\":4"), "encoded snapshot declares schema v4");
        check(json.contains("\"reminderLeadMinutes\":60"), "reminder lead is encoded as a JSON integer");
        check(json.contains("\"dueTime\":\"14:30\""), "due time is encoded as a JSON string");

        TaskSnapshotCodec.Snapshot decoded = TaskSnapshotCodec.decode(encoded);
        check(decoded.tasks.size() == 1 && decoded.templates.size() == 1, "round-trip preserves record counts");
        Task restored = decoded.tasks.get(0);
        check("task-1".equals(restored.id) && "Write report".equals(restored.title), "identity and title round-trip");
        check("2026-10-05".equals(restored.dueDate) && "high".equals(restored.priority)
                && !restored.completed, "due date, priority, and completion round-trip");
        check(restored.attachments.size() == 1 && restored.attachments.get(0).sizeBytes == 42,
                "attachment metadata round-trips");
        check("Line one\nLine two with \"quotes\" and unicode: café ☕".equals(restored.notes),
                "notes round-trip exactly, including newlines, quotes, and unicode");
        check("14:30".equals(restored.dueTime), "due time round-trips");
        check(Integer.valueOf(60).equals(restored.reminderLeadMinutes), "reminder lead round-trips");
        check("2026-10-05T13:30:00Z".equals(restored.reminderShownFire), "reminder shown state round-trips");
        check(restored.subtasks.size() == 2, "subtasks round-trip");
        check("sub-1".equals(restored.subtasks.get(0).id) && restored.subtasks.get(0).done
                && "sub-2".equals(restored.subtasks.get(1).id) && !restored.subtasks.get(1).done,
                "subtask identity and completion round-trip");
        check(TaskLogic.isValidTaskList(decoded.tasks), "decoded v4 tasks pass shared validation");
        TaskTemplate restoredTemplate = decoded.templates.get(0);
        check(template.id.equals(restoredTemplate.id) && "2026-10-12".equals(restoredTemplate.dueDate),
                "templates round-trip");

        // Re-encoding the decoded snapshot must be byte-stable (idempotent migration target).
        byte[] reencoded = TaskSnapshotCodec.encode(decoded.tasks, decoded.templates);
        check(Arrays.equals(encoded, reencoded), "encode(decode(x)) is byte-stable");
    }

    private static void legacyV1SnapshotMigratesWithDefaults() throws Exception {
        String legacy = "{\n"
                + "  \"version\": 1,\n"
                + "  \"tasks\": [\n"
                + "    {\"id\": \"2e8cae89-3dd4-43e3-82d4-51a8df643501\", \"title\": \"Replace the scratched desk mat\","
                + " \"dueDate\": \"2026-10-06\", \"priority\": \"high\", \"completed\": false,"
                + " \"createdAt\": \"2026-10-05T10:15:30Z\", \"updatedAt\": \"2026-10-05T12:04:11Z\"},\n"
                + "    {\"id\": \"fa271a20-7586-4fc1-8855-7f8cb9059b73\", \"title\": \"Read notes\","
                + " \"dueDate\": null, \"priority\": \"low\", \"completed\": true,"
                + " \"createdAt\": \"2026-10-04T08:00:00Z\", \"updatedAt\": \"2026-10-05T14:02:59Z\"}\n"
                + "  ]\n"
                + "}";
        TaskSnapshotCodec.Snapshot decoded = TaskSnapshotCodec.decode(legacy.getBytes(StandardCharsets.UTF_8));
        check(decoded.tasks.size() == 2, "schema-v1 snapshot imports as two tasks");
        check(decoded.templates.isEmpty(), "schema-v1 snapshot has no templates");
        Task first = decoded.tasks.get(0);
        check("".equals(first.notes) && first.dueTime == null && first.reminderLeadMinutes == null
                && first.reminderShownFire == null && first.subtasks.isEmpty() && first.attachments.isEmpty(),
                "schema-v1 tasks gain empty defaults for v4 fields");
        check("2026-10-06".equals(first.dueDate) && !first.completed, "schema-v1 values are preserved");
        Task second = decoded.tasks.get(1);
        check(second.dueDate == null && second.completed, "nullable v1 due dates and completion survive");
        check(TaskLogic.isValidTaskList(decoded.tasks), "migrated v1 tasks pass validation");
    }

    private static void legacyV2SnapshotKeepsAttachmentsAndDefaults() throws Exception {
        String legacy = "{\"version\":2,\"tasks\":[{"
                + "\"id\":\"v2-task\",\"title\":\"Attach me\",\"dueDate\":null,\"priority\":\"medium\","
                + "\"completed\":false,\"createdAt\":\"2026-10-01T00:00:00Z\",\"updatedAt\":\"2026-10-01T00:00:00Z\","
                + "\"attachments\":[{\"id\":\"11111111-2222-4333-8444-555555555555\",\"displayName\":\"scan.pdf\",\"mimeType\":\"application/pdf\","
                + "\"sizeBytes\":1234}]}]}";
        TaskSnapshotCodec.Snapshot decoded = TaskSnapshotCodec.decode(legacy.getBytes(StandardCharsets.UTF_8));
        check(decoded.tasks.size() == 1, "schema-v2 snapshot imports its task");
        Task task = decoded.tasks.get(0);
        check(task.attachments.size() == 1 && "scan.pdf".equals(task.attachments.get(0).displayName)
                && task.attachments.get(0).sizeBytes == 1234, "schema-v2 attachments survive migration");
        check("".equals(task.notes) && task.dueTime == null && task.reminderLeadMinutes == null
                && task.subtasks.isEmpty(), "schema-v2 tasks gain empty defaults for v4 fields");
    }

    private static void legacyV3SnapshotKeepsTemplatesAndDefaults() throws Exception {
        String legacy = "{\"version\":3,\"tasks\":[{"
                + "\"id\":\"v3-task\",\"title\":\"Keep me\",\"dueDate\":\"2026-10-09\",\"priority\":\"low\","
                + "\"completed\":false,\"createdAt\":\"2026-10-01T00:00:00Z\",\"updatedAt\":\"2026-10-01T00:00:00Z\","
                + "\"attachments\":[]}],"
                + "\"templates\":[{\"id\":\"22222222-3333-4444-8555-666666666666\",\"title\":\"Weekly review\",\"dueDate\":null,\"priority\":\"medium\"}]}";
        TaskSnapshotCodec.Snapshot decoded = TaskSnapshotCodec.decode(legacy.getBytes(StandardCharsets.UTF_8));
        check(decoded.tasks.size() == 1 && decoded.templates.size() == 1, "schema-v3 snapshot imports tasks and templates");
        check("Weekly review".equals(decoded.templates.get(0).title), "schema-v3 templates survive migration");
        check("".equals(decoded.tasks.get(0).notes) && decoded.tasks.get(0).subtasks.isEmpty(),
                "schema-v3 tasks gain empty defaults for v4 fields");
    }

    private static void legacySnapshotsKeepHandAddedExtendedFields() throws Exception {
        String legacy = "{\"version\":3,\"tasks\":[{"
                + "\"id\":\"v3-task\",\"title\":\"Keep me\",\"dueDate\":\"2026-10-09\",\"priority\":\"low\","
                + "\"completed\":false,\"createdAt\":\"2026-10-01T00:00:00Z\",\"updatedAt\":\"2026-10-01T00:00:00Z\","
                + "\"attachments\":[],"
                + "\"notes\":\"hand-added note\",\"dueTime\":\"08:15\",\"reminderLeadMinutes\":30,"
                + "\"subtasks\":[{\"id\":\"s1\",\"title\":\"Step one\",\"done\":false}]}],"
                + "\"templates\":[]}";
        TaskSnapshotCodec.Snapshot decoded = TaskSnapshotCodec.decode(legacy.getBytes(StandardCharsets.UTF_8));
        Task task = decoded.tasks.get(0);
        check("hand-added note".equals(task.notes), "extended fields on legacy documents are preserved, not dropped");
        check("08:15".equals(task.dueTime) && Integer.valueOf(30).equals(task.reminderLeadMinutes)
                && task.subtasks.size() == 1, "hand-added due time, reminder, and subtasks survive migration");
    }

    private static void versionFourRejectsMalformedExtendedFields() {
        String base = "{\"version\":4,\"tasks\":[{"
                + "\"id\":\"t\",\"title\":\"Task\",\"dueDate\":%s,\"priority\":\"medium\","
                + "\"completed\":false,\"createdAt\":\"2026-10-01T00:00:00Z\",\"updatedAt\":\"2026-10-01T00:00:00Z\","
                + "\"notes\":%s,\"dueTime\":%s,\"reminderLeadMinutes\":%s,\"reminderShownFire\":%s,"
                + "\"subtasks\":%s,\"attachments\":[]}],\"templates\":[]}";
        expectDecodeFailure(String.format(base, "\"2026-10-09\"", "\"note\"", "\"08:15\"", "30", "null",
                "[{\"id\":\"s1\",\"title\":\"Step\",\"done\":false}]").replace("\"2026-10-09\"", "null"),
                "v4 due time without a due date is rejected");
        expectDecodeFailure(String.format(base, "\"2026-10-09\"", "12", "\"08:15\"", "30", "null", "[]"),
                "v4 notes with a non-string type are rejected");
        expectDecodeFailure(String.format(base, "\"2026-10-09\"", "\"note\"", "\"8:15\"", "30", "null", "[]"),
                "v4 malformed due time is rejected");
        expectDecodeFailure(String.format(base, "\"2026-10-09\"", "\"note\"", "\"08:15\"", "45", "null", "[]"),
                "v4 unsupported reminder lead is rejected");
        expectDecodeFailure(String.format(base, "\"2026-10-09\"", "\"note\"", "\"08:15\"", "30.5", "null", "[]"),
                "v4 fractional reminder lead is rejected");
        expectDecodeFailure(String.format(base, "\"2026-10-09\"", "\"note\"", "\"08:15\"", "30", "42", "[]"),
                "v4 non-string reminder state is rejected");
        expectDecodeFailure(String.format(base, "\"2026-10-09\"", "\"note\"", "\"08:15\"", "30", "null",
                "[{\"id\":\"s1\",\"title\":\"Step\",\"done\":\"yes\"}]"),
                "v4 non-boolean subtask state is rejected");
        expectDecodeFailure(String.format(base, "\"2026-10-09\"", "\"note\"", "\"08:15\"", "30", "null",
                "[{\"id\":\"s1\",\"title\":\"Step\",\"done\":false},{\"id\":\"s1\",\"title\":\"Again\",\"done\":false}]"),
                "v4 duplicate subtask ids are rejected");
        expectDecodeFailure(String.format(base, "\"2026-10-09\"", "\"" + "x".repeat(4001) + "\"", "\"08:15\"", "30", "null", "[]"),
                "v4 overlong notes are rejected");
        String missingNotes = "{\"version\":4,\"tasks\":[{"
                + "\"id\":\"t\",\"title\":\"Task\",\"dueDate\":null,\"priority\":\"medium\","
                + "\"completed\":false,\"createdAt\":\"2026-10-01T00:00:00Z\",\"updatedAt\":\"2026-10-01T00:00:00Z\","
                + "\"attachments\":[]}],\"templates\":[]}";
        expectDecodeFailure(missingNotes, "v4 documents missing extended fields are rejected");
    }

    private static void codecRejectsUnsupportedAndBrokenDocuments() {
        expectDecodeFailure("{\"version\":5,\"tasks\":[],\"templates\":[]}", "unsupported future schema is rejected");
        expectDecodeFailure("{\"version\":\"4\",\"tasks\":[],\"templates\":[]}", "string schema version is rejected");
        expectDecodeFailure("{\"version\":4,\"templates\":[]}", "missing task list is rejected");
        expectDecodeFailure("{\"version\":4,\"tasks\":[],\"templates\":[]} trailing", "trailing JSON data is rejected");
        expectDecodeFailure("{\"version\":4,\"tasks\":[],\"templates\":[]", "unterminated JSON is rejected");
        expectDecodeFailure("{\"version\":4,\"tasks\":[{\"id\":\"t\",\"title\":\"Task\",\"dueDate\":null,"
                        + "\"priority\":\"medium\",\"completed\":\"false\",\"createdAt\":\"2026-10-01T00:00:00Z\","
                        + "\"updatedAt\":\"2026-10-01T00:00:00Z\",\"notes\":\"\",\"dueTime\":null,"
                        + "\"reminderLeadMinutes\":null,\"reminderShownFire\":null,\"subtasks\":[],\"attachments\":[]}],"
                        + "\"templates\":[]}",
                "string completion flag is rejected");
        expectDecodeFailure("{\"version\":4,\"tasks\":[],\"templates\":[],\"templates\":[]}",
                "duplicate JSON keys are rejected");
    }

    private static void encodeRejectsInvalidTaskLists() {
        expectEncodeFailure(Collections.singletonList(
                        new Task("bad", "One", null, "urgent", false,
                                "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z")),
                "encode refuses lists that fail shared validation");
        expectEncodeFailure(null, "encode refuses a null task list");
    }

    private static void writerEscapesQuotesNewlinesAndUnicode() throws Exception {
        String tricky = "tab\there \"quoted\" back\\slash \u0001 end";
        Task task = new Task("esc", "Escape", null, "medium", false,
                "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z",
                Collections.<AttachmentRef>emptyList(), tricky, null, null, null,
                Collections.<Subtask>emptyList());
        byte[] encoded = TaskSnapshotCodec.encode(Collections.singletonList(task), Collections.<TaskTemplate>emptyList());
        String json = new String(encoded, StandardCharsets.UTF_8);
        check(json.contains("\\u0001"), "control characters are escaped in encoded JSON");
        check(json.contains("\\\"quoted\\\""), "quotes are escaped in encoded JSON");
        TaskSnapshotCodec.Snapshot decoded = TaskSnapshotCodec.decode(encoded);
        check(tricky.equals(decoded.tasks.get(0).notes), "escaped notes round-trip exactly");
    }


    private static void repeatRuleRoundTrips() throws Exception {
        Task task = new Task("repeat-1", "Water the plants", "2026-10-05", "medium", false,
                "2026-10-01T08:00:00Z", "2026-10-01T08:00:00Z",
                Collections.<AttachmentRef>emptyList(), "", null, null, null, TaskLogic.REPEAT_DAILY,
                Collections.<Subtask>emptyList());
        byte[] encoded = TaskSnapshotCodec.encode(Collections.singletonList(task),
                Collections.<TaskTemplate>emptyList());
        String json = new String(encoded, StandardCharsets.UTF_8);
        check(json.contains("\"repeatRule\":\"daily\""), "the repeat rule is encoded as a JSON string");
        TaskSnapshotCodec.Snapshot decoded = TaskSnapshotCodec.decode(encoded);
        check(TaskLogic.REPEAT_DAILY.equals(decoded.tasks.get(0).repeatRule),
                "the repeat rule round-trips through the encrypted snapshot");
        check(TaskLogic.isValidTaskList(decoded.tasks), "decoded repeating tasks pass shared validation");
        byte[] reencoded = TaskSnapshotCodec.encode(decoded.tasks, decoded.templates);
        check(Arrays.equals(encoded, reencoded), "repeat rule encoding is byte-stable");

        String legacyV4 = "{\"version\":4,\"tasks\":[{"
                + "\"id\":\"old-v4\",\"title\":\"Old task\",\"dueDate\":\"2026-10-06\",\"priority\":\"high\","
                + "\"completed\":false,\"createdAt\":\"2026-10-01T00:00:00Z\",\"updatedAt\":\"2026-10-01T00:00:00Z\","
                + "\"attachments\":[],\"notes\":\"\",\"dueTime\":null,\"reminderLeadMinutes\":null,"
                + "\"reminderShownFire\":null,\"subtasks\":[]}],\"templates\":[]}";
        TaskSnapshotCodec.Snapshot legacyDecoded =
                TaskSnapshotCodec.decode(legacyV4.getBytes(StandardCharsets.UTF_8));
        check(legacyDecoded.tasks.get(0).repeatRule == null,
                "v4 snapshots written before repeat rules load with no repeat");
        expectDecodeFailure(legacyV4.replace("\"dueDate\":\"2026-10-06\"", "\"dueDate\":null")
                        .replace("\"subtasks\":[]", "\"subtasks\":[],\"repeatRule\":\"daily\""),
                "v4 repeat rule without a due date is rejected");
        expectDecodeFailure(legacyV4.replace("\"subtasks\":[]", "\"subtasks\":[],\"repeatRule\":\"hourly\""),
                "v4 unknown repeat rules are rejected");
    }

    private static void expectDecodeFailure(String json, String message) {
        assertions++;
        try {
            TaskSnapshotCodec.decode(json.getBytes(StandardCharsets.UTF_8));
            throw new AssertionError(message);
        } catch (Exception expected) {
            // Expected decode/validation failure.
        }
    }

    private static void expectEncodeFailure(List<Task> tasks, String message) {
        assertions++;
        try {
            TaskSnapshotCodec.encode(tasks, Collections.<TaskTemplate>emptyList());
            throw new AssertionError(message);
        } catch (Exception expected) {
            // Expected encode failure.
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}

package com.cue.daymark;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java codec for the encrypted task snapshot document (schema v1–v4).
 *
 * Extracted from EncryptedTaskStore so the encode/decode and migration rules are
 * host-testable without Android classes. Backward compatibility is a hard contract:
 * v1 (no attachments), v2 (attachments), and v3 (templates) documents must keep
 * loading; new fields default to empty for older schemas. Encoding always writes v4.
 *
 * The codec depends only on StrictJsonParser and a small internal writer, so the
 * whole migration surface runs in host tests without org.json on the classpath.
 */
final class TaskSnapshotCodec {
    static final int CURRENT_VERSION = 4;

    private TaskSnapshotCodec() { }

    static final class Snapshot {
        final List<Task> tasks;
        final List<TaskTemplate> templates;

        Snapshot(List<Task> tasks, List<TaskTemplate> templates) {
            this.tasks = new ArrayList<>(tasks);
            this.templates = new ArrayList<>(templates);
        }
    }

    static byte[] encode(List<Task> tasks, List<TaskTemplate> templates) throws IOException {
        if (!TaskLogic.isValidTaskList(tasks) || !TaskTemplateLogic.isValidList(templates)) {
            throw new IOException("Refusing to save invalid or duplicate task or template data.");
        }
        Writer out = new Writer();
        out.beginObject();
        out.name("version").value(CURRENT_VERSION);
        out.name("tasks").beginArray();
        for (Task task : tasks) {
            out.beginObject();
            out.name("id").value(task.id);
            out.name("title").value(task.title);
            out.name("dueDate").nullableValue(task.dueDate);
            out.name("priority").value(task.priority);
            out.name("completed").value(task.completed);
            out.name("createdAt").value(task.createdAt);
            out.name("updatedAt").value(task.updatedAt);
            out.name("notes").value(task.notes == null ? "" : task.notes);
            out.name("dueTime").nullableValue(task.dueTime);
            out.name("reminderLeadMinutes").nullableValue(task.reminderLeadMinutes);
            out.name("reminderShownFire").nullableValue(task.reminderShownFire);
            out.name("repeatRule").nullableValue(task.repeatRule);
            out.name("subtasks").beginArray();
            for (Subtask subtask : task.subtasks) {
                out.beginObject();
                out.name("id").value(subtask.id);
                out.name("title").value(subtask.title);
                out.name("done").value(subtask.done);
                out.endObject();
            }
            out.endArray();
            out.name("attachments").beginArray();
            for (AttachmentRef attachment : task.attachments) {
                out.beginObject();
                out.name("id").value(attachment.id);
                out.name("displayName").value(attachment.displayName);
                out.name("mimeType").value(attachment.mimeType);
                out.name("sizeBytes").value(attachment.sizeBytes);
                out.endObject();
            }
            out.endArray();
            out.endObject();
        }
        out.endArray();
        out.name("templates").beginArray();
        for (TaskTemplate template : templates) {
            out.beginObject();
            out.name("id").value(template.id);
            out.name("title").value(template.title);
            out.name("dueDate").nullableValue(template.dueDate);
            out.name("priority").value(template.priority);
            out.endObject();
        }
        out.endArray();
        out.endObject();
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static Snapshot decode(byte[] plaintext) throws Exception {
        Object documentValue;
        try {
            documentValue = StrictJsonParser.parse(new String(plaintext, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Task data is not valid JSON.", exception);
        }
        Map<String, Object> document = StrictJsonParser.object(documentValue, "Task snapshot");
        Object version = document.get("version");
        if (!TaskSnapshotSchema.isSupportedVersion(version)) {
            throw new IOException("Task data schema version is not supported.");
        }
        boolean versionFour = TaskSnapshotSchema.isVersionFour(version);
        boolean hasAttachments = TaskSnapshotSchema.isVersionTwo(version)
                || TaskSnapshotSchema.isVersionThree(version)
                || versionFour;
        Object tasksValue = document.get("tasks");
        if (tasksValue == null) throw new IOException("Task data is missing its task list.");
        List<Object> taskArray = StrictJsonParser.array(tasksValue, "Task list");

        List<Task> result = new ArrayList<>(taskArray.size());
        for (Object taskValue : taskArray) {
            Map<String, Object> object = StrictJsonParser.object(taskValue, "Task record");
            if (!object.containsKey("completed") || !(object.get("completed") instanceof Boolean)) {
                throw new IOException("Task completion value is malformed.");
            }
            boolean completed = (Boolean) object.get("completed");
            String dueDate = optionalString(object, "dueDate", "Task due date");
            String notes = "";
            String dueTime = null;
            Integer reminderLeadMinutes = null;
            String reminderShownFire = null;
            String repeatRule = null;
            List<Subtask> subtasks = new ArrayList<>();
            boolean hasNotesKey = object.containsKey("notes");
            if (versionFour || hasNotesKey) {
                Object notesValue = object.get("notes");
                if (!(notesValue instanceof String)) throw new IOException("Task notes are malformed.");
                notes = (String) notesValue;
            }
            if (versionFour || object.containsKey("dueTime")) {
                dueTime = optionalString(object, "dueTime", "Task due time");
            }
            if (versionFour || object.containsKey("reminderLeadMinutes")) {
                reminderLeadMinutes = optionalInteger(object, "reminderLeadMinutes");
            }
            if (versionFour || object.containsKey("reminderShownFire")) {
                reminderShownFire = optionalString(object, "reminderShownFire", "Task reminder state");
            }
            // Key-presence read: v4 snapshots written before repeat rules load with a null rule.
            if (object.containsKey("repeatRule")) {
                repeatRule = optionalString(object, "repeatRule", "Task repeat rule");
            }
            if (versionFour || object.containsKey("subtasks")) {
                Object subtaskValue = object.get("subtasks");
                if (subtaskValue == null) throw new IOException("Task subtask list is malformed.");
                List<Object> subtaskArray = StrictJsonParser.array(subtaskValue, "Task subtask list");
                for (Object subtaskItem : subtaskArray) {
                    Map<String, Object> item = StrictJsonParser.object(subtaskItem, "Task subtask");
                    Object doneValue = item.get("done");
                    if (!(doneValue instanceof Boolean)) {
                        throw new IOException("Task subtask state is malformed.");
                    }
                    subtasks.add(new Subtask(requiredString(item, "id"),
                            requiredString(item, "title"), (Boolean) doneValue));
                }
            }
            List<AttachmentRef> attachments = new ArrayList<>();
            if (hasAttachments || object.containsKey("attachments")) {
                Object referencesValue = object.get("attachments");
                if (referencesValue == null) throw new IOException("Task attachment list is malformed.");
                List<Object> references = StrictJsonParser.array(referencesValue, "Task attachment list");
                for (Object referenceValue : references) {
                    Map<String, Object> reference = StrictJsonParser.object(referenceValue,
                            "Task attachment metadata");
                    Object sizeValue = reference.get("sizeBytes");
                    if (!(sizeValue instanceof BigInteger)) {
                        throw new IOException("Task attachment size is malformed.");
                    }
                    BigInteger sizeNumber = (BigInteger) sizeValue;
                    if (sizeNumber.bitLength() > 63) {
                        throw new IOException("Task attachment size is malformed.");
                    }
                    long sizeBytes = sizeNumber.longValue();
                    if (sizeBytes < 0) throw new IOException("Task attachment size is malformed.");
                    attachments.add(new AttachmentRef(requiredString(reference, "id"),
                            requiredString(reference, "displayName"),
                            requiredString(reference, "mimeType"), sizeBytes));
                }
            }
            Task task = new Task(requiredString(object, "id"),
                    requiredString(object, "title"), dueDate,
                    requiredString(object, "priority"), completed,
                    requiredString(object, "createdAt"),
                    requiredString(object, "updatedAt"), attachments,
                    notes, dueTime, reminderLeadMinutes, reminderShownFire, repeatRule, subtasks);
            result.add(task);
        }
        if (!TaskLogic.isValidTaskList(result)) throw new IOException("Task data failed validation.");
        List<TaskTemplate> templates = new ArrayList<>();
        if (TaskSnapshotSchema.isVersionThree(version) || versionFour) {
            Object templateValue = document.get("templates");
            if (templateValue == null) throw new IOException("Task template list is malformed.");
            List<Object> templateArray = StrictJsonParser.array(templateValue, "Task template list");
            for (Object templateItem : templateArray) {
                Map<String, Object> object = StrictJsonParser.object(templateItem, "Task template record");
                String dueDate = optionalString(object, "dueDate", "Task template due date");
                templates.add(new TaskTemplate(requiredString(object, "id"),
                        requiredString(object, "title"), dueDate,
                        requiredString(object, "priority")));
            }
        }
        if (!TaskTemplateLogic.isValidList(templates)) throw new IOException("Task template data failed validation.");
        return new Snapshot(result, templates);
    }

    private static String requiredString(Map<String, Object> object, String key) throws IOException {
        Object value = object.get(key);
        if (!(value instanceof String)) {
            throw new IOException("A task snapshot field has the wrong JSON type.");
        }
        return (String) value;
    }

    private static String optionalString(Map<String, Object> object, String key, String description)
            throws IOException {
        if (!object.containsKey(key) || object.get(key) == null) return null;
        Object value = object.get(key);
        if (!(value instanceof String)) {
            throw new IOException(description + " is malformed.");
        }
        return (String) value;
    }

    private static Integer optionalInteger(Map<String, Object> object, String key) throws IOException {
        if (!object.containsKey(key) || object.get(key) == null) return null;
        Object value = object.get(key);
        if (!(value instanceof BigInteger)) {
            throw new IOException("Task reminder is malformed.");
        }
        BigInteger number = (BigInteger) value;
        if (number.bitLength() > 31) {
            throw new IOException("Task reminder is malformed.");
        }
        return number.intValue();
    }

    /** Minimal compact JSON writer; escapes exactly what JSON requires and keeps UTF-8 text raw. */
    private static final class Writer {
        private final StringBuilder out = new StringBuilder();
        /** Each entry: {hasElements, isObject}; the stack tracks nested containers. */
        private final java.util.ArrayDeque<boolean[]> stack = new java.util.ArrayDeque<>();
        private boolean expectingValue;

        Writer beginObject() {
            beforeElement();
            out.append('{');
            stack.push(new boolean[] { false, true });
            return this;
        }

        Writer endObject() {
            out.append('}');
            stack.pop();
            return this;
        }

        Writer beginArray() {
            beforeElement();
            out.append('[');
            stack.push(new boolean[] { false, false });
            return this;
        }

        Writer endArray() {
            out.append(']');
            stack.pop();
            return this;
        }

        Writer name(String key) {
            boolean[] top = stack.peek();
            if (top == null || !top[1]) throw new IllegalStateException("name() outside an object");
            if (top[0]) out.append(',');
            top[0] = true;
            writeString(key);
            out.append(':');
            expectingValue = true;
            return this;
        }

        Writer value(String text) {
            beforeElement();
            writeString(text == null ? "" : text);
            return this;
        }

        Writer value(long number) {
            beforeElement();
            out.append(number);
            return this;
        }

        Writer value(boolean flag) {
            beforeElement();
            out.append(flag);
            return this;
        }

        Writer nullableValue(String text) {
            if (text == null) return nullValue();
            return value(text);
        }

        Writer nullableValue(Integer number) {
            if (number == null) return nullValue();
            beforeElement();
            out.append(number.intValue());
            return this;
        }

        Writer nullValue() {
            beforeElement();
            out.append("null");
            return this;
        }

        private void beforeElement() {
            if (expectingValue) {
                expectingValue = false;
                return;
            }
            boolean[] top = stack.peek();
            if (top == null) return; // the root container has no parent
            if (top[0]) out.append(',');
            top[0] = true;
        }

        private void writeString(String text) {
            out.append('"');
            for (int index = 0; index < text.length(); index++) {
                char c = text.charAt(index);
                switch (c) {
                    case '"': out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    case '\t': out.append("\\t"); break;
                    case '\b': out.append("\\b"); break;
                    case '\f': out.append("\\f"); break;
                    default:
                        if (c < 0x20) {
                            out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                }
            }
            out.append('"');
        }

        @Override public String toString() {
            return out.toString();
        }
    }
}

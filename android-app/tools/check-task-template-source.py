#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
store = (root / "app/src/main/java/com/cue/daymark/EncryptedTaskStore.java").read_text(encoding="utf-8")
codec = (root / "app/src/main/java/com/cue/daymark/TaskSnapshotCodec.java").read_text(encoding="utf-8")
template_logic = (root / "app/src/main/java/com/cue/daymark/TaskTemplateLogic.java").read_text(encoding="utf-8")
checks = 0

def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)

composer = activity[activity.index("private View buildSharedComposer()"):activity.index("private void ", activity.index("private View buildSharedComposer()") + 1)]
check('compactButton("Task templates"' in composer, "Task mode exposes a compact templates entry point")
check("View, reuse, or create encrypted on-device task templates" in composer,
      "templates entry point explains local storage and explicit creation")

list_start = activity.index("private void showTaskTemplatesDialog()")
list_end = activity.index("private void removeTaskTemplate(", list_start)
template_list = activity[list_start:list_end]
check("showTaskEditorFromTemplate(template)" in template_list,
      "choosing Use opens the editable task details form")
check("setPositiveButton(\"Remove\"" in template_list,
      "template removal requires confirmation")
check("showNewTemplateEditor()" in template_list,
      "the template list can start a save-only template form")

editor_start = activity.index("private void showTaskEditorFromTemplate(")
editor_end = activity.index("private void showDatePicker(", editor_start)
editor = activity[editor_start:editor_end]
check('sourceTemplate != null ? "Create task"' in editor,
      "template review labels the explicit task-creation action")
check("TaskTemplateLogic.instantiate(sourceTemplate, normalized, selectedDate[0], priority)" in editor,
      "the confirmed task uses the reviewed template field values")
check("if (templateOnly)" in editor and "Save as template" in editor,
      "saving a template is separate from task creation")
check("saveTasksAsync(\"Saving encrypted templates…\")" in activity,
      "template changes use the existing async encrypted-save failure path")
check("taskStore.saveSnapshot(snapshot, templateSnapshot)" in activity,
      "task and template changes commit together")

check('out.name("version").value(CURRENT_VERSION)' in codec and 'CURRENT_VERSION = 4' in codec
      and 'out.name("templates").beginArray()' in codec,
      "schema v4 serializes tasks (with extended fields) and templates into the protected snapshot")
check("TaskSnapshotSchema.isVersionThree(version)" in codec and "TaskSnapshotSchema.isVersionTwo(version)" in codec
      and "TaskSnapshotSchema.isVersionFour(version)" in codec,
      "the decoder retains schema-v1/v2/v3 migration paths and reads v4")
check("TaskTemplateLogic.isValidList(templates)" in codec and "TaskLogic.isValidTaskList(result)" in codec,
      "decoded task and template records pass strict shared validation")
check("TaskSnapshotCodec.decode(" in store and "TaskSnapshotCodec.encode(" in store,
      "the encrypted store delegates snapshot coding to the shared codec")
check("TaskLogic.create(reviewedTitle, reviewedDueDate, reviewedPriority)" in template_logic,
      "confirmed template conversion creates a new task from reviewed fields")
check("SharedPreferences" not in template_logic and "SharedPreferences" not in store,
      "template content is not stored in ordinary preferences")
print(f"PASS task-template UI/storage source checks: {checks} assertions")

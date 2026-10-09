#!/usr/bin/env python3
"""Guard at-least-once in-app reminder presentation across Activity lifecycle failures."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
method = activity.split("private void checkDueReminders()", 1)[1].split(
    "private void showTaskActions", 1
)[0]
assert "reminderDialog.setOnShowListener" in method, "shown marker must be deferred until the dialog is visible"
listener = method.split("reminderDialog.setOnShowListener", 1)[1].split("try {", 1)[0]
assert "replaceTask(current.withReminderShown" in listener, "only a shown reminder may be marked as shown"
assert "if (changed) saveTasksAsync()" in listener, "shown state must be persisted after display"
assert "replaceTask(task.withReminderShown" not in method, "do not consume reminders before attempting display"
assert "reminderDialog.show()" in method, "the due reminder dialog must still be displayed"
assert "Reminder could not be shown. It will be retried the next time you open Daymark." in method, \
    "failed presentation must tell the user the reminder remains eligible for retry"
assert "TaskLogic.reminderFireInstant(current, zone)" in listener, "re-check current task state before marking shown"
print("PASS reminder lifecycle source checks: mark-after-show, revalidate task, persist only visible reminders, retry disclosure")

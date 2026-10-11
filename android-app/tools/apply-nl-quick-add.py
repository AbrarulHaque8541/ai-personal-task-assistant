#!/usr/bin/env python3
"""Wire NaturalLanguageTaskParse into TaskLogic.create (idempotent)."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
path = root / "app/src/main/java/com/cue/daymark/TaskLogic.java"
text = path.read_text(encoding="utf-8")

old = '''    static Task create(String title, String dueDate, String priority, String notes,
                       String dueTime, Integer reminderLeadMinutes, List<Subtask> subtasks,
                       String repeatRule) {
        String normalizedTitle = requireTitle(title);
        String normalizedNotes = requireNotes(notes);
        validateDetails(dueDate, priority);'''

new = '''    static Task create(String title, String dueDate, String priority, String notes,
                       String dueTime, Integer reminderLeadMinutes, List<Subtask> subtasks,
                       String repeatRule) {
        // Natural-language quick-add: fill empty structured fields from the title tokens.
        NaturalLanguageTaskParse.Result parsed =
                NaturalLanguageTaskParse.parse(title, java.time.LocalDate.now());
        if (parsed.title != null && !parsed.title.isEmpty()) {
            title = parsed.title;
        }
        if (dueDate == null && parsed.dueDate != null) {
            dueDate = parsed.dueDate;
        }
        if (dueTime == null && parsed.dueTime != null) {
            dueTime = parsed.dueTime;
        }
        if ((priority == null || "medium".equalsIgnoreCase(priority))
                && parsed.priority != null
                && !"medium".equalsIgnoreCase(parsed.priority)) {
            priority = parsed.priority;
        }
        notes = NaturalLanguageTaskParse.notesWithTags(notes, parsed.tags);
        String normalizedTitle = requireTitle(title);
        String normalizedNotes = requireNotes(notes);
        validateDetails(dueDate, priority);'''

if "NaturalLanguageTaskParse.parse" in text:
    print("TaskLogic NL already wired")
    sys.exit(0)
if old not in text:
    sys.exit("TaskLogic.create pattern missing")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("TaskLogic NL wired")

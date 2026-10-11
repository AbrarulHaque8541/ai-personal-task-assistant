#!/usr/bin/env python3
"""Wire NaturalLanguageTaskParse into TaskLogic.create (idempotent)."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
path = root / "app/src/main/java/com/cue/daymark/TaskLogic.java"
text = path.read_text(encoding="utf-8")

if "NaturalLanguageTaskParse.parse" in text:
    print("TaskLogic NL already wired")
    sys.exit(0)

nl_block = '''        NaturalLanguageTaskParse.Result parsed =
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
'''

# Prefer tags-aware create
old_tags = '''    static Task create(String title, String dueDate, String priority, String notes,
                       String dueTime, Integer reminderLeadMinutes, List<Subtask> subtasks,
                       String repeatRule, List<String> tags) {
        String normalizedTitle = requireTitle(title);
        String normalizedNotes = requireNotes(notes);
        validateDetails(dueDate, priority);'''

new_tags = '''    static Task create(String title, String dueDate, String priority, String notes,
                       String dueTime, Integer reminderLeadMinutes, List<Subtask> subtasks,
                       String repeatRule, List<String> tags) {
''' + nl_block + '''        if ((tags == null || tags.isEmpty()) && parsed.tags != null && !parsed.tags.isEmpty()) {
            tags = parsed.tags;
        }
        String normalizedTitle = requireTitle(title);
        String normalizedNotes = requireNotes(notes);
        validateDetails(dueDate, priority);'''

old_legacy = '''    static Task create(String title, String dueDate, String priority, String notes,
                       String dueTime, Integer reminderLeadMinutes, List<Subtask> subtasks,
                       String repeatRule) {
        String normalizedTitle = requireTitle(title);
        String normalizedNotes = requireNotes(notes);
        validateDetails(dueDate, priority);'''

new_legacy = '''    static Task create(String title, String dueDate, String priority, String notes,
                       String dueTime, Integer reminderLeadMinutes, List<Subtask> subtasks,
                       String repeatRule) {
''' + nl_block + '''        notes = NaturalLanguageTaskParse.notesWithTags(notes, parsed.tags);
        String normalizedTitle = requireTitle(title);
        String normalizedNotes = requireNotes(notes);
        validateDetails(dueDate, priority);'''

if old_tags in text:
    path.write_text(text.replace(old_tags, new_tags, 1), encoding="utf-8")
    print("TaskLogic NL wired (tags-aware)")
elif old_legacy in text:
    path.write_text(text.replace(old_legacy, new_legacy, 1), encoding="utf-8")
    print("TaskLogic NL wired (legacy notes tags)")
else:
    sys.exit("TaskLogic.create pattern missing")

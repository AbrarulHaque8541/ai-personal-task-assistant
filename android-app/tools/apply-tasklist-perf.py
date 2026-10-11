#!/usr/bin/env python3
"""Idempotent perf: skip identical task-list rebuilds."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
ma_path = root / "app/src/main/java/com/cue/daymark/MainActivity.java"
ma = ma_path.read_text(encoding="utf-8")
changed = False

if "taskListRenderGate" not in ma:
    needle = "private LinearLayout taskList;"
    insert = (
        "private LinearLayout taskList;\n"
        "    private final TaskListRenderGate taskListRenderGate = TaskListRenderGate.create();"
    )
    if needle not in ma:
        sys.exit("taskList field missing")
    ma = ma.replace(needle, insert, 1)
    changed = True

old = (
    "        List<Task> visible = TaskLogic.filter(tasks, selectedFilter, selectedQuery, today);\n"
    "        taskList.removeAllViews();\n"
    "        for (Task task : visible) {\n"
    "            taskList.addView(buildTaskRow(task), bottomMargin(dp(8)));\n"
    "        }"
)
new = (
    "        List<Task> visible = TaskLogic.filter(tasks, selectedFilter, selectedQuery, today);\n"
    "        if (!taskListRenderGate.shouldRebuild(selectedFilter, selectedQuery, visible)\n"
    "                && taskList.getChildCount() == visible.size()) {\n"
    "            return;\n"
    "        }\n"
    "        taskList.removeAllViews();\n"
    "        for (Task task : visible) {\n"
    "            taskList.addView(buildTaskRow(task), bottomMargin(dp(8)));\n"
    "        }"
)
if "taskListRenderGate.shouldRebuild" not in ma:
    if old not in ma:
        sys.exit("renderTaskList loop pattern missing")
    ma = ma.replace(old, new, 1)
    changed = True

if changed:
    ma_path.write_text(ma, encoding="utf-8")
    print("MainActivity perf patched")
else:
    print("MainActivity already perf-patched")

#!/usr/bin/env python3
"""Idempotent perf: skip identical task-list rebuilds + debounce search."""
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
        "    private final TaskListRenderGate taskListRenderGate = TaskListRenderGate.create();\n"
        "    private Runnable searchRenderRunnable;"
    )
    if needle not in ma:
        sys.exit("taskList field missing")
    ma = ma.replace(needle, insert, 1)
    changed = True
elif "searchRenderRunnable" not in ma:
    needle = "private final TaskListRenderGate taskListRenderGate = TaskListRenderGate.create();"
    if needle in ma:
        ma = ma.replace(
            needle,
            needle + "\n    private Runnable searchRenderRunnable;",
            1,
        )
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

old_search = (
    "            public void onTextChanged(CharSequence text, int start, int before, int count) {\n"
    "                searchQuery = text == null ? \"\" : text.toString();\n"
    "                renderTaskList();\n"
    "                renderTaskCount();\n"
    "            }"
)
new_search = (
    "            public void onTextChanged(CharSequence text, int start, int before, int count) {\n"
    "                searchQuery = text == null ? \"\" : text.toString();\n"
    "                if (searchRenderRunnable != null) {\n"
    "                    mainHandler.removeCallbacks(searchRenderRunnable);\n"
    "                }\n"
    "                searchRenderRunnable = () -> {\n"
    "                    searchRenderRunnable = null;\n"
    "                    renderTaskList();\n"
    "                    renderTaskCount();\n"
    "                };\n"
    "                mainHandler.postDelayed(searchRenderRunnable, 150);\n"
    "            }"
)
if "postDelayed(searchRenderRunnable, 150)" not in ma:
    if old_search not in ma:
        sys.exit("search TextWatcher pattern missing")
    ma = ma.replace(old_search, new_search, 1)
    changed = True

if changed:
    ma_path.write_text(ma, encoding="utf-8")
    print("MainActivity perf patched")
else:
    print("MainActivity already perf-patched")

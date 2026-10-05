#!/usr/bin/env python3
"""Source-contract checks for the read-only Android suggestion-card action."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")


def between(source: str, start: str, end: str) -> str:
    assert start in source, f"missing source section: {start}"
    tail = source.split(start, 1)[1]
    assert end in tail, f"missing end of source section: {end}"
    return tail.split(end, 1)[0]


suggestions = between(
    ACTIVITY,
    "    private void renderSuggestions(LocalDate today) {",
    "    private boolean isSuggestionActivationKey(int keyCode) {",
)
for expected in (
    "item.setMinimumHeight(dp(48));",
    "item.setClickable(true);",
    "item.setFocusable(true);",
    "item.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);",
    'item.setContentDescription("Demo suggestion "',
    "item.setOnClickListener(view -> showSuggestedTask(suggestion.task.id));",
    "item.setOnKeyListener((view, keyCode, event) -> {",
    "item.setAccessibilityDelegate(new View.AccessibilityDelegate()",
    "info.setClassName(Button.class.getName());",
    'text("Open", 11, palette.accent, Typeface.BOLD)',
    "copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);",
):
    assert expected in suggestions, f"suggestion card lacks accessible activation semantics: {expected}"

keys = between(
    ACTIVITY,
    "    private boolean isSuggestionActivationKey(int keyCode) {",
    "    private Task findTaskById(String taskId) {",
)
for expected in (
    "KeyEvent.KEYCODE_ENTER",
    "KeyEvent.KEYCODE_NUMPAD_ENTER",
    "KeyEvent.KEYCODE_SPACE",
    "KeyEvent.KEYCODE_DPAD_CENTER",
):
    assert expected in keys, f"suggestion keyboard activation missing {expected}"

lookup = between(
    ACTIVITY,
    "    private Task findTaskById(String taskId) {",
    "    private void showSuggestedTask(String taskId) {",
)
assert "taskId.equals(task.id)" in lookup, "navigation must resolve the task from its stable ID"

navigation = between(
    ACTIVITY,
    "    private void showSuggestedTask(String taskId) {",
    "    private View findTaskRow(String taskId) {",
)
for expected in (
    "if (!storageReady || taskId == null) return;",
    "Task task = findTaskById(taskId);",
    "if (task == null) {",
    "renderSuggestions(LocalDate.now());",
    "activeFilter = TaskLogic.FILTER_ALL;",
    'searchInput.setText("");',
    'searchQuery = "";',
    "renderFilters(today);",
    "renderTaskList(today);",
    "renderTaskCount(today);",
    "taskRow.requestFocus();",
    "AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS",
    "ValueAnimator.areAnimatorsEnabled()",
    "contentScroll.smoothScrollTo(0, Math.max(0, targetY));",
    "contentScroll.scrollTo(0, Math.max(0, targetY));",
    "postDelayed(suggestionHighlightReset, 2500);",
):
    assert expected in navigation, f"suggestion navigation missing required behavior: {expected}"
for forbidden in (
    "saveTasksAsync(",
    "tasks.add(",
    "replaceTask(",
    "taskStore.",
    "browserWebView",
    "loadUrl(",
):
    assert forbidden not in navigation, f"suggestion action must remain read-only/offline: {forbidden}"

row_lookup = between(
    ACTIVITY,
    "    private View findTaskRow(String taskId) {",
    "    private View buildTaskRow(Task task) {",
)
assert "taskId.equals(row.getTag())" in row_lookup, "task row lookup must use the stable task ID"

task_row = between(
    ACTIVITY,
    "    private View buildTaskRow(Task task) {",
    "    private String dueLabel(Task task) {",
)
for expected in (
    "row.setTag(task.id);",
    "row.setFocusable(highlighted);",
    "row.setImportantForAccessibility(highlighted",
    "row.setContentDescription(highlighted",
    "row.setOnFocusChangeListener((view, hasFocus) -> {",
    "row.setBackground(taskRowBackground(highlighted));",
):
    assert expected in task_row, f"task target lacks focus/highlight support: {expected}"

destroy = between(ACTIVITY, "    protected void onDestroy() {", "    private int themeResource(int mode) {")
assert "removeCallbacks(suggestionHighlightReset)" in destroy, "temporary highlight timer must be cleaned up"

print("PASS suggestion action source contract: touch/keyboard/TalkBack activation, stable-ID targeting, safe stale-task handling, search/filter reset, visible and accessible focus, motion-aware scrolling, timed highlight, and no task/network writes")

#!/usr/bin/env python3
"""Source-level contract for Android launcher long-press shortcuts."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
android = "{http://schemas.android.com/apk/res/android}"
manifest_path = root / "app/src/main/AndroidManifest.xml"
shortcuts_path = root / "app/src/main/res/xml/shortcuts.xml"
strings_path = root / "app/src/main/res/values/strings.xml"
activity_path = root / "app/src/main/java/com/abrar/daymark/MainActivity.java"

manifest = ET.parse(manifest_path).getroot()
application = manifest.find("application")
assert application is not None, "missing application"
metadata = {
    item.get(android + "name"): item.get(android + "resource")
    for item in application.findall("meta-data")
}
assert metadata.get("android.app.shortcuts") == "@xml/shortcuts", \
    "launcher shortcut metadata must be registered on the application"

strings_root = ET.parse(strings_path).getroot()
strings = {item.get("name"): (item.text or "") for item in strings_root.findall("string")}
shortcuts_root = ET.parse(shortcuts_path).getroot()
shortcuts = shortcuts_root.findall("shortcut")
expected = {
    "new_task": ("com.abrar.daymark.action.SHORTCUT_NEW_TASK", "shortcut_new_task_short", "shortcut_new_task_long"),
    "task_details": ("com.abrar.daymark.action.SHORTCUT_TASK_DETAILS", "shortcut_task_details_short", "shortcut_task_details_long"),
    "open_browser": ("com.abrar.daymark.action.SHORTCUT_OPEN_BROWSER", "shortcut_open_browser_short", "shortcut_open_browser_long"),
}
assert len(shortcuts) == len(expected), "exactly the three supported shortcuts should be published"
seen = set()
for shortcut in shortcuts:
    shortcut_id = shortcut.get(android + "shortcutId")
    assert shortcut_id in expected, f"unexpected shortcut: {shortcut_id}"
    assert shortcut_id not in seen, f"duplicate shortcut id: {shortcut_id}"
    seen.add(shortcut_id)
    action, short_key, long_key = expected[shortcut_id]
    assert shortcut.get(android + "enabled") == "true", f"{shortcut_id} must be enabled"
    assert shortcut.get(android + "icon"), f"{shortcut_id} must have an icon"
    short_label = strings.get(short_key, "")
    long_label = strings.get(long_key, "")
    assert short_label and len(short_label) <= 10, f"{shortcut_id} short label must be 1-10 characters"
    assert long_label and len(long_label) <= 25, f"{shortcut_id} long label must be 1-25 characters"
    intent = shortcut.find("intent")
    assert intent is not None, f"{shortcut_id} must have a launch intent"
    assert intent.get(android + "action") == action, f"{shortcut_id} action mismatch"
    assert intent.get(android + "targetPackage") == "com.abrar.daymark", "shortcut package identity changed"
    assert intent.get(android + "targetClass") == "com.abrar.daymark.MainActivity", "shortcut must route to MainActivity"

activity = activity_path.read_text(encoding="utf-8")
for marker in (
    'ACTION_SHORTCUT_NEW_TASK = "com.abrar.daymark.action.SHORTCUT_NEW_TASK"',
    'ACTION_SHORTCUT_TASK_DETAILS = "com.abrar.daymark.action.SHORTCUT_TASK_DETAILS"',
    'ACTION_SHORTCUT_OPEN_BROWSER = "com.abrar.daymark.action.SHORTCUT_OPEN_BROWSER"',
    "handleShortcutIntent(getIntent());",
    "setIntent(intent);",
    "dispatchPendingLauncherShortcut();",
    "showTaskEditor(null);",
    "inputMethodManager.showSoftInput(quickCaptureInput",
):
    assert marker in activity, f"missing launcher shortcut routing contract: {marker}"

handler = activity.split("private void dispatchLauncherShortcut(String action)", 1)[1].split(
    "private void handleShareInIntent", 1
)[0]
browser = handler.split("if (ACTION_SHORTCUT_OPEN_BROWSER.equals(action))", 1)[1]
assert "setWebMode(true);" in browser, "browser shortcut must open the browser workspace"
assert "loadBrowserAddress(" not in browser and ".loadUrl(" not in browser, \
    "browser shortcut must not open a site or make a network request automatically"
assert "TaskLogic.create(" not in handler, "launcher shortcuts must not create or save tasks automatically"

pending = activity.split("private void dispatchPendingLauncherShortcut()", 1)[1].split(
    "private void dispatchLauncherShortcut", 1
)[0]
assert "if (!canEdit())" in pending and "pendingLauncherShortcutAction = null;" in pending, \
    "task shortcuts must fail closed if encrypted storage cannot be opened"

print("PASS Android launcher shortcuts: manifest registration, unique static actions, valid labels/icons, package identity, intent routing, storage-ready gating, and no automatic task creation or website navigation")

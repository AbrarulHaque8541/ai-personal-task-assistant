#!/usr/bin/env python3
"""Guard browser Downloads discoverability and its accessible overflow action."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
activity = (
    root / "app/src/main/java/com/abrar/daymark/MainActivity.java"
).read_text(encoding="utf-8")
browser_screen = activity.split("private LinearLayout buildBrowserScreen()", 1)[1].split(
    "private void selectSearchEngine(", 1
)[0]

overflow_start = activity.index("private void showBrowserOverflowMenu(View anchor)")
overflow_end = activity.index("private ", overflow_start + len("private void "))
overflow_menu = activity[overflow_start:overflow_end]
assert '"Downloads"' in overflow_menu, "Downloads must remain discoverable in browser actions"
assert 'case 8:' in overflow_menu and 'showBrowserDownloadsDialog();' in overflow_menu, \
    "the Downloads overflow action must open the existing in-app DownloadManager list"
assert 'compactButton("Downloads", false)' not in browser_screen, \
    "the redundant persistent Downloads toolbar button should stay removed"

print("PASS browser Downloads shortcut: overflow discoverability and existing status-list action")

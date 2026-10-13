#!/usr/bin/env python3
"""Guard the first-level browser Downloads shortcut and its accessible action."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
activity = (
    root / "app/src/main/java/com/abrar/daymark/MainActivity.java"
).read_text(encoding="utf-8")
browser_screen = activity.split("private LinearLayout buildBrowserScreen()", 1)[1].split(
    "private void selectSearchEngine(", 1
)[0]

assert 'compactButton("Downloads", false)' in browser_screen, \
    "Downloads should be visibly labeled rather than hidden only in the overflow menu"
assert 'setContentDescription("Open browser Downloads to view download status and progress")' in browser_screen, \
    "the Downloads shortcut must explain its purpose to assistive technology"
assert "downloadsShortcut.setOnClickListener(view -> showBrowserDownloadsDialog());" in browser_screen, \
    "the shortcut must open the existing in-app DownloadManager list"
assert "Arrays.asList(downloadsShortcut, browserBackButton" in browser_screen, \
    "Downloads must be part of the visible, scrollable browser toolbar"

print("PASS browser Downloads shortcut: first-level labeled control, accessible description, existing status-list action")

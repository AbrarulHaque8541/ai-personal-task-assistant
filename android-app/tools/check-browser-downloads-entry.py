#!/usr/bin/env python3
"""Source regression checks for the in-app browser Downloads destination (issues #211/#213)."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
menu = activity.split("private void showBrowserOverflowMenu", 1)[1].split(
    "private void importExtensionFromUri", 1
)[0]
method = activity.split("private void showBrowserDownloadsDialog()", 1)[1].split(
    "private String browserDownloadStatusLabel", 1
)[0]

assert 'showBrowserDownloadsDialog();' in menu, \
    "the browser Downloads menu must open the in-app status list"
assert "private void openDownloadsFolder()" not in activity, \
    "the obsolete generic/system downloads destination must not shadow the in-app list"
assert "manager.query(new DownloadManager.Query())" in method, \
    "the in-app list must query Android DownloadManager"
assert "No Daymark browser downloads are available yet." in method, \
    "the list must explain the empty state"
assert 'setNeutralButton("Refresh"' in method, \
    "the list must expose a refresh action"
assert "DownloadManager.ACTION_VIEW_DOWNLOADS" not in method, \
    "the status list must not silently hand off to another screen"
print("PASS browser Downloads destination: in-app DownloadManager list, explicit empty state and refresh")

# Work by: ChatGPT
# Model: GPT-6
# Tooling: GitHub MCP tools
# Timestamp (UTC): 2026-10-09T17:44:30Z

#!/usr/bin/env python3
"""Source regression checks for the browser Downloads destination (issue #213)."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
method = activity.split("private void openDownloadsFolder()", 1)[1].split(
    "private void queueBrowserDownload", 1
)[0]

assert "DownloadManager.ACTION_VIEW_DOWNLOADS" in method, \
    "the Downloads menu should open the system download list first"
assert "Intent.ACTION_OPEN_DOCUMENT" in method, \
    "a document picker may only be used as an explicit fallback"
assert method.index("DownloadManager.ACTION_VIEW_DOWNLOADS") < method.index("Intent.ACTION_OPEN_DOCUMENT"), \
    "the file picker must not replace the primary Downloads destination"
assert "Use Files to locate downloaded items." in method, \
    "fallback behavior must be disclosed"
assert "No Downloads app or file picker is available on this device." in method, \
    "missing system handlers must produce a useful message"
print("PASS browser Downloads destination: system list first, explicit picker fallback, actionable errors")

# Work by: ChatGPT
# Model: GPT-6
# Tooling: GitHub MCP tools
# Timestamp (UTC): 2026-10-09T17:31:45.598Z

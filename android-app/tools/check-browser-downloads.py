#!/usr/bin/env python3
"""Source regression checks for the DownloadManager-backed browser downloads view (issue #211)."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
method = activity.split("private void showBrowserDownloadsDialog()", 1)[1].split(
    "private void queueBrowserDownload(", 1
)[0]

for marker, explanation in (
    ("manager.query(new DownloadManager.Query())", "Downloads must be queried from the system provider"),
    ("DownloadManager.COLUMN_STATUS", "the list must read provider status"),
    ("DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR", "progress must use provider byte counts"),
    ("DownloadManager.COLUMN_TOTAL_SIZE_BYTES", "progress must account for known total size"),
    ("DownloadManager.STATUS_PENDING", "queued downloads need a visible status"),
    ("DownloadManager.STATUS_RUNNING", "running downloads need a visible status"),
    ("DownloadManager.STATUS_PAUSED", "paused downloads need a visible status"),
    ("DownloadManager.STATUS_SUCCESSFUL", "completed downloads need an open action"),
    ("DownloadManager.STATUS_FAILED", "failed downloads need an honest state"),
    ("manager.getUriForDownloadedFile(id)", "completed files must use the provider content URI"),
    ("Intent.FLAG_GRANT_READ_URI_PERMISSION", "file handoff must grant read-only access"),
    ("Intent.ACTION_VIEW", "completed files should open only after a user tap"),
    ("BrowserAddress.isAllowedWebUrl(url)", "retry must validate HTTPS before making a new request"),
    ('setPositiveButton("Retry"', "retry must require explicit user confirmation"),
    ("android.webkit.WebSettings.getDefaultUserAgent(this)", "retry must not inherit an unrelated tab's user agent"),
    ('setNeutralButton("Refresh"', "the list must offer refresh"),
):
    assert marker in method, explanation

assert "showBrowserDownloadsDialog();" in activity, "the overflow Downloads action must open the download list"
assert "openDownloadsFolder()" not in activity, "Downloads must not be a generic document picker"
assert "ACTION_OPEN_DOCUMENT" not in method, "the downloads list must not be implemented as a generic file picker"
assert "No Daymark browser downloads are available yet." in method, "empty state must be explicit"
assert "No compatible app could open this file." in method, "viewer handoff failure must be disclosed"
print("PASS browser downloads source checks: status/progress, confirmed HTTPS retry, safe file handoff, refresh and empty/error states")

# Work by: ChatGPT
# Model: GPT-6
# Tooling: GitHub MCP tools
# Timestamp (UTC): 2026-10-09T17:30:04Z

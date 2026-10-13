#!/usr/bin/env python3
"""Source regression checks for the user-triggered browser diagnostics mode (P1 #7, issue #221 support)."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/abrar/daymark/MainActivity.java").read_text(encoding="utf-8")
diagnostics = (root / "app/src/main/java/com/abrar/daymark/BrowserDiagnostics.java").read_text(encoding="utf-8")
smoke = (root / "tools/BrowserDiagnosticsSmoke.java").read_text(encoding="utf-8")

menu = activity.split("private void showBrowserOverflowMenu", 1)[1].split(
    "private void importExtensionFromUri", 1
)[0]
assert '"Diagnostics"' in menu and "showBrowserDiagnosticsDialog()" in menu, \
    "diagnostics must be discoverable from the browser overflow menu"
assert "new BrowserDiagnostics()" in activity, "MainActivity must own a diagnostics recorder"
assert activity.count("browserDiagnostics.record(") >= 10, \
    "navigation lifecycle events (navigate/load/started/finished/blocked/error/renderer/watchdog) must be recorded"
for expected in ("navigate requested", "load requested", "page started", "page finished",
                 "load failed", "navigation blocked", "renderer gone", "watchdog"):
    assert expected in activity, f"missing lifecycle stage recording: {expected}"
assert "WebView.getCurrentWebViewPackage()" in activity, \
    "the report must include the WebView provider version (top blank-page hypothesis for #221)"
assert "Build.VERSION.SDK_INT" in activity and "Build.MODEL" in activity, \
    "the report must include device/API context for issue reports"
assert '"Copy report"' in activity, "export must be an explicit opt-in Copy action"
assert "never sent automatically" in activity and "no task data" in activity, \
    "the dialog must disclose that the report is local-only and task-free"

# Redaction and bounds live in the pure recorder class and are host-tested.
for expected in ("redactUrl", "MAX_EVENTS", "indexOf('?')", "indexOf('#')", "indexOf('@')", "indexOf('/')"):
    assert expected in diagnostics, f"diagnostics recorder must implement redaction/bounds: {expected}"
for forbidden in ("SharedPreferences", "java.net", "Intent", "ContentResolver", "openOutputStream"):
    assert forbidden not in diagnostics, f"the recorder must stay in-memory and side-effect-free: {forbidden}"
for expected in ("credentials, query, and fragment must be redacted", "buffer must stay bounded",
                 "report must never contain query strings", "events() must return a defensive copy",
                 "an @ inside the path is not a credential"):
    assert expected in smoke, f"missing executable diagnostics regression: {expected}"
print("PASS browser diagnostics: user-triggered only, redacted URLs, bounded in-memory events, opt-in copy export")

# Work by: Vibe
# Model: GLM (glm-5-latest-short)
# Tooling: GitHub MCP tools
# Timestamp (UTC): 2026-10-11T17:55:00Z

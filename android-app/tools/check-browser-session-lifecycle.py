#!/usr/bin/env python3
"""Source regression guard for retaining WebView sessions across Activity pause/resume."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
source = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")

pause_start = source.find("protected void onPause()")
pause_end = source.find("private boolean isActivityCallbackCurrent()", pause_start)
assert pause_start >= 0 and pause_end > pause_start, "Activity onPause lifecycle method must exist"
on_pause = source[pause_start:pause_end]
assert "discardBrowserWebView();" not in on_pause, "backgrounding must not destroy the browser session"
assert "for (DaymarkWebView tab : new ArrayList<>(browserTabs))" in on_pause, "all live tabs must be paused"
assert "tab.onPause();" in on_pause and "browserWebView.pauseTimers();" in on_pause, "WebView rendering and timers must pause in background"

resume_start = source.find("protected void onResume()")
resume_end = source.find("@Override\n    @SuppressWarnings(\"deprecation\")", resume_start)
assert resume_start >= 0 and resume_end > resume_start, "Activity onResume lifecycle method must exist"
on_resume = source[resume_start:resume_end]
assert "browserWebView.resumeTimers();" in on_resume, "WebView timers must resume"
assert "tab.onResume();" in on_resume, "retained tabs must resume"
assert "if (browserPageLoading" in on_resume and "scheduleBrowserLoadWatchdog(lastBrowserAddress);" in on_resume, (
    "an interrupted in-flight page load must regain its bounded watchdog after resume"
)

assert "private boolean browserPageLoading;" in source, "loading state must be tracked across lifecycle"
page_started = source.find("@Override public void onPageStarted(String url)")
page_finished = source.find("@Override public void onPageFinished(String url)", page_started)
load_error = source.find("@Override public void onLoadError(int errorCode", page_finished)
assert page_started >= 0 and page_finished > page_started and load_error > page_finished, "browser lifecycle callbacks must exist"
assert "browserPageLoading = true;" in source[page_started:page_finished], "page start must mark navigation in flight"
assert "browserPageLoading = false;" in source[page_finished:load_error], "successful page finish must clear in-flight state"
assert "browserPageLoading = false;" in source[load_error:load_error + 700], "load errors must clear in-flight state"

print("PASS WebView session lifecycle: background pause retains tabs; resume restores timers and bounded watchdog")

#!/usr/bin/env python3
"""Source regression guard for the WebView page-finish watchdog lifecycle."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
activity_path = root / "app/src/main/java/com/cue/daymark/MainActivity.java"
source = activity_path.read_text(encoding="utf-8")

start = source.find("@Override public void onPageStarted(String url)")
end = source.find("@Override public void onPageFinished(String url)", start)
assert start >= 0 and end > start, "browser listener must retain page-start and page-finish callbacks"
page_started = source[start:end]

assert "cancelBrowserLoadWatchdog();" in page_started, (
    "page-start callback should cancel the navigation watchdog before resetting it"
)
assert "scheduleBrowserLoadWatchdog(url);" in page_started, (
    "page-start callback must re-arm the watchdog so a page that starts but never finishes "
    "cannot leave the viewport stuck indefinitely"
)
assert page_started.index("cancelBrowserLoadWatchdog();") < page_started.index(
    "scheduleBrowserLoadWatchdog(url);"
), "watchdog must be reset, not stacked, when page-start callback arrives"

watchdog_start = source.find("private void scheduleBrowserLoadWatchdog(String address)")
watchdog_end = source.find("private void cancelBrowserLoadWatchdog()", watchdog_start)
assert watchdog_start >= 0 and watchdog_end > watchdog_start, "bounded watchdog implementation must exist"
watchdog = source[watchdog_start:watchdog_end]
assert "browserSelfHealingPolicy.shouldRecover(address)" in watchdog, (
    "automatic retry must remain bounded by the recovery policy"
)
assert "browserLoadFailed = true;" in watchdog, "watchdog exhaustion must expose retryable failure state"

print("PASS browser watchdog lifecycle: page-start re-arms finish timeout; automatic recovery remains bounded")

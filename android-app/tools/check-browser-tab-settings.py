#!/usr/bin/env python3
"""Source regression guards for browser settings and site-data actions across tabs."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")

def section(start: str, end: str) -> str:
    i = SOURCE.index(start)
    j = SOURCE.index(end, i + len(start))
    return SOURCE[i:j]

safe = section("private boolean setBrowserSafeBrowsingEnabled", "private boolean setBrowserImagesBlocked")
assert "new ArrayList<>(browserTabs)" in safe, "Safe Browsing must snapshot every open tab"
assert "tabsToUpdate.add(browserWebView)" in safe, "active WebView must be covered even during tab-list transitions"
assert "for (DaymarkWebView tab : tabsToUpdate)" in safe, "Safe Browsing must apply to every open tab"
assert "rollback.getSettings().setSafeBrowsingEnabled(previous)" in safe, "partial Safe Browsing updates must roll back"
assert "browserPreferences.edit().putBoolean(SAFE_BROWSING_ENABLED_KEY, enabled).apply()" in safe, "persist only after all tabs update"

clear = section("private void clearBrowserData()", "private void addSuggestionCard")
assert "new ArrayList<>(browserTabs)" in clear, "clear-site-data must snapshot every open tab"
assert "openTabs.add(browserWebView)" in clear, "active WebView must be covered during tab-list transitions"
assert "for (DaymarkWebView tab : openTabs)" in clear, "clear-site-data must visit every open tab"
for method in ("clearHistory()", "clearCache(true)", "clearFormData()", "clearSslPreferences()"):
    assert f"tab.{method}" in clear, f"every tab must receive {method}"
assert "allTabDataCleared" in clear and "one or more open tabs could not clear" in clear, "partial failure must not be reported as full success"
assert "browserWebView.clearHistory()" not in clear, "avoid clearing only the active WebView"
assert "private boolean clearBrowserTabData(DaymarkWebView tab)" in clear, "each tab should use a best-effort cleanup helper"
assert clear.count("catch (RuntimeException clearFailed)") >= 4, "each WebView cleanup operation must fail independently"
assert "siteStorageCleared" in clear and "catch (RuntimeException storageClearFailed)" in clear, "WebStorage failures must be handled"
assert "cookiesFlushed" in clear and "catch (RuntimeException flushFailed)" in clear, "cookie flush failures must be reported"
assert "catch (RuntimeException cookieClearFailed)" in clear, "synchronous cookie-clear failures must not strand the UI"
assert "Site-data cleanup was partial" in clear, "partial global cleanup must not be reported as full success"
print("PASS browser multi-tab source checks: Safe Browsing transaction, all-tab data clearing, and honest partial-failure UI")

#!/usr/bin/env python3
"""Apply WebView session-keep to MainActivity.java (idempotent)."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
ma_path = root / "app/src/main/java/com/cue/daymark/MainActivity.java"
guard_path = root / "tools/check-v1-source.sh"
ma = ma_path.read_text(encoding="utf-8")
if "BrowserSessionController.pauseAll" in ma:
    print("MainActivity already session-keep")
else:
    old = '''            Log.i(BROWSER_LOG_TAG, "backgrounded; page discarded: " + lastBrowserAddress);
            discardBrowserWebView();
            if (browserHomeView != null) browserHomeView.setVisibility(View.VISIBLE);
            if (browserStatus != null) {
                browserStatus.setText(browserNetworkPolicy.isOnlineEnabled()
                        ? "Daymark went into the background. The page will reopen when you return to Daymark."
                        : "Daymark went into the background. Offline; WebView network loads are blocked.");'''
    new = '''            Log.i(BROWSER_LOG_TAG, "backgrounded; pausing WebView (not discarded): " + lastBrowserAddress);
            BrowserSessionController.pauseAll(browserTabs);
            if (browserStatus != null) {
                browserStatus.setText(browserNetworkPolicy != null && browserNetworkPolicy.isOnlineEnabled()
                        ? "In background — page kept; returns faster when you come back."
                        : "In background — offline; WebView network loads stay blocked.");'''
    if old not in ma:
        sys.exit("onPause pattern not found")
    ma = ma.replace(old, new, 1)
    old_r = '''        if (webMode && browserWebView == null && lastBrowserAddress != null
                && browserNetworkPolicy != null && browserNetworkPolicy.allowsRemoteLoads()) {
            final String restoreAddress = lastBrowserAddress;
            Log.i(BROWSER_LOG_TAG, "resumed; restoring last page: " + restoreAddress);
            postActivityCallback(() -> navigateBrowserTo(restoreAddress));
        }'''
    new_r = '''        if (browserWebView != null) {
            BrowserSessionController.resumeAll(browserTabs);
            Log.i(BROWSER_LOG_TAG, "resumed; WebView timers resumed");
        } else if (webMode && lastBrowserAddress != null
                && browserNetworkPolicy != null && browserNetworkPolicy.allowsRemoteLoads()) {
            final String restoreAddress = lastBrowserAddress;
            Log.i(BROWSER_LOG_TAG, "resumed; restoring last page: " + restoreAddress);
            postActivityCallback(() -> navigateBrowserTo(restoreAddress));
        }'''
    if old_r not in ma:
        sys.exit("onResume pattern not found")
    ma = ma.replace(old_r, new_r, 1)
    ma_path.write_text(ma, encoding="utf-8")
    print("MainActivity patched")

g = guard_path.read_text(encoding="utf-8")
old_g = 'assert on_pause and "discardBrowserWebView()" in on_pause.group(1), "backgrounding must close the page"'
new_g = 'assert on_pause and ("pauseTimers()" in on_pause.group(1) or "BrowserSessionController.pauseAll" in on_pause.group(1) or "discardBrowserWebView()" in on_pause.group(1)), "backgrounding must pause or close the WebView"'
if old_g in g:
    guard_path.write_text(g.replace(old_g, new_g, 1), encoding="utf-8")
    print("guard patched")
elif "BrowserSessionController.pauseAll" in g or "pauseTimers()" in g:
    print("guard already allows pause")
else:
    sys.exit("guard pattern not found")

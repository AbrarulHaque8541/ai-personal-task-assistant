#!/usr/bin/env python3
"""Idempotent MainActivity + guard patch: keep WebView across onPause."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
ma_path = root / "app/src/main/java/com/cue/daymark/MainActivity.java"
guard_path = root / "tools/check-v1-source.sh"
if not ma_path.is_file():
    sys.exit(f"missing {ma_path}")

ma = ma_path.read_text(encoding="utf-8")
if "BrowserSessionController.pauseAll" in ma:
    print("MainActivity already session-keep")
else:
    old = (
        '            Log.i(BROWSER_LOG_TAG, "backgrounded; page discarded: " + lastBrowserAddress);\n'
        '            discardBrowserWebView();\n'
        '            if (browserHomeView != null) browserHomeView.setVisibility(View.VISIBLE);\n'
        '            if (browserStatus != null) {\n'
        '                browserStatus.setText(browserNetworkPolicy.isOnlineEnabled()\n'
        '                        ? "Daymark went into the background. The page will reopen when you return to Daymark."\n'
        '                        : "Daymark went into the background. Offline; WebView network loads are blocked.");'
    )
    new = (
        '            Log.i(BROWSER_LOG_TAG, "backgrounded; pausing WebView (not discarded): " + lastBrowserAddress);\n'
        '            BrowserSessionController.pauseAll(browserTabs);\n'
        '            if (browserStatus != null) {\n'
        '                browserStatus.setText(browserNetworkPolicy != null && browserNetworkPolicy.isOnlineEnabled()\n'
        '                        ? "In background - page kept; returns faster when you come back."\n'
        '                        : "In background - offline; WebView network loads stay blocked.");'
    )
    if old not in ma:
        sys.exit("onPause pattern not found in MainActivity")
    ma = ma.replace(old, new, 1)
    old_r = (
        '        if (webMode && browserWebView == null && lastBrowserAddress != null\n'
        '                && browserNetworkPolicy != null && browserNetworkPolicy.allowsRemoteLoads()) {\n'
        '            final String restoreAddress = lastBrowserAddress;\n'
        '            Log.i(BROWSER_LOG_TAG, "resumed; restoring last page: " + restoreAddress);\n'
        '            postActivityCallback(() -> navigateBrowserTo(restoreAddress));\n'
        '        }'
    )
    new_r = (
        '        if (browserWebView != null) {\n'
        '            BrowserSessionController.resumeAll(browserTabs);\n'
        '            Log.i(BROWSER_LOG_TAG, "resumed; WebView timers resumed");\n'
        '        } else if (webMode && lastBrowserAddress != null\n'
        '                && browserNetworkPolicy != null && browserNetworkPolicy.allowsRemoteLoads()) {\n'
        '            final String restoreAddress = lastBrowserAddress;\n'
        '            Log.i(BROWSER_LOG_TAG, "resumed; restoring last page: " + restoreAddress);\n'
        '            postActivityCallback(() -> navigateBrowserTo(restoreAddress));\n'
        '        }'
    )
    if old_r not in ma:
        sys.exit("onResume pattern not found in MainActivity")
    ma = ma.replace(old_r, new_r, 1)
    ma_path.write_text(ma, encoding="utf-8")
    print("MainActivity patched for session-keep")

if guard_path.is_file():
    g = guard_path.read_text(encoding="utf-8")
    old_g = 'assert on_pause and "discardBrowserWebView()" in on_pause.group(1), "backgrounding must close the page"'
    new_g = (
        'assert on_pause and ("pauseTimers()" in on_pause.group(1) or '
        '"BrowserSessionController.pauseAll" in on_pause.group(1) or '
        '"discardBrowserWebView()" in on_pause.group(1)), '
        '"backgrounding must pause or close the WebView"'
    )
    if old_g in g:
        guard_path.write_text(g.replace(old_g, new_g, 1), encoding="utf-8")
        print("guard patched")
    else:
        print("guard already allows pause or pattern absent")

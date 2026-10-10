# Performance: keep WebView session across background (2026-10-10)

## Problem
`MainActivity.onPause()` called `discardBrowserWebView()`, destroying every tab.
Resume always cold-started `loadUrl` again → slow, blank risk, wasted network.

## Fix (apply to MainActivity)
1. **onPause**: for each tab call `onPause()` + `pauseTimers()`; keep instance; do not discard.
2. **onResume**: if WebView alive, `resumeTimers()` + `onResume()`; else restore URL as today.
3. **onDestroy** still discards (unchanged).
4. **check-v1-source.sh** assertion: accept `pauseTimers()` OR `discardBrowserWebView()` in onPause.

## DaymarkWebView (this PR branch)
- `LOAD_DEFAULT` cache
- `setLoadsImagesAutomatically(true)`
- `LAYER_TYPE_HARDWARE`

## Device verify
Background Daymark for 10s while on a page → return → page should appear without full reload spinner (or much faster).

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-10T15:05:00Z

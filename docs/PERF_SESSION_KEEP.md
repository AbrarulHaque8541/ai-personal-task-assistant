# Performance: keep WebView session across background

## Highest-impact browser performance fix

Before: every `onPause` called `discardBrowserWebView()` → full destroy → resume cold `loadUrl`.
After: `onPause`/`pauseTimers` keep the instance; `onResume`/`resumeTimers` restore instantly.

## Apply
```bash
cd android-app && patch -p2 < ../patches/perf-webview-session-keep-20261010.patch
# paths: patch is written against MainActivity.java at repo root style — use:
patch -p1 < patches/perf-webview-session-keep-20261010.patch
```

Also update `check-v1-source.sh`:
```
assert on_pause and ("pauseTimers()" in on_pause.group(1) or "discardBrowserWebView()" in on_pause.group(1)), "backgrounding must pause or close the WebView"
```

`DaymarkWebView` on this branch already sets LOAD_DEFAULT, auto images, hardware layer.

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-10T15:06:00Z

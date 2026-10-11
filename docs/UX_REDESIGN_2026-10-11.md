# UX redesign slice — 2026-10-11

## Owner complaints addressed
1. Task home: too much fixed top chrome / half screen stuck → compact heading, single-line privacy, tighter stats.
2. Browser: noisy status / clutter feel → empty status until action; Reader stays in ⋮ only (not a permanent mid-page control).
3. Smoothness → mode transition 220ms + DecelerateInterpolator; chrome hide 160ms decelerate.
4. Performance branch separate: hardware WebView + list gate + search debounce still on `perf/boost-tasklist-webview-*`.

## Apply
CI / local: `python3 android-app/tools/apply-home-browser-redesign.py android-app`

## Not in this slice (follow-ups)
- Full glassmorphism system palette
- Bookmarks / tags / NL add
- Bottom nav Templates tab removal (guarded)
- Device visual QA by owner

DEVICE VERIFIED: no

---
Work by: Grok
Model: Grok 4.5 (xAI)
Timestamp (UTC): 2026-10-11T17:20:00Z

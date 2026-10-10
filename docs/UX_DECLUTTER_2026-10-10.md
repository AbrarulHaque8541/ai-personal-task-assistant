# UX declutter (composer) — 2026-10-10

Addresses **#225** (and partially **#222** chrome density on Tasks home).

## Changes (MainActivity)
1. Stop showing **DEMO SUGGESTION** card on home (`addSuggestionCard` call commented).
2. Stop attaching **Today / Tomorrow / High / Details** smart-chip row to the composer.
3. Remove **Add with a date or priority** secondary button (redundant with **Add task** → full editor).
4. Keep **Task templates** button.

## Apply
```bash
patch -p1 < patches/ux-declutter-composer-20261010.patch
```

## Not claimed
- Full browser chrome rebuild (#222) not complete in this patch.
- Device blank-page (#221) needs owner retest after WebView update + latest APK.
- OS reminders (#226) tracked in PR #231 (CI encoding fixes pushed separately).

SOURCE-VERIFIED for this patch. DEVICE VERIFIED: no.

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-10T09:55:00Z

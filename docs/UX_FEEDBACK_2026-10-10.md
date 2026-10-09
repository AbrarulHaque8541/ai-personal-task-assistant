# UX feedback (2026-10-10) — status

## Browser (screenshot)

| Issue | Status |
|-------|--------|
| Blank page after search | **Fix on branch:** Chrome mobile User-Agent in `DaymarkWebView` |
| Keyboard Enter should search | **Patch:** address-bar Enter → navigate |
| Big Reader mode box | **Patch:** hide primary Reader row; use overflow |
| Cluttered Tasks / back / forward / Stop | Still needs chrome simplify pass |
| Search engine control size | Follow-up: shrink; show mainly pre-navigation |
| Bottom provider chips cut off | Layout follow-up after Reader row hidden |

## Tasks home (screenshot)

| Issue | Status |
|-------|--------|
| Today/Tomorrow/High/Details always visible | **Patch:** collapse under one toggle |
| Templates / secondary rows clutter | Follow-up: move into editor / toggle |
| Reminders, time-of-day, ringtone | **Not implemented** — backlog (needs alarm permission design) |
| Task/Web tabs large | Follow-up: slightly smaller tabs |
| Date “SATURDAY, OCT 10” | Uses device local date; at 01:43 IST on Oct 10 it **is** Saturday |

## More menu

Contextual Tasks vs Browser More is tracked separately (`feat/contextual-more-about-20261010`).

## Honesty

Physical-device verification of blank-page fix: **NOT TESTED** in this environment.

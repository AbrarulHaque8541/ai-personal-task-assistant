# Daymark performance roadmap

First dedicated performance track (2026-10-10). Goal: keep Tasks + embedded browser responsive as features grow.

## Principles
- Measure before claiming wins (Systrace / Macrobenchmark / simple wall-clock).
- Prefer Android System WebView knobs that do not weaken HTTPS / privacy policy.
- Avoid UI thrash: no full Activity rebuild for small state changes.
- Cache aggressively where safe; never skip security checks for speed.

## Shipped in this branch
1. **WebView HTTP cache** — `WebSettings.LOAD_DEFAULT` so repeat navigations reuse disk cache.
2. **Automatic images** — `setLoadsImagesAutomatically(true)` (still gated by network policy).
3. **Hardware layer** — `LAYER_TYPE_HARDWARE` on `DaymarkWebView` for smoother scroll.

## Next multi-purpose work packages (implement as focused PRs, not spam)
| ID | Area | Work |
|----|------|------|
| P01 | Browser | Pause/resume timers; avoid work while WebView not visible |
| P02 | Browser | Cap concurrent `evaluateJavascript` / extension inject cost |
| P03 | Browser | Optional image block toggle already exists — document hot path |
| P04 | Browser | Download path: prefer DownloadManager; no main-thread IO |
| P05 | Tasks | Incremental task list update (diff) instead of full rebuild |
| P06 | Tasks | Debounce search/filter typing |
| P07 | Tasks | Lazy-build off-screen sections |
| P08 | Startup | Defer non-critical UI (templates, suggestion ranking) |
| P09 | Storage | Profile encrypt/decrypt path; avoid double-decode |
| P10 | Memory | Bound WebView tab count (policy already exists) — enforce aggressively |
| P11 | Main thread | Audit `MainActivity` for heavy work on UI thread |
| P12 | Scroll | Recycler-style recycling if task list grows large |

## Explicit non-goals
- Shipping **hundreds of empty GitHub issues/PRs** (noise, CI load, agent confusion).
- Weakening Safe Browsing / mixed-content / file-access blocks for speed.
- Claiming device FPS without measurement.

## How to extend
One PR per package above, with: before/after note, guard updates if needed, CI green.

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-10T14:57:00Z

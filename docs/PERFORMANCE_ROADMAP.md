# Daymark performance roadmap

First dedicated performance track began 2026-10-10. Revalidated 2026-10-13T18:08:39Z against live `main` `a36bf7467b5cbe4c0b5162cec68412680447c33d`; this supersedes the earlier `43fbe...` source snapshot. Goal: keep Tasks + embedded browser responsive as features grow, without claiming unmeasured gains.

## Principles
- Measure before claiming wins (Systrace / Macrobenchmark / simple wall-clock).
- Prefer Android System WebView knobs that do not weaken HTTPS / privacy policy.
- Avoid UI thrash; skip unnecessary work, but do not equate a render gate with row-level diffing.
- Cache aggressively where safe; never skip security checks for speed.

## Present in current main source
1. **WebView HTTP cache** — `WebSettings.LOAD_DEFAULT` allows normal cache behavior.
2. **Automatic images** — `setLoadsImagesAutomatically(true)` remains gated by network policy.
3. **Hardware layer + offscreen pre-raster** — configured on the Daymark WebView. This is source state only; GPU memory pressure, blank viewport, and device-specific rendering still need physical-device testing.
4. **Pause/resume lifecycle** — WebView timers pause while not visible and resume with the session.
5. **Task list render gate** — skips rebuilding for identical list state; updates still rebuild rather than diff individual rows.
6. **Task search debounce** — a 150 ms debounce is present before task-list filtering/render.
7. **Network-image preference** — user-controlled and persisted; this is a data-saving option, not an ad/tracker blocker.

## Validation status
- The current-main `build` check passed on exact SHA `a36bf7467b5cbe4c0b5162cec68412680447c33d`: https://github.com/AbrarulHaque8541/ai-personal-task-assistant/actions/runs/38438713728. This is CI evidence only, not device-performance measurement.
- Source configuration and CI do not establish physical-device FPS, memory, thermal behavior, blank-WebView recovery, or visual correctness. No device performance measurements are recorded here.
- Keep #221 (blank viewport), #222 (browser UX/device acceptance), and #160 (device matrix) open until real-device evidence is recorded.

## Remaining work packages
| ID | Area | Work | Status |
|----|------|------|--------|
| P01 | Browser | Pause/resume timers; avoid work while WebView is not visible | Source present; device measurement pending |
| P02 | Browser | Cap concurrent `evaluateJavascript` / extension-injection cost | Open |
| P03 | Browser | Document image-blocking hot path | Source option present; no network-performance claim |
| P04 | Browser | Download path: prefer DownloadManager; avoid main-thread I/O | Review/measurement pending |
| P05 | Tasks | Incremental row diff instead of full rebuild on changed content | Open; render gate only skips identical state |
| P06 | Tasks | Debounce search/filter typing | Source present (150 ms); device measurement pending |
| P07 | Tasks | Lazy-build off-screen sections | Open |
| P08 | Startup | Defer non-critical UI (templates, suggestion ranking) | Open |
| P09 | Storage | Profile encrypt/decrypt path; avoid double-decode | Open |
| P10 | Memory | Enforce a bounded WebView tab count | Policy exists; profile real-device memory |
| P11 | Main thread | Audit `MainActivity` for heavy work on UI thread | Open |
| P12 | Scroll | Recycler-style recycling if task list grows large | Open; measure before adding complexity |

## Explicit non-goals
- Shipping **hundreds of empty GitHub issues/PRs** (noise, CI load, agent confusion).
- Weakening Safe Browsing / mixed-content / file-access blocks for speed.
- Claiming device FPS without measurement.

## How to extend
One focused PR per package, with before/after evidence, necessary source-guard updates, and green CI. Preserve issue and release gates; do not merge or publish on the basis of this roadmap.

---
Original roadmap authorship:
Work by: ChatGPT
Model: GPT-6
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-10T15:15:04Z

Current-source refresh:
Work by: Cue
Model: not disclosed by runtime
Tooling: GitHub connector CLI and local source review
Timestamp (UTC): 2026-10-13T18:08:39Z

# Task home vs Browser — settings ownership + competitor gaps

Date: 2026-10-11 snapshot. Honest audit against live main + external product research; source-status corrections refreshed 2026-10-13.
Audience: owner + agents. Not a release claim.

**Current-source correction (2026-10-13):** Task tags are available in the editor, visible task summary, and search; a dedicated tag-filter-chip UI is not present. Natural-language quick add (supported dates/times, priorities, and `#tags`) later merged in [PR #292](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/292). Current browser source also has searchable/filterable tabs and local HTTPS-origin bookmarks; Browser Settings owns the default search engine, site history/data, bookmarks, extensions, image loading, Safe Browsing, and desktop-site preference. These later features supersede matching 2026-10-11 gaps below. The settings-ownership section and ordered work are historical proposals, not today's backlog; consult [`../PROJECT_PLAN.md`](../PROJECT_PLAN.md) and live issues [#221](https://github.com/AbrarulHaque8541/daymark/issues/221) / [#222](https://github.com/AbrarulHaque8541/daymark/issues/222) before proposing work. Quick-add formats are listed in [`NL_QUICK_ADD.md`](NL_QUICK_ADD.md).

## 1. What improved recently (main)

### Task / home
- Reminders path + OS notification wiring (device proof still open).
- Composer declutter (less chip noise on add).
- Task list still full rebuild on filter change (skip-rebuild gate on branch, not merged).

### Browser
- Session keep on background (`onPause` pauseTimers, not destroy).
- HTTP disk cache `LOAD_DEFAULT`.
- Chrome cohesion (#248): bottom control row, disclosure once, engine chip auto-hide.
- Overflow: find, reader, share, copy URL, external, desktop, downloads, extensions, history, privacy, settings.

### Whole-app performance (honest)
| Area | On main | Branch not merged |
|------|---------|-------------------|
| WebView pause/resume session | Yes | — |
| HTTP cache | Yes | — |
| Hardware layer + offscreenPreRaster | No | `perf/boost-tasklist-webview-*` |
| Task list skip identical rebuild | No | same branch |
| Search 150ms debounce | No | same branch |
| Less chrome views | Yes (#248) | — |

No FPS/device benchmarks. Do not claim "200x".

## 2. Settings ownership (2026-10-11 snapshot; later source supersedes it)

### Desired rule
- **Task page More** → only task + app-shell data (theme, text size, backup, reminders defaults, filters help).
- **Browser ⋮ / Browser settings** → only browser (safe browsing, images, desktop default, download folder UX, extensions, history clear, engine defaults).

### Current on the 2026-10-11 snapshot (historical)
`showSettingsDialog` (global More) includes:
- PERSONALIZE: appearance, text size, high contrast ✓ (app-shell)
- DATA: accessibility, permissions, encrypted backup ✓
- POWER: **Browser & extensions** (cross-page) + Check for updates ✓ updater is app-shell

`showBrowserSettingsDialog` is thin:
- Load images toggle
- Safe Browsing toggle
- Missing many browser-standard controls (see §3)

**Gap at that snapshot; superseded by the 2026-10-13 source correction above.**

### Fix direction (implement in code PRs, not this doc alone)
1. Task More: remove "Browser & extensions" row; keep updater + backup + appearance.
2. Browser settings expand: default search engine, clear site data, clear history, desktop-by-default, download prompt, open external always, JavaScript note (policy), image toggle (exists), Safe Browsing (exists).
3. Contextual More already documented in `docs/CONTEXTUAL_MORE.md` — enforce in UI.

## 3. Browser gaps vs multi-browser research

References used for *why multiple browsers exist*: Chrome, Firefox, Brave, Samsung Internet, Edge, DuckDuckGo, Vivaldi, Opera (2025–2026 roundups).

| Capability | Daymark | Gap |
|------------|---------|-----|
| Multi-engine search switch | Yes | — |
| Tabs | Yes — filterable/searchable | Tab groups are not claimed here |
| Find in page / Reader / Desktop | Yes | — |
| Downloads UI | Partial | Queue + retry + open folder polish |
| History | Site history | Full typed history + search |
| Bookmarks / favorites | Local HTTPS-origin bookmarks | Full-page saving and sync are separate, unclaimed capabilities |
| Private / secret tab | No | Optional session-only tab |
| Ad/tracker block | No (WebView limit) | Cosmetic userscripts only; not Brave Shields |
| Real extensions | Page-local / userscript | Not Firefox-class add-ons |
| Bottom chrome | Improved #248 | Good direction |
| Default search engine setting | Yes | — |
| Password manager | No (by design) | Keep out unless owner asks |
| Sync account | No (by design) | Keep out |
| Collections / research save | No | **Research→task** is better fit |
| Full-screen / media | Partial | OK |
| Clear data per site | App-wide clear-site-data action | Per-site deletion is not claimed; see the current Android README for scope |

## 4. Task app gaps vs Tasks.org / offline managers

| Capability | Daymark | Gap |
|------------|---------|-----|
| Local encrypted store | Strong | — |
| Due + time + priority + notes + subtasks | Yes | — |
| Reminders + repeat | Implemented in source; notifications are best-effort | Device proof open |
| Tags / labels | Yes — editor, task summary, and search | Dedicated filter chips missing |
| Natural-language quick add | Implemented in source after this snapshot (PR #292) | Device UX acceptance remains separate |
| Global search (title+notes) | Filter search | Notes depth |
| Templates | Yes | — |
| Attachments | Yes | — |
| Portable backup | Yes | Scheduled backup missing |
| Share-in create task | Weak | Android Share target |
| Widgets | No | Optional later |
| Calendar view | No | Optional |
| Drag reorder | No | Nice-to-have |

## 5. Historical suggestions (2026-10-11; not today's execution queue)

**A — Separation + browser settings completeness**
- Split More menus strictly.
- Reassess only currently documented settings gaps; origin bookmarks and default-search controls are already present in current source.

**B — Task home density**
- Dedicated tag-filter chips and global search depth.
- Merge perf branch (hardware WebView + list gate + search debounce).

**C — Research loop**
- Browser selection → Add as task.
- Optional saved-page/research capture is a separate proposal, not a missing origin-bookmark feature; confirm owner scope first.

## 6. Non-goals (keep)
- Cloud sync / accounts
- Claiming full Chrome/Firefox extension parity without engine change
- Fake ad-block that cannot work inside stock WebView policy

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP + web research
Timestamp (UTC): 2026-10-11T17:05:00Z

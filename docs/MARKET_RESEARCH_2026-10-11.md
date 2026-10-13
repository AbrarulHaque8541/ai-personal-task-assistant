# Daymark market research — 2026-10-11

Scope: personal **encrypted offline tasks** + **in-app research browser**. Not team SaaS, not Play-first growth hacks.
Sources: 2025–2026 roundups (Tasks.org, Super Productivity, TickTick, Todoist, Trayzero privacy list, Brave/Firefox/DDG/Samsung/Edge/WebLibre/Bare, secure notes).

> The competitive and Daymark-gap tables below are the **2026-10-11 research snapshot**. Current implementation status is reconciled in the dated addendum at the end; do not read the old “missing” labels as current without that cross-check.

## 1. Competitive map

### A. Cloud task giants
**Todoist, TickTick, Any.do, Microsoft To Do, Google Tasks**
- Strengths: NL quick-add, tags/labels, calendar views, widgets, polished capture, cross-device sync, habits/Pomodoro (TickTick).
- Gaps vs Daymark: account/cloud as source of truth; weak or no on-device encryption story; **no serious embedded privacy browser**.

### B. Local / privacy task tools
**Tasks.org, Super Productivity, Everdo, Trayzero, Orgzly, Taskwarrior, Planify, Vikunja (self-host)**
- Strengths: local DB, optional CalDAV/E2EE sync, open source, GTD depth, time tracking (Super Productivity).
- Gaps vs Daymark: almost none ship a **full in-app HTTPS browser + multi-engine/AI search + page-local extensions + signed sideload updater**.

### C. Secure notes (adjacent)
**Joplin, Standard Notes, Notesnook, Scrib, Obsidian+Tasks**
- Strengths: E2EE notes, markdown, vaults, attachments.
- Gaps: task UX secondary; no Daymark-style research browser loop.

### D. Privacy browsers
**Brave, Firefox/IronFox, DuckDuckGo, Samsung Internet, Edge, WebLibre, Bare, Sentinel**
- Strengths: Shields/ad-block defaults, real extensions (Firefox), bookmarks, private tabs, history search, downloads, reader.
- Gaps: **no AES task system, portable encrypted task backup, or task↔web research workflow**.

### E. Daymark position (unique blend)
Almost no product owns: **AES-256-GCM tasks + no account + portable encrypted backup + HTTPS-only WebView browser + multi-engine/AI shortcuts + page-local extensions + GitHub sideload release channel**.
That blend is the moat — keep it; fill hygiene features the market trains users to expect.

## 2. What the market has that Daymark must not miss

| Must-have (user-expected) | Why market wins without it | Daymark status |
|---------------------------|----------------------------|----------------|
| **Tags / labels** | Tasks.org, TickTick, Todoist filter everything | Missing / weak |
| **Natural-language quick add** | Todoist/TickTick muscle memory | Missing |
| **Reliable reminders (device-proven)** | Every mainstream task app | Code path; device proof open |
| **Global search (title + notes)** | Baseline | Partial (filter) |
| **Share → new task** | Android habit | Weak |
| **Home-screen widget** | TickTick/MS To Do retention | Missing |
| **Bookmarks** | Every browser | Missing |
| **Private / session tab** | Chrome/Samsung/Brave | Missing |
| **Searchable history** | Every browser | Partial site history |
| **App lock (PIN/biometric)** | Secure notes + privacy browsers | Missing |
| **Research → task** (page/selection) | Edge Collections spirit | Missing — high fit |
| **Clean chrome / density** | User complaints + redesign slice | In progress |

## 3. What many competitors lack (Daymark should keep / press)

| Daymark strength | Who lacks it |
|------------------|--------------|
| Default on-device AES task encryption | Todoist, TickTick, MS To Do, Google Tasks |
| No account required | Cloud giants |
| Portable encrypted backup (owner-held) | Most SaaS |
| In-app multi-engine + AI site launcher | Task apps; most browsers are single-product |
| Explicit HTTPS / consent web policy | Casual browsers |
| Sideload signed updater without Play | Most consumer apps |
| Page-local extension packs | Stock WebView browsers |

Do **not** copy: forced accounts, telemetry, crypto-rewards (Brave noise), team chat bloat, cloud-as-truth.

## 4. Gaps competitors could use against Daymark

1. **Capture speed** — without NL + tags, power users bounce to Todoist/TickTick.
2. **Browser completeness** — without bookmarks + private tab + history search, users keep Chrome/Brave alongside.
3. **Surface presence** — without widget, app is “out of sight”.
4. **Trust UX** — app lock expected for encrypted data apps.
5. **Stability/polish** — cluttered chrome + blank WebView incidents train uninstalls faster than missing tags.

## 5. Recommended build order (market-driven)

**P0 — stop churn**
- Device-green browser load + reminders
- Finish home/browser density redesign
- Merge perf (hardware WebView, list gate, search debounce)

**P1 — parity hygiene**
- Tags + NL quick-add + deeper search
- Share-in task + research→task from browser
- Bookmarks + private tab + history search
- App lock

**P2 — leverage unique blend**
- Scheduled encrypted backup to SAF
- Widget (today / open count)
- Command palette

**Explicit non-copy**
- Cloud sync as default
- Fake full ad-block inside stock WebView policy
- GeckoView rewrite before P0 solid

## 6. One-line strategy

Be the **only daily driver** where encrypted tasks and research browsing live in one private app — match market capture/chrome hygiene, never trade away local encryption or no-account defaults.

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: web research + Daymark product knowledge
Timestamp (UTC): 2026-10-11T18:10:00Z


## Current-source status update — 2026-10-13

This is an addendum, not a rewrite of the 2026-10-11 competitor snapshot. It was checked against current `main` `43fbe7670ad19ff2809f916f1b7dace4bc5ceb04` on 2026-10-13.

- Natural-language quick add is now on `main` via merged PR [#292](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/292). The single-line parser supports documented date/time, priority, and `#tag` tokens; the smoke test passed 16 assertions. Tags are captured through this path, not a general tag-management or tag-filter UI. See [`NL_QUICK_ADD.md`](NL_QUICK_ADD.md).
- HTTPS-origin bookmarks and a filterable tab switcher are present in source; browser preferences have a dedicated Browser Settings surface. Bookmarks do not imply private/session tabs or full searchable URL history.
- Private/session tabs, full typed-history search, tab groups, browser-selection-to-task capture, share-in, and a home-screen widget remain gaps/proposals. The Oct 11 “missing” labels for quick add and bookmarks are historical, not current-source claims.
- Privacy-safe WebView visual-state/lifecycle log breadcrumbs are present via merged PR [#291](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/pull/291); they are not a user-facing diagnostics/export screen and do not prove that a page visibly renders.
- Issues [#221](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/221), [#222](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/222), [#160](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/160), and [#257](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/257) were still open at review time. Browser, release, and Android 16 device acceptance remain **NOT TESTED / NOT COMPLETE**; check the live status index before using these issue labels.

---
Work by: Cue
Model: not disclosed by runtime
Tooling: GitHub connector CLI and local source review
Timestamp (UTC): 2026-10-13T14:42:14Z

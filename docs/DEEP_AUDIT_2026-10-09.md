# Deep audit — 2026-10-09 (Grok)

**No production tag from this audit.** Other agents may still land work; release only when the owner says the queue is clear.

## Published vs source

| Layer | State |
|-------|--------|
| Latest **published** GitHub Release | **v1.0.4** (APK+AAB); signer SHA-256 matches pin `ad6be60b…030580` |
| Source `versionName` / `versionCode` | **1.0.5** / **6** |
| Exact tag `v1.0.5` | **Not created** (intentional hold) |
| Production workflow | Includes `daymark-updater-v1` metadata block (after #181) |

## Merged this session / recently

| PR | What |
|----|------|
| **#181** | Updater release notes metadata, Trust & add for extension import, grouped More UI, network-image block, docs |
| **#182** | True **local text-only Reader Mode** (bounded extract, native TextView, Copy); Expand stays separate |

## Nested UI inventory (MainActivity — SOURCE-VERIFIED)

Entry points observed (non-exhaustive of every helper):

**Tasks:** `showTaskEditor`, `showAdvancedDetails`, `showNotesDialog`, `showSubtasksDialog`, `showAddSubtaskDialog`, `showTaskActions`, `showTaskTemplatesDialog`, `showAttachmentManager`, date/time pickers, reminder checks.

**Browser:** home, overflow, settings, tabs, history, find-in-page, media list, **Reader Mode**, full-screen expand, extensions manager + **extension details** + **per-site** dialogs, downloads, external open, search provider picker.

**System / data:** More/settings (grouped), theme, text size, high contrast, screen-reader info, permissions, portable backup + import key + recovery key ack, update details, save-failure choices.

## Gaps still real (not theoretical)

| Gap | Severity | Notes |
|-----|----------|--------|
| **Report a bug** not wired in More | Medium | `BugReportLinks` on branch; needs Settings row + open URL |
| Quick-capture **time** not fully shared | Low–Med | Inline `parseSmartTaskDraft` lacks HH:mm; `SmartTaskDraft.parse` on branch does |
| **Recurrence / tags** | Med backlog | Documented in milestones; schema not on main |
| In-app updater vs **v1.0.4 empty body** | Med | Fixed for *next* tag only; v1.0.4 cannot supply updater metadata |
| Physical device QA | High for “done” claims | Install/update, TalkBack, Reader quality, SAF |
| Play API 36 | N/A for personal sideload | targetSdk 35 |

## Agent coordination rule

Before merge/tag:

1. `gh pr list` + recent commits on `main`
2. Prefer one clean implementation per feature (no duplicate Back/Reader/Extensions)
3. Do not tag while useful open PRs remain unless owner accepts deferral
4. Never change production keystore secrets

## Explicit non-actions

- No `v1.0.5` tag from this agent until owner confirms other agents are finished.
- No GeckoView engine swap in this release train.
- No claim of full Chrome extension / network adblock support.

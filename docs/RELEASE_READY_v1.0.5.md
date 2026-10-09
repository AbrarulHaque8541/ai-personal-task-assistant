# Release-ready notes — v1.0.5

## Current baseline (2026-10-09 audit by Grok)

| Item | Value |
|------|--------|
| Source `versionName` | **1.0.5** |
| Source `versionCode` | **6** |
| `applicationId` | `com.cue.daymark` |
| Pinned signer | `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580` |
| Latest **published** release | **v1.0.4** (APK+AAB; signer verified match) |
| Open critical PR | **#181 merged** (updater metadata + trust UX branch) |

## Already in product (do not re-implement blindly)

- Tasks: title, notes, due date, **due time**, priority, subtasks, reminder lead, attachments, templates
- Smart quick capture: today / tomorrow / next week / priority words
- Filters: All, Today, Upcoming, Overdue, No date, Completed
- Browser: multi search engines, AI shortcuts, back history, overflow, downloads, extensions packs
- Security: HTTPS-only, encrypted store, same-signer release gates
- Production workflow emits `<!-- daymark-updater-v1 {…} -->` metadata (required by in-app updater)

## This branch adds

- `BugReportLinks` — GitHub new-issue URL with device/app prefill (wire into More / settings UI if not already)
- `SmartTaskDraft` as shared parser with **time** support (`3:30 pm`, `09:05`); MainActivity may still use an inline copy — prefer calling `SmartTaskDraft.parse`

## Tag steps (owner)

1. Confirm `main` includes desired commits (including merged #181).
2. Prefer merging this branch if CI green.
3. Create tag **`v1.0.5`** on that `main` commit (not an older tip).
4. Wait for **Android production release** workflow.
5. Install APK over v1.0.4 — should be in-place update (same signer verified on v1.0.4).

## Intentionally not in this slice

- Full recurrence engine / tags taxonomy (schema + UX still open)
- GeckoView engine swap (future optional path; not for this tag)
- Play Store API 36 migration (sideload OK)
- Claiming device E2E without phone test

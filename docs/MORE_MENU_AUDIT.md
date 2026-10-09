# More menu audit (current main)

All existing rows were already wired to real methods:

| Row | Method | Status |
|-----|--------|--------|
| Appearance | `showThemePicker` | Working |
| Text size | `showTextSizePicker` | Working |
| High contrast | `toggleHighContrast` | Working |
| Accessibility | `showScreenReaderInfo` | Working (info) |
| Permission status | `showPermissionStatus` | Working |
| Encrypted backup | `showPortableBackupDialog` | Working |
| Browser & extensions | `showBrowserExtensionsManager` / Web mode | Working |
| Check for updates | `checkForUpdates(true)` | Working (sideload + publisher pin) |

**Gap:** no About screen (version/package/releases).

**UX issue:** flat list felt dated; primary actions (updates, backup) buried.

**Fix:** `MoreSettingsSheet` — card groups, hero version header, Quick Actions first (updates, backup, about), then look & feel / access / browser.

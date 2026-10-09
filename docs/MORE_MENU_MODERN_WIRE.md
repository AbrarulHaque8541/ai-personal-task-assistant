# Wire modern More menu into MainActivity

## Audit: existing rows were already working

| Item | Method | Working |
|------|--------|--------|
| Appearance | showThemePicker | yes |
| Text size | showTextSizePicker | yes |
| High contrast | toggleHighContrast | yes |
| Accessibility | showScreenReaderInfo | yes |
| Permissions | showPermissionStatus | yes |
| Backup | showPortableBackupDialog | yes |
| Browser/extensions | showBrowserExtensionsManager / setWebMode | yes |
| Check for updates | checkForUpdates(true) | yes (sideload) |

Missing: About. UX: old flat list; important actions not first.

## Apply

1. Ensure `MoreSettingsSheet.java` is on the branch.
2. Make nested `Palette` package-visible (remove `private` if present).
3. Replace `showSettingsDialog()` body with the Host wiring that calls `new MoreSettingsSheet(...).show()` and add `showAboutApp()` — see commit on branch `feat/more-menu-modern-about-v2` docs / local patch `MA_fixed` from agent workspace.

**Do not merge branch `feat/more-menu-modern-about`** — it briefly had a corrupted MainActivity placeholder; use **v2** only.

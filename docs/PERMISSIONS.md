# Daymark permissions

## Declared (manifest)
- `INTERNET` — in-app browser only
- `POST_NOTIFICATIONS` — task reminders (Android 13+)
- `RECEIVE_BOOT_COMPLETED` — reschedule reminders after reboot
- `VIBRATE` — reminder feedback
- `ACCESS_NETWORK_STATE` — sideload flavor (updater network check)

## Not declared (by design)
Camera, microphone, location, contacts, SMS, broad storage — privacy-first V1.

## Runtime UX
- **Settings → Permission status** — live grant summary + **Allow notifications**
- **More permission settings** — exact-alarm screen + system accessibility settings
- Reminders still call `requestNotificationPermissionIfNeeded` when a reminder is enabled
- `onRequestPermissionsResult` shows a short Toast

Accessibility services cannot be auto-granted; the app only opens the system Accessibility screen.

---
Work by: Grok
Model: Grok 4.5 (xAI)
Tooling: GitHub MCP tools
Timestamp (UTC): 2026-10-11T18:50:00Z

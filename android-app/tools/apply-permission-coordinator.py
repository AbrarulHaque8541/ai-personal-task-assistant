#!/usr/bin/env python3
"""Wire PermissionCoordinator into MainActivity permission UI (idempotent)."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
path = root / "app/src/main/java/com/cue/daymark/MainActivity.java"
text = path.read_text(encoding="utf-8")

if "PermissionCoordinator.statusSummary" in text:
    print("PermissionCoordinator already wired")
    sys.exit(0)

# Replace showPermissionStatus body opener with richer dialog + actions.
# We insert a dedicated helper and call it from the settings row path by
# replacing the simple AlertDialog-only ending when possible.

marker = "private void showPermissionStatus()"
if marker not in text:
    sys.exit("showPermissionStatus missing")

# Append helper methods before the last closing brace of the class.
helpers = '''
    private void showPermissionActionsDialog() {
        String summary = PermissionCoordinator.statusSummary(this);
        new AlertDialog.Builder(this)
                .setTitle("Permissions")
                .setMessage(summary
                        + "\n\nDaymark only asks for permissions it declares. "
                        + "Use the buttons below to grant notification access or open system settings "
                        + "(exact alarms, app details, accessibility).")
                .setPositiveButton("Allow notifications", (d, w) -> {
                    if (PermissionCoordinator.hasPostNotifications(this)) {
                        Toast.makeText(this, "Notifications already allowed", Toast.LENGTH_SHORT).show();
                    } else {
                        PermissionCoordinator.requestPostNotifications(this);
                    }
                })
                .setNeutralButton("Notification settings", (d, w) ->
                        PermissionCoordinator.openNotificationSettings(this))
                .setNegativeButton("App settings", (d, w) ->
                        PermissionCoordinator.openAppDetailsSettings(this))
                .show();
        // Secondary path for exact alarms / accessibility via long status sheet
        // (extra buttons limited on AlertDialog — use follow-up toast with actions in status).
    }

    private void showAdvancedPermissionSettings() {
        new AlertDialog.Builder(this)
                .setTitle("More permission settings")
                .setMessage("Exact alarms improve reminder timing on Android 12+. "
                        + "Accessibility is controlled only in system settings — Daymark cannot enable it by itself.")
                .setPositiveButton("Exact alarms", (d, w) ->
                        PermissionCoordinator.openExactAlarmSettings(this))
                .setNeutralButton("Accessibility", (d, w) ->
                        PermissionCoordinator.openAccessibilitySettings(this))
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PermissionCoordinator.REQUEST_POST_NOTIFICATIONS) return;
        boolean granted = grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        Toast.makeText(this,
                granted ? "Notification permission granted" : "Notification permission denied — in-app reminders still work",
                Toast.LENGTH_SHORT).show();
    }
'''

# Point settings row at the new dialog: replace showPermissionStatus() lambda call targets
# that open the old dialog — change addSettingsRow permission line to open actions first.
old_row = '''addSettingsRow(content, "Permission status", permissionStatusLabel(),
                        () -> showPermissionStatus());'''
new_row = '''addSettingsRow(content, "Permission status", permissionStatusLabel(),
                        () -> showPermissionActionsDialog());
                addSettingsRow(content, "More permission settings", "Alarms & accessibility",
                        () -> showAdvancedPermissionSettings());'''

if old_row in text:
    text = text.replace(old_row, new_row, 1)
elif "showPermissionActionsDialog" not in text:
    # fallback: still inject helpers
    pass
else:
    print("settings row already updated")

if "showPermissionActionsDialog" not in text:
    # insert helpers before final class closing brace
    idx = text.rfind("}")
    if idx < 0:
        sys.exit("no class end")
    text = text[:idx] + helpers + "\n" + text[idx:]

path.write_text(text, encoding="utf-8")
print("PermissionCoordinator UI wired")

#!/usr/bin/env python3
"""Wire PermissionCoordinator into MainActivity permission UI (idempotent)."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
path = root / "app/src/main/java/com/cue/daymark/MainActivity.java"
text = path.read_text(encoding="utf-8")

old_row = (
    '        addSettingsRow(content, "Permission status", permissionStatusLabel(),\n'
    '                () -> showPermissionStatus());'
)
new_row = (
    '        addSettingsRow(content, "Permission status", permissionStatusLabel(),\n'
    '                () -> showPermissionActionsDialog());\n'
    '        addSettingsRow(content, "More permission settings", "Alarms & accessibility",\n'
    '                () -> showAdvancedPermissionSettings());'
)

if old_row in text:
    text = text.replace(old_row, new_row, 1)
    print("settings rows updated")
elif "() -> showPermissionActionsDialog()" in text:
    print("settings rows already point at actions dialog")
else:
    print("WARN: permission settings row pattern not found")

helpers = '''
    private void showPermissionActionsDialog() {
        String summary = PermissionCoordinator.statusSummary(this);
        new AlertDialog.Builder(this)
                .setTitle("Permissions")
                .setMessage(summary
                        + "\n\nDaymark only asks for permissions it declares. "
                        + "Use the buttons below to grant notification access or open system settings.")
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
                granted ? "Notification permission granted"
                        : "Notification permission denied — in-app reminders still work",
                Toast.LENGTH_SHORT).show();
    }
'''

if "private void showPermissionActionsDialog()" not in text:
    idx = text.rfind("}")
    if idx < 0:
        sys.exit("no class end")
    text = text[:idx] + helpers + "\n" + text[idx:]
    print("helpers inserted")
elif "onRequestPermissionsResult" not in text:
    idx = text.rfind("}")
    text = text[:idx] + helpers + "\n" + text[idx:]
    print("helpers inserted (result handler)")
else:
    print("helpers already present")

path.write_text(text, encoding="utf-8")
print("PermissionCoordinator UI wired")

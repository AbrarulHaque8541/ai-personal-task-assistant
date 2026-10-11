package com.cue.daymark;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Runtime permission helpers for permissions Daymark actually declares.
 * Does not add camera/mic/location — those stay out of the privacy-first manifest.
 * Provides request + Settings deep-links so the user can always grant what is needed.
 */
final class PermissionCoordinator {
    static final int REQUEST_POST_NOTIFICATIONS = 44021;

    private PermissionCoordinator() { }

    static boolean hasPostNotifications(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < 33) return true;
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Android 13+: show system dialog for POST_NOTIFICATIONS. No-op if already granted. */
    static void requestPostNotifications(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 33) return;
        if (hasPostNotifications(activity)) return;
        activity.requestPermissions(
                new String[] { Manifest.permission.POST_NOTIFICATIONS },
                REQUEST_POST_NOTIFICATIONS);
    }

    /**
     * Exact-alarm capability (Android 12+). Not a classic runtime permission dialog on all
     * OEMs — opens the system exact-alarm settings screen when the app cannot schedule exact alarms.
     */
    static boolean canScheduleExactAlarms(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        return am.canScheduleExactAlarms();
    }

    static void openExactAlarmSettings(Activity activity) {
        if (activity == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
                return;
            }
        } catch (Exception ignored) { }
        openAppDetailsSettings(activity);
    }

    static void openNotificationSettings(Activity activity) {
        if (activity == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            activity.startActivity(intent);
            return;
        } catch (Exception ignored) { }
        openAppDetailsSettings(activity);
    }

    static void openAppDetailsSettings(Activity activity) {
        if (activity == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception ignored) { }
    }

    /** Accessibility services cannot be granted by an ordinary app dialog — open system screen. */
    static void openAccessibilitySettings(Activity activity) {
        if (activity == null) return;
        try {
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) {
            openAppDetailsSettings(activity);
        }
    }

    static String statusSummary(Context context) {
        StringBuilder sb = new StringBuilder();
        if (Build.VERSION.SDK_INT >= 33) {
            sb.append("Notifications: ")
                    .append(hasPostNotifications(context) ? "allowed" : "not allowed")
                    .append('\n');
        } else {
            sb.append("Notifications: allowed by system (pre-Android 13)\n");
        }
        if (Build.VERSION.SDK_INT >= 31) {
            sb.append("Exact alarms: ")
                    .append(canScheduleExactAlarms(context) ? "allowed" : "not allowed")
                    .append('\n');
        }
        sb.append("Internet: declared for the in-app browser only\n");
        sb.append("Camera / mic / location / contacts: not requested (privacy policy)");
        return sb.toString();
    }
}

package com.cue.daymark;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.util.List;

/** Android platform adapter for local reminders; no worker, service, network, or wake lock is used. */
final class ReminderScheduler {
    private static final String ALARM_SCHEME = "daymark-reminder";
    private static final String CHANNEL_PREFIX = "Daymark reminders · ";

    private ReminderScheduler() { }

    static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    static ReminderLogic.SchedulePlan schedule(Context context, Reminder reminder) {
        if (reminder == null || reminder.delivered || !notificationsEnabled(context)) {
            return ReminderLogic.SchedulePlan.NO_SCHEDULE;
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return ReminderLogic.SchedulePlan.NO_SCHEDULE;
        PendingIntent pending = alarmPendingIntent(context, reminder.taskId);
        PendingIntent fallback = fallbackPendingIntent(context, reminder.taskId);
        boolean exactAccess = canScheduleExactAlarms(context);
        ReminderLogic.SchedulePlan plan = ReminderLogic.schedulePlan(true, exactAccess);
        if (plan == ReminderLogic.SchedulePlan.EXACT_ALLOW_WHILE_IDLE) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending);
                if (ReminderLogic.needsInexactRevocationFallback(Build.VERSION.SDK_INT, exactAccess)) {
                    // Exact access can be revoked without a revoke broadcast; retain a distinct inexact backstop.
                    long fallbackAt = reminder.triggerAtMillis > Long.MAX_VALUE - ReminderLogic.MIN_IDLE_ALARM_INTERVAL_MILLIS
                            ? Long.MAX_VALUE : reminder.triggerAtMillis + ReminderLogic.MIN_IDLE_ALARM_INTERVAL_MILLIS;
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fallbackAt, fallback);
                }
                return plan;
            } catch (SecurityException revoked) {
                // Access can change between the check and API call. Degrade rather than crash.
                manager.cancel(pending);
                manager.cancel(fallback);
                fallback.cancel();
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending);
                return ReminderLogic.SchedulePlan.INEXACT_ALLOW_WHILE_IDLE;
            }
        }
        if (plan == ReminderLogic.SchedulePlan.INEXACT_ALLOW_WHILE_IDLE) {
            manager.cancel(fallback);
            fallback.cancel();
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending);
        }
        return plan;
    }

    static void rescheduleAll(Context context, List<Reminder> reminders) {
        if (reminders == null) return;
        boolean enabled = notificationsEnabled(context);
        for (Reminder reminder : reminders) {
            if (!ReminderLogic.shouldRestoreAfterReboot(reminder)) continue;
            if (enabled) schedule(context, reminder);
            else cancel(context, reminder.taskId);
        }
    }

    static void cancel(Context context, String taskId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = alarmPendingIntent(context, taskId);
        PendingIntent fallback = fallbackPendingIntent(context, taskId);
        if (manager != null) {
            manager.cancel(pending);
            manager.cancel(fallback);
        }
        pending.cancel();
        fallback.cancel();
    }

    static void cancelNotification(Context context, String taskId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId(taskId));
    }

    static boolean notificationsEnabled(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static int notificationId(String taskId) {
        int value = taskId == null ? 1 : taskId.hashCode() & 0x7fffffff;
        return value == 0 ? 1 : value;
    }

    static String ensureChannel(Context context, String soundUri) {
        String id = ReminderLogic.notificationChannelId(soundUri);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return id;
        if (manager.getNotificationChannel(id) != null) return id;

        NotificationChannel channel = new NotificationChannel(id, CHANNEL_PREFIX + id.substring(id.length() - 6),
                NotificationManager.IMPORTANCE_DEFAULT);
        Uri sound = soundUri == null ? null : Uri.parse(soundUri);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(sound, attributes);
        manager.createNotificationChannel(channel);
        return id;
    }

    static PendingIntent actionPendingIntent(Context context, String action, String taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(action)
                .setData(actionUri(action, taskId))
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent alarmPendingIntent(Context context, String taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .setData(Uri.parse(ALARM_SCHEME + "://alarm/" + Uri.encode(taskId)))
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent fallbackPendingIntent(Context context, String taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(ReminderReceiver.ACTION_FALLBACK)
                .setData(Uri.parse(ALARM_SCHEME + "://fallback/" + Uri.encode(taskId)))
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Uri actionUri(String action, String taskId) {
        return Uri.parse(ALARM_SCHEME + "://action/" + Uri.encode(action) + "/" + Uri.encode(taskId));
    }
}

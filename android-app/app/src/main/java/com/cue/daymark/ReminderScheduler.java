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
        if (reminder == null || !reminder.isValid() || reminder.version <= 0
                || reminder.delivered || !notificationsEnabled(context)) {
            return ReminderLogic.SchedulePlan.NO_SCHEDULE;
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return ReminderLogic.SchedulePlan.NO_SCHEDULE;
        Long retryAt = ReminderLogic.deliveryRetryAtMillis(reminder.triggerAtMillis);
        if (retryAt == null) return ReminderLogic.SchedulePlan.NO_SCHEDULE;

        PendingIntent pending = alarmPendingIntent(context, reminder.taskId, reminder.version);
        PendingIntent fallback = fallbackPendingIntent(context, reminder.taskId, reminder.version);
        PendingIntent retry = retryPendingIntent(context, reminder.taskId, reminder.version);
        boolean exactAccess = canScheduleExactAlarms(context);
        ReminderLogic.SchedulePlan plan = ReminderLogic.schedulePlan(true, exactAccess);
        boolean retryArmed = false;
        try {
            // Arm the durable, inexact retry before the one-shot alarm can be consumed.
            // It remains until the notification post and delivered-state commit both succeed.
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, retryAt,
                    AlarmManager.INTERVAL_FIFTEEN_MINUTES, retry);
            retryArmed = true;
            if (plan == ReminderLogic.SchedulePlan.EXACT_ALLOW_WHILE_IDLE) {
                Long fallbackAt = ReminderLogic.revocationFallbackAtMillis(reminder.triggerAtMillis);
                if (fallbackAt != null) {
                    // This independent allow-while-idle backup survives exact-access revocation.
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fallbackAt, fallback);
                }
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            reminder.triggerAtMillis, pending);
                    return plan;
                } catch (SecurityException revoked) {
                    manager.cancel(pending);
                    manager.cancel(fallback);
                    fallback.cancel();
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            reminder.triggerAtMillis, pending);
                    return ReminderLogic.SchedulePlan.INEXACT_ALLOW_WHILE_IDLE;
                }
            }
            manager.cancel(fallback);
            fallback.cancel();
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending);
            return plan;
        } catch (RuntimeException failure) {
            manager.cancel(pending);
            manager.cancel(fallback);
            // The primary alarm can fail while the already-armed repeating retry remains usable.
            return retryArmed ? ReminderLogic.SchedulePlan.INEXACT_ALLOW_WHILE_IDLE
                    : ReminderLogic.SchedulePlan.NO_SCHEDULE;
        }
    }

    static void rescheduleAll(Context context, List<Reminder> reminders) {
        if (reminders == null) return;
        boolean enabled = notificationsEnabled(context);
        for (Reminder reminder : reminders) {
            if (reminder == null || reminder.version <= 0) continue;
            cancelLegacyNotification(context, reminder.taskId);
            if (reminder.delivered) {
                cancel(context, reminder.taskId, reminder.version);
            } else if (enabled && ReminderLogic.shouldRestoreAfterReboot(reminder)) {
                schedule(context, reminder);
            } else if (!enabled) {
                cancel(context, reminder.taskId, reminder.version);
            }
        }
    }

    static void cancel(Context context, String taskId, long version) {
        if (taskId == null || version <= 0) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = alarmPendingIntent(context, taskId, version);
        PendingIntent fallback = fallbackPendingIntent(context, taskId, version);
        PendingIntent retry = retryPendingIntent(context, taskId, version);
        if (manager != null) {
            manager.cancel(pending);
            manager.cancel(fallback);
            manager.cancel(retry);
        }
        pending.cancel();
        fallback.cancel();
        retry.cancel();
    }

    /** Schedule cleanup before writing a cancellation tombstone, so a process death cannot strand it. */
    static boolean scheduleTombstoneCleanup(Context context, ReminderTombstone tombstone) {
        if (tombstone == null || !tombstone.isValid()) return false;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return false;
        long now = System.currentTimeMillis();
        Long firstRetry = ReminderLogic.deliveryRetryAtMillis(now);
        if (firstRetry == null) return false;
        PendingIntent cleanup = cleanupPendingIntent(context, tombstone);
        try {
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, firstRetry,
                    AlarmManager.INTERVAL_FIFTEEN_MINUTES, cleanup);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static void cancelTombstoneCleanup(Context context, ReminderTombstone tombstone) {
        if (tombstone == null || !tombstone.isValid()) return;
        PendingIntent cleanup = cleanupPendingIntent(context, tombstone);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(cleanup);
        cleanup.cancel();
    }

    static void cancelNotification(Context context, String taskId, long version) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && taskId != null && version > 0) {
            manager.cancel(notificationTag(taskId, version), notificationId(taskId, version));
        }
    }

    /** Remove the pre-v3 per-task notification key during migration/reconciliation only. */
    static void cancelLegacyNotification(Context context, String taskId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && taskId != null) {
            manager.cancel(ReminderLogic.legacyNotificationTag(taskId),
                    ReminderLogic.legacyNotificationId(taskId));
        }
    }

    static boolean notificationsEnabled(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static int notificationId(String taskId, long version) {
        return ReminderLogic.notificationId(taskId, version);
    }

    static String notificationTag(String taskId, long version) {
        return ReminderLogic.notificationTag(taskId, version);
    }

    static String ensureChannel(Context context, String soundUri) {
        String id = ReminderLogic.notificationChannelId(soundUri);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return id;
        if (manager.getNotificationChannel(id) != null) return id;

        NotificationChannel channel = new NotificationChannel(id, CHANNEL_PREFIX + id.substring(id.length() - 6),
                NotificationManager.IMPORTANCE_DEFAULT);
        android.net.Uri sound = soundUri == null ? null : android.net.Uri.parse(soundUri);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(sound, attributes);
        manager.createNotificationChannel(channel);
        return id;
    }

    static PendingIntent actionPendingIntent(Context context, String action, String taskId, long version) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(action)
                .setData(actionUri("action/" + action, taskId, version))
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
                .putExtra(ReminderReceiver.EXTRA_VERSION, version);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent alarmPendingIntent(Context context, String taskId, long version) {
        Intent intent = versionedIntent(context, ReminderReceiver.ACTION_FIRE, "alarm", taskId, version);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent fallbackPendingIntent(Context context, String taskId, long version) {
        Intent intent = versionedIntent(context, ReminderReceiver.ACTION_FALLBACK, "fallback", taskId, version);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent retryPendingIntent(Context context, String taskId, long version) {
        Intent intent = versionedIntent(context, ReminderReceiver.ACTION_RETRY, "retry", taskId, version);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent cleanupPendingIntent(Context context, ReminderTombstone tombstone) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(ReminderReceiver.ACTION_CLEANUP)
                .setData(Uri.parse(ALARM_SCHEME + "://cleanup/" + Uri.encode(tombstone.taskId)
                        + "/" + tombstone.cancelledVersion + "/" + tombstone.revision))
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, tombstone.taskId)
                .putExtra(ReminderReceiver.EXTRA_VERSION, tombstone.cancelledVersion)
                .putExtra(ReminderReceiver.EXTRA_TOMBSTONE_REVISION, tombstone.revision);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent versionedIntent(Context context, String action, String kind,
                                          String taskId, long version) {
        return new Intent(context, ReminderReceiver.class)
                .setAction(action)
                .setData(actionUri(kind, taskId, version))
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
                .putExtra(ReminderReceiver.EXTRA_VERSION, version);
    }

    private static Uri actionUri(String action, String taskId, long version) {
        return Uri.parse(ALARM_SCHEME + "://" + Uri.encode(action) + "/" + Uri.encode(taskId)
                + "/" + version);
    }
}

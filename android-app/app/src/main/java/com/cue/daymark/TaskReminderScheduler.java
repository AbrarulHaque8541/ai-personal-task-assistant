package com.cue.daymark;

import android.app.Activity;
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

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Schedules OS alarms so due reminders can fire with a notification sound
 * even when Daymark is not open. Uses the system default notification ringtone.
 */
final class TaskReminderScheduler {
    static final String CHANNEL_ID = "daymark_task_reminders";
    static final String EXTRA_TASK_ID = "task_id";
    static final String EXTRA_TASK_TITLE = "task_title";
    private static final int REQ_POST_NOTIFICATIONS = 44021;

    private TaskReminderScheduler() { }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
        if (existing != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Task reminders",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Sounds when a task reminder is due");
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(sound, attrs);
        channel.enableVibration(true);
        nm.createNotificationChannel(channel);
    }

    /** Android 13+: ask once when the user sets a reminder. */
    static void requestNotificationPermissionIfNeeded(Activity activity) {
        if (Build.VERSION.SDK_INT < 33 || activity == null) return;
        if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        activity.requestPermissions(
                new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
                REQ_POST_NOTIFICATIONS);
    }

    static void rescheduleAll(Context context, List<Task> tasks) {
        ensureChannel(context);
        if (tasks == null) return;
        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();
        for (Task task : tasks) {
            cancel(context, task.id);
            if (task.completed || task.reminderLeadMinutes == null) continue;
            Instant fire = TaskLogic.reminderFireInstant(task, zone);
            if (fire == null || !fire.isAfter(now)) continue;
            if (task.reminderShownFire != null
                    && task.reminderShownFire.equals(fire.toString())) {
                continue;
            }
            schedule(context, task.id, task.title, fire.toEpochMilli());
        }
    }

    static void schedule(Context context, String taskId, String title, long triggerAtMillis) {
        if (taskId == null || taskId.isEmpty()) return;
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = pendingIntent(context, taskId, title == null ? "Task" : title);
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (SecurityException ignored) {
            // Exact-alarm permission denied — in-app checkDueReminders remains the fallback.
        }
    }

    static void cancel(Context context, String taskId) {
        if (taskId == null || taskId.isEmpty()) return;
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.cancel(pendingIntent(context, taskId, ""));
    }

    private static PendingIntent pendingIntent(Context context, String taskId, String title) {
        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.setAction("com.cue.daymark.TASK_REMINDER");
        intent.putExtra(EXTRA_TASK_ID, taskId);
        intent.putExtra(EXTRA_TASK_TITLE, title);
        int requestCode = taskId.hashCode();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}

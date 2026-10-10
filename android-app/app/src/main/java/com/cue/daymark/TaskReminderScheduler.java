package com.cue.daymark;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Best-effort scheduler for OS task-reminder notifications.
 *
 * Honest limits, by design:
 * - Alarms use inexact setAndAllowWhileIdle: Doze and battery saver may delay them.
 * - The schedule is recomputed on every task save and app open; nothing polls in the background.
 * - The user-chosen notification sound is stored app-locally and never leaves the device.
 * - Repeating occurrences are scheduled from the task's current due date; nothing is pre-queued.
 */
final class TaskReminderScheduler {
    static final String REMINDER_PREFERENCES = "daymark.reminders.v1";
    static final String NOTIFICATION_SOUND_URI_KEY = "notification_sound_uri";
    static final String BOOT_RESCHEDULE_FAILED_KEY = "boot_reschedule_failed";
    static final String CHANNEL_ID = "daymark_task_reminders";
    private static final String CODE_PREFERENCES = "daymark.reminder.codes.v1";
    private static final String CODE_COUNTER_KEY = "next_request_code";
    private static final int FIRST_REQUEST_CODE = 44000;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 44021;
    private static final String CHANNEL_SOUND_KEY = "channel_sound_uri";
    private static final String LOG_TAG = "DaymarkReminder";

    private TaskReminderScheduler() { }

    /** POST_NOTIFICATIONS (Android 13+) is requested only when the user enables a reminder. */
    static void requestNotificationPermissionIfNeeded(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 33) return;
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        activity.requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                REQUEST_NOTIFICATION_PERMISSION);
    }

    /** Cancels stale alarms and reschedules every open task that has a reminder. */
    static void rescheduleAll(Context context, List<Task> tasks) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(LOG_TAG, "AlarmManager is unavailable; reminders stay in-app only.");
            return;
        }
        ensureChannel(context);
        SharedPreferences codes = context.getSharedPreferences(CODE_PREFERENCES, Context.MODE_PRIVATE);
        Set<String> liveIds = new HashSet<>();
        for (Task task : tasks) liveIds.add(task.id);
        Map<String, ?> known = new HashMap<>(codes.getAll());
        for (Map.Entry<String, ?> entry : known.entrySet()) {
            String key = entry.getKey();
            if (CODE_COUNTER_KEY.equals(key)) continue;
            int requestCode = Integer.parseInt(String.valueOf(entry.getValue()));
            if (!liveIds.contains(key)) {
                cancelAlarm(context, alarmManager, key, requestCode);
                codes.edit().remove(key).apply();
            }
        }
        for (Task task : tasks) {
            Object assigned = known.get(task.id);
            boolean needsAlarm = !task.completed && task.reminderLeadMinutes != null
                    && task.dueDate != null;
            if (!needsAlarm && assigned != null) {
                cancelAlarm(context, alarmManager, task.id, Integer.parseInt(String.valueOf(assigned)));
                codes.edit().remove(task.id).apply();
            } else if (needsAlarm) {
                schedule(context, alarmManager, task);
            }
        }
    }

    private static void schedule(Context context, AlarmManager alarmManager, Task task) {
        Instant fire = TaskLogic.reminderFireInstant(task, ZoneId.systemDefault());
        if (fire == null) return;
        String fireText = fire.toString();
        if (fireText.equals(task.reminderShownFire)) return; // already presented for this occurrence
        if (!fire.isAfter(Instant.now())) return; // past occurrences stay with the in-app dialog flow
        int requestCode = requestCodeFor(context, task.id);
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.toEpochMilli(),
                pendingIntent(context, task, fireText, requestCode));
    }

    private static void cancelAlarm(Context context, AlarmManager alarmManager, String taskId, int requestCode) {
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode,
                reminderIntent(context, taskId),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            alarmManager.cancel(pending);
            pending.cancel();
        }
    }

    private static PendingIntent pendingIntent(Context context, Task task, String fireText, int requestCode) {
        Intent intent = reminderIntent(context, task.id);
        intent.putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, task.title);
        intent.putExtra(TaskReminderReceiver.EXTRA_FIRE_INSTANT, fireText);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent reminderIntent(Context context, String taskId) {
        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId);
        return intent;
    }

    /** Stable, collision-free per-task request code; old ids are pruned on reschedule. */
    private static int requestCodeFor(Context context, String taskId) {
        SharedPreferences codes = context.getSharedPreferences(CODE_PREFERENCES, Context.MODE_PRIVATE);
        String existing = codes.getString(taskId, null);
        if (existing != null) return Integer.parseInt(existing);
        int next = codes.getInt(CODE_COUNTER_KEY, FIRST_REQUEST_CODE);
        codes.edit().putString(taskId, String.valueOf(next))
                .putInt(CODE_COUNTER_KEY, next + 1).apply();
        return next;
    }

    /** Stores the user-chosen sound; the channel is recreated so the change takes effect. */
    static void applySound(Context context, Uri picked) {
        SharedPreferences preferences =
                context.getSharedPreferences(REMINDER_PREFERENCES, Context.MODE_PRIVATE);
        if (picked == null) {
            preferences.edit().remove(NOTIFICATION_SOUND_URI_KEY).apply();
        } else {
            preferences.edit().putString(NOTIFICATION_SOUND_URI_KEY, picked.toString()).apply();
        }
        ensureChannel(context);
    }

    /** Creates (and on sound changes, recreates) the reminder notification channel. */
    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.w(LOG_TAG, "NotificationManager is unavailable; reminders stay in-app only.");
            return;
        }
        SharedPreferences preferences =
                context.getSharedPreferences(REMINDER_PREFERENCES, Context.MODE_PRIVATE);
        String soundUri = preferences.getString(NOTIFICATION_SOUND_URI_KEY, null);
        String normalized = soundUri == null ? "" : soundUri;
        String previous = preferences.getString(CHANNEL_SOUND_KEY, null);
        if (previous != null && !previous.equals(normalized)) {
            notificationManager.deleteNotificationChannel(CHANNEL_ID);
        }
        preferences.edit().putString(CHANNEL_SOUND_KEY, normalized).apply();
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Task reminders", NotificationManager.IMPORTANCE_HIGH);
        Uri sound = soundUri == null
                ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                : Uri.parse(soundUri);
        channel.setSound(sound, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        notificationManager.createNotificationChannel(channel);
    }
}

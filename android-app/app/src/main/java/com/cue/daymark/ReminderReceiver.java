package com.cue.daymark;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Performs small local alarm actions and restores scheduled reminders after system changes. */
public final class ReminderReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "com.cue.daymark.action.REMINDER_FIRE";
    static final String ACTION_FALLBACK = "com.cue.daymark.action.REMINDER_FALLBACK";
    static final String ACTION_SNOOZE = "com.cue.daymark.action.REMINDER_SNOOZE";
    static final String ACTION_CANCEL = "com.cue.daymark.action.REMINDER_CANCEL";
    static final String EXTRA_TASK_ID = "com.cue.daymark.extra.TASK_ID";
    private static final String ACTION_EXACT_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Context appContext = context.getApplicationContext();
        String action = intent.getAction();
        String taskId = intent.getStringExtra(EXTRA_TASK_ID);
        PendingResult result = goAsync();
        EXECUTOR.execute(() -> {
            try {
                handle(appContext, action, taskId);
            } catch (Exception ignored) {
                // Encrypted storage failures are fail-closed; never replace or disclose task data.
            } finally {
                result.finish();
            }
        });
    }

    private void handle(Context context, String action, String taskId) throws Exception {
        EncryptedReminderStore store = new EncryptedReminderStore(context);
        if (ACTION_FIRE.equals(action) || ACTION_FALLBACK.equals(action)) {
            if (taskId == null) return;
            deliverIfCurrent(context, store, taskId);
        } else if (ACTION_SNOOZE.equals(action)) {
            if (taskId == null) return;
            Reminder next = store.snooze(taskId, System.currentTimeMillis());
            ReminderScheduler.cancelNotification(context, taskId);
            if (next != null) ReminderScheduler.schedule(context, next);
        } else if (ACTION_CANCEL.equals(action)) {
            if (taskId == null) return;
            store.remove(taskId);
            ReminderScheduler.cancel(context, taskId);
            ReminderScheduler.cancelNotification(context, taskId);
        } else if (isSystemRescheduleAction(action)) {
            List<Reminder> reminders = store.rebaseForCurrentTimezone();
            ReminderScheduler.rescheduleAll(context, reminders);
        }
    }

    private void deliverIfCurrent(Context context, EncryptedReminderStore store, String taskId) throws Exception {
        Reminder pending = store.find(taskId);
        if (pending == null || pending.delivered) return;
        Reminder rebased = ReminderLogic.afterTimezoneChange(pending, ZoneId.systemDefault());
        if (rebased.triggerAtMillis != pending.triggerAtMillis) {
            store.put(rebased);
            pending = rebased;
        }
        if (pending.triggerAtMillis > System.currentTimeMillis()) {
            ReminderScheduler.schedule(context, pending);
            return;
        }
        if (!ReminderScheduler.notificationsEnabled(context)) {
            ReminderScheduler.cancel(context, taskId);
            return;
        }
        Reminder delivered = store.markDelivered(taskId);
        if (delivered == null) return;
        ReminderScheduler.cancel(context, taskId);
        postNotification(context, delivered);
    }

    private boolean isSystemRescheduleAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || ACTION_EXACT_PERMISSION_CHANGED.equals(action);
    }

    private void postNotification(Context context, Reminder reminder) {
        if (!ReminderScheduler.notificationsEnabled(context)) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String channelId = ReminderScheduler.ensureChannel(context, reminder.soundUri);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(reminder.taskTitle)
                .setContentText("Task reminder")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_lock_idle_alarm,
                        "Snooze 10 min", ReminderScheduler.actionPendingIntent(context, ACTION_SNOOZE,
                        reminder.taskId)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                        "Cancel reminder", ReminderScheduler.actionPendingIntent(context, ACTION_CANCEL,
                        reminder.taskId)).build());
        manager.notify(ReminderScheduler.notificationId(reminder.taskId), builder.build());
    }
}

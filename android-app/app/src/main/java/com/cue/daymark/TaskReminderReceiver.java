package com.cue.daymark;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.util.List;

/** Fires a high-priority notification with the default notification ringtone. */
public final class TaskReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            rescheduleFromStore(context);
            return;
        }
        if (!"com.cue.daymark.TASK_REMINDER".equals(action)) return;

        String taskId = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_ID);
        String title = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_TITLE);
        if (title == null || title.isEmpty()) title = "Task reminder";

        TaskReminderScheduler.ensureChannel(context);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(context, 0, open, flags);

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Notification.Builder builder = new Notification.Builder(context, TaskReminderScheduler.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Daymark reminder")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(title))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setSound(sound)
                .setContentIntent(content);

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            int id = taskId == null ? (int) System.currentTimeMillis() : taskId.hashCode();
            nm.notify(id, builder.build());
        }
    }

    private static void rescheduleFromStore(Context context) {
        try {
            EncryptedTaskStore store = new EncryptedTaskStore(context);
            List<Task> tasks = store.load();
            TaskReminderScheduler.rescheduleAll(context, tasks);
        } catch (Exception ignored) {
            // Keystore/storage may be locked briefly after boot; in-app path still works.
        }
    }
}

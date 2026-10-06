package com.cue.daymark;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Performs local alarm actions and restores scheduled/pending reminders after system changes. */
public final class ReminderReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "com.cue.daymark.action.REMINDER_FIRE";
    static final String ACTION_FALLBACK = "com.cue.daymark.action.REMINDER_FALLBACK";
    static final String ACTION_RETRY = "com.cue.daymark.action.REMINDER_RETRY";
    static final String ACTION_CLEANUP = "com.cue.daymark.action.REMINDER_CLEANUP";
    static final String ACTION_SNOOZE = "com.cue.daymark.action.REMINDER_SNOOZE";
    static final String ACTION_CANCEL = "com.cue.daymark.action.REMINDER_CANCEL";
    static final String EXTRA_TASK_ID = "com.cue.daymark.extra.TASK_ID";
    static final String EXTRA_VERSION = "com.cue.daymark.extra.REMINDER_VERSION";
    static final String EXTRA_TOMBSTONE_REVISION = "com.cue.daymark.extra.TOMBSTONE_REVISION";
    private static final String ACTION_EXACT_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Context appContext = context.getApplicationContext();
        String action = intent.getAction();
        String taskId = intent.getStringExtra(EXTRA_TASK_ID);
        long version = intent.getLongExtra(EXTRA_VERSION, -1L);
        long tombstoneRevision = intent.getLongExtra(EXTRA_TOMBSTONE_REVISION, -1L);
        PendingResult result = goAsync();
        EXECUTOR.execute(() -> {
            try {
                synchronized (ReminderDeliveryLock.LOCK) {
                    handle(appContext, action, taskId, version, tombstoneRevision);
                }
            } catch (Exception ignored) {
                // Encrypted state and repeating recovery alarms remain authoritative after transient failures.
            } finally {
                result.finish();
            }
        });
    }

    private void handle(Context context, String action, String taskId,
                        long version, long tombstoneRevision) throws Exception {
        EncryptedReminderStore store = new EncryptedReminderStore(context);
        if (ACTION_FIRE.equals(action) || ACTION_FALLBACK.equals(action) || ACTION_RETRY.equals(action)) {
            if (taskId != null && version > 0) deliverIfCurrent(context, store, taskId, version);
        } else if (ACTION_SNOOZE.equals(action)) {
            if (taskId != null && version > 0) snoozeIfCurrent(context, store, taskId, version);
        } else if (ACTION_CANCEL.equals(action)) {
            if (taskId != null && version > 0) {
                boolean cancelled = cancelReminder(context, store, taskId, version);
                if (!cancelled) cleanStaleGeneration(context, store, taskId, version);
            }
        } else if (ACTION_CLEANUP.equals(action)) {
            if (taskId != null && version > 0 && tombstoneRevision > version) {
                ReminderTombstone expected = new ReminderTombstone(taskId, version, tombstoneRevision);
                ReminderTombstone stored = store.findTombstone(taskId, version);
                if (stored != null && stored.revision == tombstoneRevision) {
                    cleanTombstone(context, store, stored);
                } else {
                    // No tombstone means the pre-armed cleanup alarm fired before cancellation committed.
                    ReminderScheduler.cancelTombstoneCleanup(context, expected);
                }
            }
        } else if (isSystemRescheduleAction(action)) {
            reconcileAndReschedule(context, store);
        }
    }

    /** Called under the process-wide lock; retry alarm remains armed until durable delivered commit. */
    private void deliverIfCurrent(Context context, EncryptedReminderStore store,
                                  String taskId, long expectedVersion) throws Exception {
        Reminder current = store.find(taskId);
        if (!ReminderLogic.isCurrentGeneration(current, expectedVersion)) {
            cleanStaleGeneration(context, store, taskId, expectedVersion);
            if (current != null && !current.delivered) {
                if (current.deliveryPending) postPendingNotification(context, store, taskId, current.version);
                else ReminderScheduler.schedule(context, current);
            }
            return;
        }
        if (current.delivered) {
            ReminderScheduler.cancel(context, taskId, current.version);
            return;
        }
        Task task = findTask(context, taskId);
        if (!ReminderLogic.shouldDeliverForTask(current, task)) {
            cancelReminder(context, store, taskId, current.version);
            return;
        }
        if (!task.title.equals(current.taskTitle)) {
            store.updateTaskTitle(taskId, current.version, task.title);
            current = current.withTitle(task.title);
        }
        if (current.deliveryPending) {
            postPendingNotification(context, store, taskId, expectedVersion);
            return;
        }
        if (current.triggerAtMillis > System.currentTimeMillis()) {
            ReminderScheduler.schedule(context, current);
            return;
        }
        if (!ReminderScheduler.notificationsEnabled(context)) return;

        Reminder pending = store.markDeliveryPending(taskId, expectedVersion);
        if (pending == null) {
            Reminder latest = store.find(taskId);
            if (!ReminderLogic.isCurrentGeneration(latest, expectedVersion)) {
                cleanStaleGeneration(context, store, taskId, expectedVersion);
            } else if (latest != null && latest.delivered) {
                ReminderScheduler.cancel(context, taskId, expectedVersion);
            }
            return;
        }
        // Do not cancel any alarm before posting; the persistent repeating retry is the crash-safe wakeup.
        postPendingNotification(context, store, taskId, expectedVersion);
    }

    private void snoozeIfCurrent(Context context, EncryptedReminderStore store,
                                 String taskId, long expectedVersion) throws Exception {
        Reminder current = store.find(taskId);
        if (!ReminderLogic.isCurrentGeneration(current, expectedVersion) || !current.delivered) {
            cleanStaleGeneration(context, store, taskId, expectedVersion);
            return;
        }
        Task task = findTask(context, taskId);
        if (!ReminderLogic.belongsToOpenTask(current, task)) {
            cancelReminder(context, store, taskId, current.version);
            return;
        }
        ReminderTombstone cleanup = store.plannedCancellation(taskId, expectedVersion);
        long nextVersion = store.nextVersionForSnooze(taskId, expectedVersion);
        if (cleanup == null || nextVersion <= 0) return;
        if (!ReminderScheduler.scheduleTombstoneCleanup(context, cleanup)) {
            return;
        }
        long snoozeAtMillis = System.currentTimeMillis();
        Reminder candidate = ReminderLogic.snooze(current, snoozeAtMillis).withVersion(nextVersion);
        ReminderLogic.SchedulePlan plan = ReminderScheduler.schedule(context, candidate);
        if (plan == ReminderLogic.SchedulePlan.NO_SCHEDULE) {
            ReminderScheduler.cancelTombstoneCleanup(context, cleanup);
            return;
        }
        Reminder next;
        try {
            next = store.snooze(taskId, expectedVersion, snoozeAtMillis);
        } catch (Exception failure) {
            Reminder latest = store.find(taskId);
            if (latest == null || latest.version != nextVersion) {
                ReminderScheduler.cancel(context, taskId, nextVersion);
                ReminderScheduler.cancelTombstoneCleanup(context, cleanup);
                throw failure;
            }
            next = latest;
        }
        if (next == null || next.version != nextVersion) {
            ReminderScheduler.cancel(context, taskId, nextVersion);
            ReminderScheduler.cancelTombstoneCleanup(context, cleanup);
            return;
        }
        // The new retry was armed before the old generation was retired.
        reconcileTombstones(context, store);
    }

    private boolean isSystemRescheduleAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || ACTION_EXACT_PERMISSION_CHANGED.equals(action);
    }

    private void reconcileAndReschedule(Context context, EncryptedReminderStore store) throws Exception {
        reconcileTombstones(context, store);
        List<Reminder> reminders = store.rebaseForCurrentTimezone();
        List<Task> tasks;
        try {
            tasks = new EncryptedTaskStore(context).load();
        } catch (Exception exception) {
            // Keep/re-arm reminders: a task-store read failure is not a cancellation request.
            ReminderScheduler.rescheduleAll(context, reminders);
            throw exception;
        }
        Map<String, Task> tasksById = new HashMap<>();
        for (Task task : tasks) tasksById.put(task.id, task);
        List<Reminder> active = new ArrayList<>();
        for (Reminder reminder : reminders) {
            Task task = tasksById.get(reminder.taskId);
            if (!ReminderLogic.belongsToOpenTask(reminder, task)) {
                cancelReminder(context, store, reminder.taskId, reminder.version);
            } else {
                Reminder current = reminder;
                if (!task.title.equals(reminder.taskTitle)) {
                    store.updateTaskTitle(task.id, task.title);
                    current = reminder.withTitle(task.title);
                }
                active.add(current);
            }
        }
        active = reconcilePendingNotifications(context, store, active);
        ReminderScheduler.rescheduleAll(context, active);
    }

    /** Retry durable pending posts and finish durable cancellation cleanup at app start/resume. */
    static List<Reminder> reconcilePendingNotifications(Context context, EncryptedReminderStore store,
                                                        List<Reminder> reminders) throws Exception {
        if (context == null || store == null || reminders == null) return reminders;
        synchronized (ReminderDeliveryLock.LOCK) {
            Context appContext = context.getApplicationContext();
            reconcileTombstones(appContext, store);
            for (Reminder reminder : reminders) {
                if (reminder != null) ReminderScheduler.cancelLegacyNotification(appContext, reminder.taskId);
            }
            ReminderReceiver receiver = new ReminderReceiver();
            for (Reminder reminder : reminders) {
                Reminder current = store.find(reminder.taskId);
                if (current == null) continue;
                if (current.deliveryPending && ReminderScheduler.notificationsEnabled(appContext)) {
                    try {
                        receiver.postPendingNotification(appContext, store, current.taskId, current.version);
                    } catch (Exception ignored) {
                        // Leave pending state and its repeating retry alarm intact.
                    }
                } else if (current.delivered) {
                    ReminderScheduler.cancel(appContext, current.taskId, current.version);
                }
            }
            List<Reminder> currentRecords = new ArrayList<>();
            for (Reminder reminder : reminders) {
                Reminder current = store.find(reminder.taskId);
                if (current != null) currentRecords.add(current);
            }
            return currentRecords;
        }
    }

    private void postPendingNotification(Context context, EncryptedReminderStore store,
                                         String taskId, long expectedVersion) throws Exception {
        Reminder pending = store.find(taskId);
        if (!ReminderLogic.isCurrentGeneration(pending, expectedVersion)) {
            cleanStaleGeneration(context, store, taskId, expectedVersion);
            return;
        }
        if (pending.delivered) {
            ReminderScheduler.cancel(context, taskId, expectedVersion);
            return;
        }
        if (!pending.deliveryPending) return;
        Task task = findTask(context, taskId);
        if (!ReminderLogic.shouldDeliverForTask(pending, task)) {
            cancelReminder(context, store, taskId, expectedVersion);
            return;
        }
        if (!task.title.equals(pending.taskTitle)) {
            store.updateTaskTitle(taskId, expectedVersion, task.title);
            pending = pending.withTitle(task.title);
        }
        if (!ReminderScheduler.notificationsEnabled(context)) return;
        if (!postNotification(context, pending)) return;

        // Notify uses the same tag/ID on retries. Persist completion only after Android accepts the post.
        Reminder delivered = store.markDelivered(taskId, expectedVersion);
        if (delivered != null) {
            ReminderScheduler.cancel(context, taskId, expectedVersion);
            return;
        }
        Reminder latest = store.find(taskId);
        ReminderTombstone tombstone = store.findTombstone(taskId, expectedVersion);
        if (ReminderLogic.tombstoneMatches(tombstone, taskId, expectedVersion)) {
            ensureCleanupThenFinish(context, store, tombstone);
        } else if (latest != null && latest.version == expectedVersion && latest.delivered) {
            // A second idempotent path already committed this same post. Keep its stable notification.
            ReminderScheduler.cancel(context, taskId, expectedVersion);
        }
        // A stale receiver never cancels a newer generation's notification.
    }

    static void reconcileTombstones(Context context, EncryptedReminderStore store) throws Exception {
        for (ReminderTombstone tombstone : store.loadTombstones()) {
            ensureCleanupThenFinish(context, store, tombstone);
        }
    }

    private static void ensureCleanupThenFinish(Context context, EncryptedReminderStore store,
                                                ReminderTombstone tombstone) throws Exception {
        if (!ReminderScheduler.scheduleTombstoneCleanup(context, tombstone)) return;
        cleanTombstone(context, store, tombstone);
    }

    private static void cleanTombstone(Context context, EncryptedReminderStore store,
                                       ReminderTombstone tombstone) throws Exception {
        // Version-specific identity: old cleanup cannot cancel a replacement reminder's notification.
        ReminderScheduler.cancel(context, tombstone.taskId, tombstone.cancelledVersion);
        ReminderScheduler.cancelNotification(context, tombstone.taskId, tombstone.cancelledVersion);
        store.removeTombstone(tombstone);
        ReminderScheduler.cancelTombstoneCleanup(context, tombstone);
    }

    /** Persist cancellation before alarm/notification removal; repeating cleanup survives a process stop. */
    static boolean cancelReminder(Context context, EncryptedReminderStore store,
                                  String taskId, long expectedVersion) throws Exception {
        synchronized (ReminderDeliveryLock.LOCK) {
            Reminder current = store.find(taskId);
            if (current == null || (expectedVersion > 0 && current.version != expectedVersion)) return false;
            ReminderTombstone planned = store.plannedCancellation(taskId, current.version);
            if (planned == null) return false;
            if (!ReminderScheduler.scheduleTombstoneCleanup(context, planned)) {
                throw new IOException("A safe cancellation retry could not be scheduled; the reminder was left unchanged.");
            }
            ReminderTombstone tombstone = store.cancel(taskId, current.version);
            if (tombstone == null) {
                ReminderScheduler.cancelTombstoneCleanup(context, planned);
                return false;
            }
            if (tombstone.revision != planned.revision) {
                ReminderScheduler.cancelTombstoneCleanup(context, planned);
                if (!ReminderScheduler.scheduleTombstoneCleanup(context, tombstone)) {
                    throw new IOException("Reminder cancellation committed, but its exact cleanup retry could not be armed.");
                }
            }
            try {
                cleanTombstone(context, store, tombstone);
            } catch (Exception failure) {
                // The repeating cleanup remains scheduled; never erase the tombstone on a partial cancel.
                throw failure;
            }
            return true;
        }
    }

    private static void cleanStaleGeneration(Context context, EncryptedReminderStore store,
                                             String taskId, long version) throws Exception {
        ReminderTombstone tombstone = store.findTombstone(taskId, version);
        if (ReminderLogic.tombstoneMatches(tombstone, taskId, version)) {
            ensureCleanupThenFinish(context, store, tombstone);
        } else {
            // Only remove this generation's obsolete alarms; notification cancellation requires its tombstone.
            ReminderScheduler.cancel(context, taskId, version);
        }
    }

    private Task findTask(Context context, String taskId) throws Exception {
        for (Task task : new EncryptedTaskStore(context).load()) {
            if (task.id.equals(taskId)) return task;
        }
        return null;
    }

    private boolean postNotification(Context context, Reminder reminder) {
        if (!ReminderScheduler.notificationsEnabled(context)) return false;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
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
                        reminder.taskId, reminder.version)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                        "Cancel reminder", ReminderScheduler.actionPendingIntent(context, ACTION_CANCEL,
                        reminder.taskId, reminder.version)).build());
        manager.notify(ReminderScheduler.notificationTag(reminder.taskId, reminder.version),
                ReminderScheduler.notificationId(reminder.taskId, reminder.version), builder.build());
        return true;
    }
}

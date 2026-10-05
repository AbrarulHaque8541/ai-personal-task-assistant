package com.cue.daymark;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ReminderLogicSmoke {
    private ReminderLogicSmoke() { }

    public static void main(String[] args) {
        permissionDecisionsFailClosedAndDegrade();
        dozePlanUsesIdleApisWithoutClaimingExactDelivery();
        dstGapsAreRejectedAndBothOverlapOffsetsMatchTheirPreview();
        absoluteReminderBoundsAreEpochMillisBoundsWithoutShortHorizon();
        timezoneAndRebootRestoreTheSelectedInstant();
        notificationPostingSurvivesEveryCrashWindow();
        cancellationAndSnoozeUpdateOnlyTheTargetReminder();
        taskLifecycleBlocksStaleReminders();
        soundChannelsAreStableAndDistinct();
        System.out.println("ReminderLogicSmoke: DST, bounds, recovery, and reminder policy checks passed.");
    }

    private static void permissionDecisionsFailClosedAndDegrade() {
        assert ReminderLogic.needsNotificationRuntimePermission(33);
        assert !ReminderLogic.needsNotificationRuntimePermission(32);
        assert ReminderLogic.needsExactAlarmSpecialAccess(31);
        assert !ReminderLogic.needsExactAlarmSpecialAccess(30);
        assert ReminderLogic.shouldOfferExactAccess(35, false);
        assert !ReminderLogic.shouldOfferExactAccess(35, true);
        assert !ReminderLogic.shouldOfferExactAccess(30, false);
        assert ReminderLogic.needsInexactRevocationFallback(35, true);
        assert !ReminderLogic.needsInexactRevocationFallback(35, false);
        assert !ReminderLogic.needsInexactRevocationFallback(30, true);
        assert ReminderLogic.schedulePlan(false, true) == ReminderLogic.SchedulePlan.NO_SCHEDULE
                : "notification denial must not create a scheduled notification";
        assert ReminderLogic.schedulePlan(true, false) == ReminderLogic.SchedulePlan.INEXACT_ALLOW_WHILE_IDLE
                : "exact-access denial must fall back to inexact timing";
        assert ReminderLogic.schedulePlan(true, true) == ReminderLogic.SchedulePlan.EXACT_ALLOW_WHILE_IDLE;
    }

    private static void dozePlanUsesIdleApisWithoutClaimingExactDelivery() {
        assert ReminderLogic.usesAllowWhileIdle(ReminderLogic.SchedulePlan.EXACT_ALLOW_WHILE_IDLE);
        assert ReminderLogic.usesAllowWhileIdle(ReminderLogic.SchedulePlan.INEXACT_ALLOW_WHILE_IDLE);
        assert !ReminderLogic.usesAllowWhileIdle(ReminderLogic.SchedulePlan.NO_SCHEDULE);
        assert ReminderLogic.MIN_IDLE_ALARM_INTERVAL_MILLIS == 9L * 60L * 1000L
                : "Android documents a per-app minimum interval for allow-while-idle alarms in Doze";
        assert ReminderLogic.schedulePlan(true, false) != ReminderLogic.SchedulePlan.EXACT_ALLOW_WHILE_IDLE
                : "inexact fallback must not be described as exact";
    }

    private static void dstGapsAreRejectedAndBothOverlapOffsetsMatchTheirPreview() {
        ZoneId newYork = ZoneId.of("America/New_York");
        long now = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
        LocalDateTime gap = LocalDateTime.of(2026, 3, 8, 2, 30);
        assert ReminderLogic.resolveLocalDateTime(gap, newYork).isEmpty()
                : "a nonexistent spring-forward wall time has no candidate and must not be shifted";
        boolean gapRejected = false;
        try {
            ReminderLogic.atLocalDateTime("gap", "DST gap", gap, newYork, null, now, null);
        } catch (IllegalArgumentException expected) {
            gapRejected = expected.getMessage().contains("does not exist");
        }
        assert gapRejected : "gap rejection should be clear to the user";

        LocalDateTime overlap = LocalDateTime.of(2026, 11, 1, 1, 30);
        List<ReminderLogic.ResolvedDateTime> candidates = ReminderLogic.resolveLocalDateTime(overlap, newYork);
        assert candidates.size() == 2 : "fall-back time must offer both valid occurrences";
        assert candidates.get(0).offset.equals(ZoneOffset.ofHours(-4));
        assert candidates.get(1).offset.equals(ZoneOffset.ofHours(-5));
        assert candidates.get(0).triggerAtMillis + 60L * 60L * 1000L == candidates.get(1).triggerAtMillis;

        boolean implicitChoiceRejected = false;
        try {
            ReminderLogic.atLocalDateTime("overlap", "DST overlap", overlap, newYork, null, now, null);
        } catch (IllegalArgumentException expected) {
            implicitChoiceRejected = expected.getMessage().contains("occurs twice");
        }
        assert implicitChoiceRejected : "overlap must require an explicit offset choice";

        DateTimeFormatter previewFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a");
        for (ReminderLogic.ResolvedDateTime candidate : candidates) {
            Reminder reminder = ReminderLogic.fromResolvedDateTime(
                    "overlap", "DST overlap", candidate, now, null);
            String preview = candidate.choiceLabel(previewFormat);
            ZonedDateTime actual = Instant.ofEpochMilli(reminder.triggerAtMillis).atZone(newYork);
            assert reminder.triggerAtMillis == candidate.triggerAtMillis
                    : "the scheduled epoch must equal the previewed choice";
            assert reminder.zoneId.equals(newYork.getId());
            assert reminder.offsetSeconds == candidate.offset.getTotalSeconds();
            assert actual.toLocalDateTime().equals(candidate.localDateTime)
                    : "rendered local time must equal the selected wall time";
            assert actual.getOffset().equals(candidate.offset)
                    : "rendered offset must equal the explicitly selected offset";
            assert preview.contains(ReminderLogic.formatUtcOffset(candidate.offset));
            assert preview.contains(newYork.getId());
            assert reminder.isValid();
        }
        assert candidates.get(0).choiceLabel(previewFormat).startsWith("First occurrence");
        assert candidates.get(1).choiceLabel(previewFormat).startsWith("Second occurrence");
    }

    private static void absoluteReminderBoundsAreEpochMillisBoundsWithoutShortHorizon() {
        Instant maxMillis = Instant.ofEpochMilli(Long.MAX_VALUE);
        LocalDateTime maxLocal = LocalDateTime.ofInstant(maxMillis, ZoneOffset.UTC);
        Reminder atMax = ReminderLogic.atLocalDateTime("boundary", "Maximum supported", maxLocal,
                ZoneOffset.UTC, ZoneOffset.UTC, 1L, null);
        assert atMax.triggerAtMillis == Long.MAX_VALUE;
        assert atMax.isValid() : "the greatest representable future epoch-millisecond instant remains valid";

        LocalDateTime outsideLocal = LocalDateTime.ofInstant(maxMillis.plusMillis(1), ZoneOffset.UTC);
        boolean rejectedOutside = false;
        try {
            ReminderLogic.atLocalDateTime("outside", "Outside supported", outsideLocal,
                    ZoneOffset.UTC, ZoneOffset.UTC, 1L, null);
        } catch (IllegalArgumentException expected) {
            rejectedOutside = expected.getMessage().contains("supported scheduling range");
        }
        assert rejectedOutside : "one millisecond beyond Long.MAX_VALUE must fail clearly, not wrap";

        long fallbackBoundary = Long.MAX_VALUE - ReminderLogic.MIN_IDLE_ALARM_INTERVAL_MILLIS;
        assert ReminderLogic.revocationFallbackAtMillis(fallbackBoundary) == Long.MAX_VALUE;
        assert ReminderLogic.revocationFallbackAtMillis(fallbackBoundary + 1L) == null
                : "an unrepresentable later fallback is omitted instead of overflowing or firing early";
        assert ReminderLogic.remainingMinutesCeiling(Long.MAX_VALUE, Long.MIN_VALUE) > 0
                : "display rounding must not overflow for distant representable instants";

        boolean timerOverflowRejected = false;
        try {
            ReminderLogic.afterMinutes("timer-overflow", "Timer", 1, Long.MAX_VALUE - 30L, null);
        } catch (IllegalArgumentException expected) {
            timerOverflowRejected = expected.getMessage().contains("supported scheduling range");
        }
        assert timerOverflowRejected : "relative timer addition must be overflow-checked";
    }

    private static void timezoneAndRebootRestoreTheSelectedInstant() {
        long now = Instant.parse("2026-10-06T00:00:00Z").toEpochMilli();
        LocalDateTime local = LocalDateTime.of(2026, 10, 7, 9, 15);
        Reminder dateTime = ReminderLogic.atLocalDateTime("task-1", "Review", local,
                ZoneId.of("Asia/Kolkata"), ZoneOffset.ofHoursMinutes(5, 30), now, "content://tone/a");
        Reminder afterZoneChange = ReminderLogic.afterTimezoneChange(dateTime, ZoneId.of("America/New_York"));
        assert afterZoneChange.triggerAtMillis == dateTime.triggerAtMillis
                : "a device-zone change must not reinterpret the originally selected instant";
        assert afterZoneChange.zoneId.equals("Asia/Kolkata");
        assert afterZoneChange.offsetSeconds == 5 * 60 * 60 + 30 * 60;
        assert ReminderLogic.shouldRestoreAfterReboot(dateTime);

        Reminder timer = ReminderLogic.afterMinutes("task-2", "Call", 30, now, null);
        Reminder timerAfterZoneChange = ReminderLogic.afterTimezoneChange(timer, ZoneId.of("Pacific/Auckland"));
        assert timerAfterZoneChange.triggerAtMillis == timer.triggerAtMillis
                : "relative timers keep their persisted deadline across zone changes";
        assert ReminderLogic.shouldRestoreAfterReboot(timer);
        assert !ReminderLogic.shouldRestoreAfterReboot(timer.withDelivered(true));
    }

    private static void notificationPostingSurvivesEveryCrashWindow() {
        long now = 1_800_000_000_000L;
        Reminder scheduled = ReminderLogic.afterMinutes("crash-task", "Crash test", 1, now, null);
        FakeNotificationSink notifications = new FakeNotificationSink();

        // Crash before the durable pending-state write: the stored scheduled record is re-armed.
        assert ReminderLogic.deliveryRecoveryAction(scheduled) == ReminderLogic.DeliveryRecoveryAction.REARM_ALARM;
        Reminder pending = ReminderLogic.beginDelivery(scheduled);
        assert pending != null && pending.deliveryPending && !pending.delivered;
        assert ReminderLogic.deliveryRecoveryAction(pending) == ReminderLogic.DeliveryRecoveryAction.POST_NOTIFICATION;

        // Crash after pending is durable but before posting: restart posts, then marks delivered.
        Reminder afterPendingOnly = recoverPendingOnRestart(pending, notifications);
        assert afterPendingOnly.delivered && !afterPendingOnly.deliveryPending;
        assert notifications.active.size() == 1 && notifications.postCalls == 1;

        // Crash after NotificationManager accepted the post but before delivered-state persistence.
        Reminder pendingAgain = ReminderLogic.beginDelivery(scheduled);
        notifications = new FakeNotificationSink();
        notifications.post(pendingAgain);
        assert notifications.active.size() == 1 && pendingAgain.deliveryPending;
        Reminder afterPostBeforeCommit = recoverPendingOnRestart(pendingAgain, notifications);
        assert afterPostBeforeCommit.delivered && !afterPostBeforeCommit.deliveryPending;
        assert notifications.active.size() == 1 : "same stable tag/id replaces, not duplicates, the notification";
        assert notifications.postCalls == 2 : "restart safely retries the pending post";

        // Crash after delivered-state persistence: the already-posted system notification is not reposted.
        int callsBeforeRestart = notifications.postCalls;
        assert ReminderLogic.deliveryRecoveryAction(afterPostBeforeCommit) == ReminderLogic.DeliveryRecoveryAction.NONE;
        Reminder afterDeliveredCommit = recoverPendingOnRestart(afterPostBeforeCommit, notifications);
        assert afterDeliveredCommit.delivered;
        assert notifications.postCalls == callsBeforeRestart && notifications.active.size() == 1;

        // A denied or temporarily unavailable post leaves the durable pending record retryable.
        Reminder pendingWhileDenied = ReminderLogic.beginDelivery(scheduled);
        FakeNotificationSink denied = new FakeNotificationSink();
        denied.enabled = false;
        Reminder unchanged = recoverPendingOnRestart(pendingWhileDenied, denied);
        assert unchanged.deliveryPending && !unchanged.delivered && denied.active.isEmpty();
        denied.enabled = true;
        Reminder afterGrant = recoverPendingOnRestart(unchanged, denied);
        assert afterGrant.delivered && denied.active.size() == 1;

        assert ReminderLogic.notificationTag("crash-task").equals(ReminderLogic.notificationTag("crash-task"));
        assert ReminderLogic.notificationId("crash-task") == ReminderLogic.notificationId("crash-task");
        assert !ReminderLogic.notificationTag("crash-task").equals(ReminderLogic.notificationTag("other-task"));
    }

    private static Reminder recoverPendingOnRestart(Reminder stored, FakeNotificationSink notifications) {
        if (ReminderLogic.deliveryRecoveryAction(stored) != ReminderLogic.DeliveryRecoveryAction.POST_NOTIFICATION) {
            return stored;
        }
        if (!notifications.enabled) return stored;
        notifications.post(stored);
        return ReminderLogic.completeDelivery(stored);
    }

    private static final class FakeNotificationSink {
        final Map<String, Integer> active = new HashMap<>();
        boolean enabled = true;
        int postCalls;

        void post(Reminder reminder) {
            postCalls++;
            String key = ReminderLogic.notificationTag(reminder.taskId)
                    + "#" + ReminderLogic.notificationId(reminder.taskId);
            active.put(key, ReminderLogic.notificationId(reminder.taskId));
        }
    }

    private static void cancellationAndSnoozeUpdateOnlyTheTargetReminder() {
        long now = 1_800_000_000_000L;
        Reminder first = ReminderLogic.afterMinutes("task-1", "Review", 30, now, null).withDelivered(true);
        Reminder second = ReminderLogic.afterMinutes("task-2", "Call", 45, now, null);
        List<Reminder> reminders = new ArrayList<>(Arrays.asList(first, second));
        assert ReminderLogic.removeForTask(reminders, "task-1");
        assert reminders.size() == 1 && reminders.get(0).taskId.equals("task-2");
        assert !ReminderLogic.removeForTask(reminders, "task-1");

        Reminder snoozed = ReminderLogic.snooze(first, now);
        assert snoozed.triggerAtMillis == now + 10L * 60L * 1000L;
        assert Reminder.MODE_TIMER.equals(snoozed.mode);
        assert snoozed.localDateTime == null && snoozed.zoneId == null && snoozed.offsetSeconds == null;
        assert !snoozed.delivered && !snoozed.deliveryPending;
        assert ReminderLogic.shouldRestoreAfterReboot(snoozed);

        boolean rejected = false;
        try {
            ReminderLogic.snooze(second, now);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assert rejected : "only a delivered reminder may be snoozed";
    }

    private static void taskLifecycleBlocksStaleReminders() {
        long now = 1_800_000_000_000L;
        Reminder reminder = ReminderLogic.afterMinutes("task-1", "Review", 30, now, null);
        Task open = new Task("task-1", "Review", null, "medium", false, "created", "updated");
        Task completed = open.withCompleted(true, "completed");
        Task different = new Task("task-2", "Other", null, "medium", false, "created", "updated");

        assert ReminderLogic.belongsToOpenTask(reminder, open);
        assert ReminderLogic.shouldDeliverForTask(reminder, open);
        assert !ReminderLogic.belongsToOpenTask(reminder, completed);
        assert !ReminderLogic.shouldDeliverForTask(reminder, completed);
        assert !ReminderLogic.belongsToOpenTask(reminder, different);
        assert !ReminderLogic.belongsToOpenTask(reminder, null);
        assert !ReminderLogic.shouldDeliverForTask(reminder.withDelivered(true), open);
    }

    private static void soundChannelsAreStableAndDistinct() {
        String first = ReminderLogic.notificationChannelId("content://tone/one");
        assert first.equals(ReminderLogic.notificationChannelId("content://tone/one"));
        assert !first.equals(ReminderLogic.notificationChannelId("content://tone/two"));
        assert !first.equals(ReminderLogic.notificationChannelId(null));
    }
}

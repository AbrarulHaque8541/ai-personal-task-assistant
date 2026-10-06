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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class ReminderLogicSmoke {
    private ReminderLogicSmoke() { }

    public static void main(String[] args) {
        permissionDecisionsFailClosedAndDegrade();
        dozePlanUsesIdleApisWithoutClaimingExactDelivery();
        dstGapsAreRejectedAndBothOverlapOffsetsMatchTheirPreview();
        supportedDatePickerRangeIsEnforcedAtAndBeyondItsBoundary();
        timezoneAndRebootRestoreTheSelectedInstant();
        notificationPostingSurvivesEveryCrashWindow();
        replacementCrashWindowsKeepTheOldOrNewGenerationRecoverable();
        cancellationAndSnoozeUpdateOnlyTheTargetReminder();
        cancellationRecoveryIsDurableAndGenerationScoped();
        concurrentCancelAndRecoveryAreSerialized();
        taskLifecycleBlocksStaleReminders();
        legacyNotificationIdentityIsIsolatedFromNewGenerations();
        soundChannelsAreStableAndDistinct();
        System.out.println("ReminderLogicSmoke: DST, supported range, crash recovery, cancellation races, and reminder policy checks passed.");
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
        assert ReminderLogic.DELIVERY_RETRY_INTERVAL_MILLIS == 15L * 60L * 1000L
                : "retry wakeups use the documented inexact 15-minute interval";
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
        long shiftedGapMillis = ReminderLogic.absoluteEpochMillis(gap, ZoneOffset.ofHours(-5));
        Reminder persistedGap = new Reminder("gap", "DST gap", Reminder.MODE_LOCAL_DATE_TIME,
                gap.toString(), newYork.getId(), -5 * 60 * 60, shiftedGapMillis,
                null, false, false, 1L);
        assert !persistedGap.isValid() : "a persisted spring-gap time must not become valid through a manually supplied offset";

        LocalDateTime overlap = LocalDateTime.of(2026, 11, 1, 1, 30);
        List<ReminderLogic.ResolvedDateTime> candidates = ReminderLogic.resolveLocalDateTime(overlap, newYork);
        assert candidates.size() == 2 : "fall-back time must offer both valid occurrences";
        assert candidates.get(0).offset.equals(ZoneOffset.ofHours(-4));
        assert candidates.get(1).offset.equals(ZoneOffset.ofHours(-5));
        assert candidates.get(0).triggerAtMillis + 60L * 60L * 1000L == candidates.get(1).triggerAtMillis;
        long invalidOverlapMillis = ReminderLogic.absoluteEpochMillis(overlap, ZoneOffset.ofHours(-6));
        Reminder invalidOverlap = new Reminder("overlap", "DST overlap", Reminder.MODE_LOCAL_DATE_TIME,
                overlap.toString(), newYork.getId(), -6 * 60 * 60, invalidOverlapMillis,
                null, false, false, 1L);
        assert !invalidOverlap.isValid() : "only the zone's two valid overlap offsets may be persisted";

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

    private static void supportedDatePickerRangeIsEnforcedAtAndBeyondItsBoundary() {
        long now = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
        LocalDateTime lastSupported = ReminderLogic.MAX_DATE_TIME_DATE.atTime(23, 59);
        Reminder atBoundary = ReminderLogic.atLocalDateTime("boundary", "Last supported day", lastSupported,
                ZoneOffset.UTC, ZoneOffset.UTC, now, null).withVersion(1L);
        assert atBoundary.isValid() : "December 31, 2100 is included in the supported date-picker range";
        assert LocalDateTime.parse(atBoundary.localDateTime).toLocalDate().equals(ReminderLogic.MAX_DATE_TIME_DATE);

        LocalDateTime outside = lastSupported.plusMinutes(1);
        boolean rejectedByResolver = false;
        try {
            ReminderLogic.resolveLocalDateTime(outside, ZoneOffset.UTC);
        } catch (IllegalArgumentException expected) {
            rejectedByResolver = expected.getMessage().contains("December 31, 2100");
        }
        assert rejectedByResolver : "the first time after the published upper bound must fail clearly";

        long outsideMillis = ReminderLogic.absoluteEpochMillis(outside, ZoneOffset.UTC);
        Reminder rawOutside = new Reminder("outside", "Unsupported", Reminder.MODE_LOCAL_DATE_TIME,
                outside.toString(), ZoneOffset.UTC.getId(), 0, outsideMillis, null, false, false, 2L);
        assert !rawOutside.isValid() : "persisted date/time reminders beyond the UI limit must fail validation";

        boolean pastRejected = false;
        try {
            ReminderLogic.atLocalDateTime("past", "Past date", LocalDateTime.of(2025, 12, 31, 23, 59),
                    ZoneOffset.UTC, ZoneOffset.UTC, now, null);
        } catch (IllegalArgumentException expected) {
            pastRejected = expected.getMessage().contains("future");
        }
        assert pastRejected : "past dates within the calendar range remain rejected as non-future";

        long fallbackBoundary = Long.MAX_VALUE - ReminderLogic.MIN_IDLE_ALARM_INTERVAL_MILLIS;
        assert ReminderLogic.revocationFallbackAtMillis(fallbackBoundary) == Long.MAX_VALUE;
        assert ReminderLogic.revocationFallbackAtMillis(fallbackBoundary + 1L) == null
                : "an unrepresentable later fallback is omitted instead of overflowing or firing early";
        assert ReminderLogic.remainingMinutesCeiling(Long.MAX_VALUE, Long.MIN_VALUE) > 0
                : "display rounding must not overflow";
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
                : "a device-zone change must preserve the selected instant, not reinterpret wall-clock time";
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
        Reminder scheduled = ReminderLogic.afterMinutes("crash-task", "Crash test", 1, now, null).withVersion(7L);
        for (CrashPoint crash : CrashPoint.values()) {
            FakeDurableStore store = new FakeDurableStore(scheduled, 8L);
            FakeAlarmQueue alarms = new FakeAlarmQueue();
            alarms.scheduleRetryBeforePrimary(scheduled);
            assert alarms.scheduleOrder.equals(Arrays.asList("retry", "primary"))
                    : "retry must be scheduled before the primary one-shot alarm";
            FakeNotificationSink notifications = new FakeNotificationSink();
            assert alarms.retryArmed(scheduled) : "recovery must exist before the due one-shot is consumed";
            attemptDelivery(store, notifications, alarms, scheduled.version, crash,
                    scheduled.triggerAtMillis + 1L);
            assert alarms.retryArmed(scheduled)
                    : "the inexact retry remains armed until durable delivered-state commit is reconciled";

            recoverAfterProcessDeath(store, notifications, alarms,
                    scheduled.triggerAtMillis + ReminderLogic.DELIVERY_RETRY_INTERVAL_MILLIS + 1L);
            assert store.current != null && store.current.delivered && !store.current.deliveryPending
                    : "every crash window must converge to durable delivered state";
            assert notifications.active.size() == 1 : "retrying one generation must not create duplicate notifications";
            assert !alarms.retryArmed(scheduled) : "retry is canceled only after delivered-state commit";
            if (crash == CrashPoint.AFTER_NOTIFICATION_POST) {
                assert notifications.postCalls == 2 : "post-before-commit recovery reposts the identical key";
            }
        }

        Reminder same = scheduled.withDeliveryPending();
        assert ReminderLogic.notificationTag(same.taskId, same.version)
                .equals(ReminderLogic.notificationTag(scheduled.taskId, scheduled.version));
        assert ReminderLogic.notificationId(same.taskId, same.version)
                == ReminderLogic.notificationId(scheduled.taskId, scheduled.version);
        assert !ReminderLogic.notificationTag(scheduled.taskId, scheduled.version)
                .equals(ReminderLogic.notificationTag(scheduled.taskId, scheduled.version + 1L))
                : "each replacement generation has its own stable idempotency key";
        assert !ReminderLogic.notificationTag("other-task", scheduled.version)
                .equals(ReminderLogic.notificationTag(scheduled.taskId, scheduled.version));
    }

    private static void replacementCrashWindowsKeepTheOldOrNewGenerationRecoverable() {
        long now = 1_800_000_000_000L;
        Reminder old = ReminderLogic.afterMinutes("replace-task", "Old", 30, now, null)
                .withVersion(100L);
        Reminder replacement = ReminderLogic.afterMinutes("replace-task", "New", 1, now, null)
                .withVersion(102L);

        // Process death after the new retry is armed but before the replacement commit.
        FakeDurableStore beforeCommit = new FakeDurableStore(old, 101L);
        FakeAlarmQueue beforeCommitAlarms = new FakeAlarmQueue();
        beforeCommitAlarms.scheduleRetryBeforePrimary(old);
        beforeCommitAlarms.scheduleRetryBeforePrimary(replacement);
        FakeNotificationSink beforeCommitNotifications = new FakeNotificationSink();
        beforeCommitNotifications.post(old);
        if (!ReminderLogic.isCurrentGeneration(beforeCommit.current, replacement.version)) {
            beforeCommitAlarms.cancelGeneration(replacement.taskId, replacement.version);
        }
        assert beforeCommit.current.version == old.version && beforeCommitAlarms.retryArmed(old)
                : "an uncommitted replacement must not strand the old saved reminder";
        assert !beforeCommitAlarms.retryArmed(replacement) && beforeCommitNotifications.contains(old);

        // Process death after atomic replacement commit but before old notification cleanup.
        FakeDurableStore afterCommit = new FakeDurableStore(old, 101L);
        FakeAlarmQueue afterCommitAlarms = new FakeAlarmQueue();
        afterCommitAlarms.scheduleRetryBeforePrimary(old);
        afterCommitAlarms.scheduleRetryBeforePrimary(replacement);
        ReminderTombstone planned = afterCommit.plannedCancellation(old.taskId, old.version);
        afterCommitAlarms.armCleanup(planned);
        FakeNotificationSink afterCommitNotifications = new FakeNotificationSink();
        afterCommitNotifications.post(old);
        ReminderTombstone committed = afterCommit.replaceWith(replacement);
        assert committed != null && afterCommit.findTombstone(old.taskId, old.version) != null;
        assert afterCommitAlarms.retryArmed(replacement)
                : "the new generation stays recoverable while the old notification is being retired";
        cleanupCanceledGeneration(afterCommit, afterCommitNotifications, afterCommitAlarms, committed, old);
        assert afterCommit.current.version == replacement.version;
        assert afterCommitAlarms.retryArmed(replacement) && !afterCommitAlarms.retryArmed(old);
        assert afterCommitNotifications.active.isEmpty()
                : "the prior notification is removed without canceling the replacement retry";
        recoverAfterProcessDeath(afterCommit, afterCommitNotifications, afterCommitAlarms,
                replacement.triggerAtMillis + ReminderLogic.DELIVERY_RETRY_INTERVAL_MILLIS + 1L);
        assert afterCommit.current.delivered && afterCommitNotifications.contains(replacement)
                : "recovery completes the committed replacement after process death";
    }

    private enum CrashPoint {
        BEFORE_PENDING_COMMIT,
        AFTER_PENDING_COMMIT,
        AFTER_NOTIFICATION_POST,
        AFTER_DELIVERED_COMMIT
    }

    private static void attemptDelivery(FakeDurableStore store, FakeNotificationSink notifications,
                                        FakeAlarmQueue alarms, long expectedVersion,
                                        CrashPoint crash, long nowMillis) {
        Reminder current = store.current;
        if (!ReminderLogic.isCurrentGeneration(current, expectedVersion) || current.delivered) return;
        if (crash == CrashPoint.BEFORE_PENDING_COMMIT) return;
        Reminder pending = store.markPending(expectedVersion);
        if (pending == null || crash == CrashPoint.AFTER_PENDING_COMMIT) return;
        notifications.post(pending);
        if (crash == CrashPoint.AFTER_NOTIFICATION_POST) return;
        Reminder delivered = store.markDelivered(expectedVersion);
        if (delivered == null || crash == CrashPoint.AFTER_DELIVERED_COMMIT) return;
        alarms.cancelRetry(delivered);
    }

    private static void recoverAfterProcessDeath(FakeDurableStore store, FakeNotificationSink notifications,
                                                FakeAlarmQueue alarms, long nowMillis) {
        Reminder current = store.current;
        if (current == null) return;
        if (current.delivered) {
            alarms.cancelRetry(current);
            return;
        }
        if (!alarms.retryArmed(current)) alarms.scheduleRetryBeforePrimary(current);
        attemptDelivery(store, notifications, alarms, current.version, null, nowMillis);
    }

    private static void cancellationAndSnoozeUpdateOnlyTheTargetReminder() {
        long now = 1_800_000_000_000L;
        Reminder first = ReminderLogic.afterMinutes("task-1", "Review", 30, now, null).withVersion(1L).withDelivered(true);
        Reminder second = ReminderLogic.afterMinutes("task-2", "Call", 45, now, null).withVersion(2L);
        List<Reminder> reminders = new ArrayList<>(Arrays.asList(first, second));
        assert ReminderLogic.removeForTask(reminders, "task-1");
        assert reminders.size() == 1 && reminders.get(0).taskId.equals("task-2");
        assert !ReminderLogic.removeForTask(reminders, "task-1");

        Reminder snoozed = ReminderLogic.snooze(first, now).withVersion(3L);
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

    private static void cancellationRecoveryIsDurableAndGenerationScoped() {
        Reminder canceled = ReminderLogic.afterMinutes("cancel-me", "Canceled", 30, 1_800_000_000_000L, null)
                .withVersion(10L).withDelivered(true);
        Reminder other = ReminderLogic.afterMinutes("keep-me", "Keep", 45, 1_800_000_000_000L, null)
                .withVersion(11L).withDelivered(true);
        FakeNotificationSink notifications = new FakeNotificationSink();
        notifications.post(canceled);
        notifications.post(other);
        FakeAlarmQueue alarms = new FakeAlarmQueue();
        ReminderTombstone tombstone = new ReminderTombstone(canceled.taskId, canceled.version, 12L);
        FakeDurableStore store = new FakeDurableStore(null, 13L);
        store.persistTombstone(tombstone);
        alarms.armCleanup(tombstone);

        // Process death after durable tombstone commit, before any platform cancellation.
        assert store.findTombstone(canceled.taskId, canceled.version) != null;
        cleanupCanceledGeneration(store, notifications, alarms, tombstone, canceled);
        assert notifications.active.size() == 1 && notifications.contains(other)
                : "cancellation must not remove another reminder's notification";
        assert store.findTombstone(canceled.taskId, canceled.version) == null;
        assert !alarms.cleanupArmed(tombstone);

        // Idempotent cleanup after death between platform cancellation and journal removal.
        Reminder replacement = ReminderLogic.afterMinutes(canceled.taskId, "Replacement", 20,
                1_800_000_000_000L, null).withVersion(14L).withDelivered(true);
        notifications.post(replacement);
        ReminderTombstone oldGeneration = new ReminderTombstone(canceled.taskId, canceled.version, 15L);
        store.persistTombstone(oldGeneration);
        alarms.armCleanup(oldGeneration);
        notifications.cancel(canceled);
        cleanupCanceledGeneration(store, notifications, alarms, oldGeneration, canceled);
        assert notifications.contains(replacement)
                : "a stale tombstone may cancel only its own generation's stable key";
    }

    private static void cleanupCanceledGeneration(FakeDurableStore store, FakeNotificationSink notifications,
                                                  FakeAlarmQueue alarms, ReminderTombstone tombstone,
                                                  Reminder canceled) {
        if (store.findTombstone(tombstone.taskId, tombstone.cancelledVersion) == null) {
            alarms.cancelCleanup(tombstone);
            return;
        }
        alarms.cancelGeneration(canceled.taskId, canceled.version);
        notifications.cancel(canceled);
        store.removeTombstone(tombstone);
        alarms.cancelCleanup(tombstone);
    }

    private static void concurrentCancelAndRecoveryAreSerialized() {
        long now = 1_800_000_000_000L;
        Reminder scheduled = ReminderLogic.afterMinutes("race-task", "Race", 1, now, null).withVersion(30L);
        FakeDurableStore store = new FakeDurableStore(scheduled, 31L);
        FakeNotificationSink notifications = new FakeNotificationSink();
        FakeAlarmQueue alarms = new FakeAlarmQueue();
        alarms.scheduleRetryBeforePrimary(scheduled);
        CountDownLatch posted = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch cancelWaiting = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread recovery = new Thread(() -> {
            try {
                synchronized (ReminderDeliveryLock.LOCK) {
                    Reminder pending = store.markPending(scheduled.version);
                    assert pending != null;
                    notifications.post(pending);
                    posted.countDown();
                    await(allowCommit);
                    Reminder delivered = store.markDelivered(scheduled.version);
                    assert delivered != null : "the held recovery still owns its generation";
                    alarms.cancelRetry(delivered);
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "reminder-recovery");
        Thread cancel = new Thread(() -> {
            try {
                await(posted);
                cancelWaiting.countDown();
                synchronized (ReminderDeliveryLock.LOCK) {
                    ReminderTombstone planned = store.plannedCancellation(scheduled.taskId, scheduled.version);
                    assert planned != null;
                    alarms.armCleanup(planned);
                    ReminderTombstone committed = store.cancel(scheduled.version);
                    assert committed != null;
                    notifications.cancel(scheduled);
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "reminder-cancel");
        recovery.start();
        cancel.start();
        await(posted);
        await(cancelWaiting);
        allowCommit.countDown();
        join(recovery);
        join(cancel);
        if (failure.get() != null) throw new AssertionError("cancel/recovery interleaving failed", failure.get());
        assert store.current == null : "user cancellation wins after serialized notification commit";
        assert notifications.active.isEmpty() : "no notification remains after cancel and recovery interleave";
        assert !alarms.retryArmed(scheduled) : "cancel removes the exact generation's retry";
        ReminderTombstone tombstone = store.findTombstone(scheduled.taskId, scheduled.version);
        assert tombstone != null && alarms.cleanupArmed(tombstone)
                : "durable cleanup remains armed until tombstone reconciliation";
        cleanupCanceledGeneration(store, notifications, alarms, tombstone, scheduled);
        assert store.tombstones.isEmpty();

        // Opposite ordering: cancellation commits first; a late recovery cannot resurrect it.
        Reminder second = scheduled.withVersion(40L);
        FakeDurableStore canceledFirst = new FakeDurableStore(second, 41L);
        FakeNotificationSink emptyNotifications = new FakeNotificationSink();
        FakeAlarmQueue canceledAlarms = new FakeAlarmQueue();
        canceledAlarms.scheduleRetryBeforePrimary(second);
        synchronized (ReminderDeliveryLock.LOCK) {
            ReminderTombstone planned = canceledFirst.plannedCancellation(second.taskId, second.version);
            canceledAlarms.armCleanup(planned);
            ReminderTombstone committed = canceledFirst.cancel(second.version);
            cleanupCanceledGeneration(canceledFirst, emptyNotifications, canceledAlarms, committed, second);
        }
        synchronized (ReminderDeliveryLock.LOCK) {
            Reminder current = canceledFirst.current;
            if (ReminderLogic.isCurrentGeneration(current, second.version)) {
                emptyNotifications.post(canceledFirst.markPending(second.version));
            }
        }
        assert canceledFirst.current == null && emptyNotifications.active.isEmpty()
                : "a fired or recovered receiver must not resurrect a canceled reminder";
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

    private static void legacyNotificationIdentityIsIsolatedFromNewGenerations() {
        String taskId = "migration-task";
        assert ReminderLogic.legacyNotificationTag(taskId).equals("daymark.reminder:" + taskId);
        int legacyId = taskId.hashCode() & 0x7fffffff;
        assert ReminderLogic.legacyNotificationId(taskId) == (legacyId == 0 ? 1 : legacyId);
        assert !ReminderLogic.legacyNotificationTag(taskId).equals(ReminderLogic.notificationTag(taskId, 1L))
                : "legacy cleanup must not target a new generation's stable notification key";
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for reminder test barrier.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Reminder test thread was interrupted.", interrupted);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
            if (thread.isAlive()) throw new AssertionError("Reminder test thread did not finish.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Reminder test thread was interrupted.", interrupted);
        }
    }

    private static final class FakeDurableStore {
        Reminder current;
        long nextGeneration;
        final Map<String, ReminderTombstone> tombstones = new HashMap<>();

        FakeDurableStore(Reminder initial, long nextGeneration) {
            this.current = initial;
            this.nextGeneration = nextGeneration;
        }

        Reminder markPending(long expectedVersion) {
            if (!ReminderLogic.isCurrentGeneration(current, expectedVersion)) return null;
            current = ReminderLogic.beginDelivery(current);
            return current;
        }

        Reminder markDelivered(long expectedVersion) {
            if (!ReminderLogic.isCurrentGeneration(current, expectedVersion)) return null;
            Reminder delivered = ReminderLogic.completeDelivery(current);
            if (delivered != null) current = delivered;
            return delivered;
        }

        ReminderTombstone plannedCancellation(String taskId, long expectedVersion) {
            if (!ReminderLogic.isCurrentGeneration(current, expectedVersion)) return null;
            return new ReminderTombstone(taskId, current.version, nextGeneration);
        }

        ReminderTombstone cancel(long expectedVersion) {
            if (current == null || current.version != expectedVersion) return null;
            ReminderTombstone tombstone = new ReminderTombstone(current.taskId,
                    current.version, nextGeneration++);
            current = null;
            persistTombstone(tombstone);
            return tombstone;
        }

        ReminderTombstone replaceWith(Reminder replacement) {
            ReminderTombstone tombstone = null;
            if (current != null) {
                tombstone = new ReminderTombstone(current.taskId, current.version, nextGeneration++);
                persistTombstone(tombstone);
            }
            if (replacement.version != nextGeneration) {
                throw new AssertionError("Replacement test version must match the durable generation counter.");
            }
            current = replacement;
            nextGeneration++;
            return tombstone;
        }

        void persistTombstone(ReminderTombstone tombstone) {
            tombstones.put(tombstone.key(), tombstone);
        }

        ReminderTombstone findTombstone(String taskId, long version) {
            return tombstones.get(taskId + "\u0000" + version);
        }

        void removeTombstone(ReminderTombstone tombstone) {
            tombstones.remove(tombstone.key());
        }
    }

    private static final class FakeAlarmQueue {
        final Set<String> retries = new HashSet<>();
        final Set<String> cleanups = new HashSet<>();
        final List<String> scheduleOrder = new ArrayList<>();

        void scheduleRetryBeforePrimary(Reminder reminder) {
            retries.add(generationKey(reminder.taskId, reminder.version));
            scheduleOrder.add("retry");
            scheduleOrder.add("primary");
        }

        void cancelRetry(Reminder reminder) {
            retries.remove(generationKey(reminder.taskId, reminder.version));
        }

        boolean retryArmed(Reminder reminder) {
            return retries.contains(generationKey(reminder.taskId, reminder.version));
        }

        void cancelGeneration(String taskId, long version) {
            retries.remove(generationKey(taskId, version));
        }

        void armCleanup(ReminderTombstone tombstone) {
            cleanups.add(tombstone.taskId + "\u0000" + tombstone.cancelledVersion + "\u0000" + tombstone.revision);
        }

        boolean cleanupArmed(ReminderTombstone tombstone) {
            return cleanups.contains(tombstone.taskId + "\u0000" + tombstone.cancelledVersion + "\u0000" + tombstone.revision);
        }

        void cancelCleanup(ReminderTombstone tombstone) {
            cleanups.remove(tombstone.taskId + "\u0000" + tombstone.cancelledVersion + "\u0000" + tombstone.revision);
        }

        private static String generationKey(String taskId, long version) {
            return taskId + "\u0000" + version;
        }
    }

    private static final class FakeNotificationSink {
        final Map<String, Reminder> active = new HashMap<>();
        boolean enabled = true;
        int postCalls;

        void post(Reminder reminder) {
            if (reminder == null || !enabled) return;
            postCalls++;
            active.put(key(reminder), reminder);
        }

        void cancel(Reminder reminder) {
            if (reminder != null) active.remove(key(reminder));
        }

        boolean contains(Reminder reminder) {
            return active.containsKey(key(reminder));
        }

        private static String key(Reminder reminder) {
            return ReminderLogic.notificationTag(reminder.taskId, reminder.version)
                    + "#" + ReminderLogic.notificationId(reminder.taskId, reminder.version);
        }
    }
}

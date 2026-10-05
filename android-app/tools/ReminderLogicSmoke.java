package com.cue.daymark;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ReminderLogicSmoke {
    private ReminderLogicSmoke() { }

    public static void main(String[] args) {
        permissionDecisionsFailClosedAndDegrade();
        dozePlanUsesIdleApisWithoutClaimingExactDelivery();
        timezoneAndRebootRestorePersistedIntent();
        cancellationAndSnoozeUpdateOnlyTheTargetReminder();
        soundChannelsAreStableAndDistinct();
        System.out.println("ReminderLogicSmoke: all reminder policy checks passed.");
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

    private static void timezoneAndRebootRestorePersistedIntent() {
        long now = Instant.parse("2026-10-06T00:00:00Z").toEpochMilli();
        LocalDateTime local = LocalDateTime.of(2026, 10, 7, 9, 15);
        Reminder dateTime = ReminderLogic.atLocalDateTime("task-1", "Review", local,
                ZoneId.of("Asia/Kolkata"), now, "content://tone/a");
        Reminder shifted = ReminderLogic.afterTimezoneChange(dateTime, ZoneId.of("America/New_York"));
        assert LocalDateTime.parse(shifted.localDateTime).equals(local)
                : "date/time reminders preserve their selected local wall-clock time";
        assert shifted.triggerAtMillis != dateTime.triggerAtMillis
                : "a timezone change must rebase a local date/time reminder";
        assert ReminderLogic.shouldRestoreAfterReboot(dateTime);

        Reminder timer = ReminderLogic.afterMinutes("task-2", "Call", 30, now, null);
        Reminder timerAfterZoneChange = ReminderLogic.afterTimezoneChange(timer, ZoneId.of("Pacific/Auckland"));
        assert timerAfterZoneChange.triggerAtMillis == timer.triggerAtMillis
                : "relative timers keep their persisted wall-clock deadline across zone changes";
        assert ReminderLogic.shouldRestoreAfterReboot(timer)
                : "scheduled timers must be restored after reboot";
        assert !ReminderLogic.shouldRestoreAfterReboot(timer.withDelivered(true))
                : "delivered reminders must not fire again after reboot";

        LocalDateTime dstGap = LocalDateTime.of(2026, 3, 8, 2, 30);
        long beforeDst = Instant.parse("2026-03-07T00:00:00Z").toEpochMilli();
        Reminder dstReminder = ReminderLogic.atLocalDateTime("task-3", "DST", dstGap,
                ZoneId.of("America/New_York"), beforeDst, null);
        LocalDateTime resolved = LocalDateTime.ofInstant(Instant.ofEpochMilli(dstReminder.triggerAtMillis),
                ZoneId.of("America/New_York"));
        assert resolved.equals(LocalDateTime.of(2026, 3, 8, 3, 30))
                : "a nonexistent daylight-saving local time resolves forward using java.time zone rules";
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
        assert snoozed.localDateTime == null;
        assert !snoozed.delivered;
        assert ReminderLogic.shouldRestoreAfterReboot(snoozed);

        boolean rejected = false;
        try {
            ReminderLogic.snooze(second, now);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assert rejected : "only a delivered reminder may be snoozed";
    }

    private static void soundChannelsAreStableAndDistinct() {
        String first = ReminderLogic.notificationChannelId("content://tone/one");
        assert first.equals(ReminderLogic.notificationChannelId("content://tone/one"));
        assert !first.equals(ReminderLogic.notificationChannelId("content://tone/two"));
        assert !first.equals(ReminderLogic.notificationChannelId(null));
    }
}

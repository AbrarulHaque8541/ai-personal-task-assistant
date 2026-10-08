package com.cue.daymark;

import java.time.LocalDate;

/** JDK-only checks for due-date presets used by Add task UI. */
public final class TaskDuePresetsSmoke {
    private TaskDuePresetsSmoke() { }

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 10, 8);
        assertEqual("2026-10-08", TaskDuePresets.today(today));
        assertEqual("2026-10-09", TaskDuePresets.tomorrow(today));
        assertEqual("2026-10-15", TaskDuePresets.nextWeek(today));
        assertTrue(TaskDuePresets.isTimeOnly("09:30"));
        assertTrue(TaskDuePresets.isTimeOnly("23:59"));
        assertFalse(TaskDuePresets.isTimeOnly("24:00"));
        assertFalse(TaskDuePresets.isTimeOnly("9:30"));
        assertFalse(TaskDuePresets.isTimeOnly(null));
        System.out.println("TaskDuePresetsSmoke OK");
    }

    private static void assertEqual(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " got " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("expected false");
    }
}

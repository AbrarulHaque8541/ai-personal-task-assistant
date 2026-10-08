package com.cue.daymark;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Pure due-date shortcuts for the Add/Edit task UI (no Android UI). */
final class TaskDuePresets {
    private TaskDuePresets() { }

    static String today(LocalDate today) {
        return require(today).toString();
    }

    static String tomorrow(LocalDate today) {
        return require(today).plusDays(1).toString();
    }

    static String nextWeek(LocalDate today) {
        return require(today).plusDays(7).toString();
    }

    /** Optional due time HH:mm (24h). Null/blank = none. */
    static boolean isTimeOnly(String value) {
        if (value == null || value.length() != 5 || value.charAt(2) != ':') return false;
        try {
            int h = Integer.parseInt(value.substring(0, 2));
            int m = Integer.parseInt(value.substring(3, 5));
            return h >= 0 && h <= 23 && m >= 0 && m <= 59
                    && value.equals(String.format(java.util.Locale.ROOT, "%02d:%02d", h, m));
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    static String formatShort(LocalDate date) {
        return DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()).format(require(date));
    }

    private static LocalDate require(LocalDate today) {
        if (today == null) throw new IllegalArgumentException("today is required");
        return today;
    }
}

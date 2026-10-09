package com.cue.daymark;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses lightweight natural-language hints from quick-capture text.
 * Examples: "call mom tomorrow", "pay rent next week high", "meeting at 15:30 today".
 */
final class SmartTaskDraft {
    final String title;
    final String dueDate;
    final String dueTime;
    final String priority;

    SmartTaskDraft(String title, String dueDate, String priority) {
        this(title, dueDate, null, priority);
    }

    SmartTaskDraft(String title, String dueDate, String dueTime, String priority) {
        this.title = title == null ? "" : title;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.priority = priority == null ? "medium" : priority;
    }

    private static final Pattern TIME_24 = Pattern.compile(
            "(?i)(?:\bat\s+)?(\d{1,2}):(\d{2})\b");
    private static final Pattern TIME_12 = Pattern.compile(
            "(?i)(?:\bat\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b");

    static SmartTaskDraft parse(String raw, LocalDate today) {
        if (raw == null) return new SmartTaskDraft("", null, null, "medium");
        String title = raw.trim();
        String lower = title.toLowerCase(Locale.ROOT);
        String dueDate = null;
        String dueTime = null;
        String priority = "medium";
        LocalDate base = today == null ? LocalDate.now() : today;

        if (containsWord(lower, "today")) {
            dueDate = base.toString();
            title = stripWord(title, "today");
        } else if (containsWord(lower, "tomorrow")) {
            dueDate = base.plusDays(1).toString();
            title = stripWord(title, "tomorrow");
        } else if (lower.contains("next week")) {
            dueDate = base.plusWeeks(1).toString();
            title = title.replaceAll("(?i)next\\s+week", "").trim();
        }

        Matcher m12 = TIME_12.matcher(title);
        if (m12.find()) {
            int h = Integer.parseInt(m12.group(1));
            int min = m12.group(2) == null ? 0 : Integer.parseInt(m12.group(2));
            String ap = m12.group(3).toLowerCase(Locale.ROOT);
            if (h == 12) h = 0;
            if ("pm".equals(ap)) h += 12;
            if (h >= 0 && h <= 23 && min >= 0 && min <= 59) {
                dueTime = String.format(Locale.ROOT, "%02d:%02d", h, min);
                if (dueDate == null) dueDate = base.toString();
                title = (title.substring(0, m12.start()) + " " + title.substring(m12.end())).trim();
            }
        } else {
            Matcher m24 = TIME_24.matcher(title);
            if (m24.find()) {
                int h = Integer.parseInt(m24.group(1));
                int min = Integer.parseInt(m24.group(2));
                if (h >= 0 && h <= 23 && min >= 0 && min <= 59) {
                    dueTime = String.format(Locale.ROOT, "%02d:%02d", h, min);
                    if (dueDate == null) dueDate = base.toString();
                    title = (title.substring(0, m24.start()) + " " + title.substring(m24.end())).trim();
                }
            }
        }

        lower = title.toLowerCase(Locale.ROOT);
        if (containsWord(lower, "urgent") || containsWord(lower, "important") || containsWord(lower, "high")) {
            priority = "high";
            title = stripWord(stripWord(stripWord(title, "urgent"), "important"), "high");
        } else if (containsWord(lower, "low")) {
            priority = "low";
            title = stripWord(title, "low");
        }

        title = title.replaceAll("\\s{2,}", " ").replaceAll("^[,.:;\\-]+|[,.:;\\-]+$", "").trim();
        if (dueTime != null && !TaskDuePresets.isTimeOnly(dueTime)) dueTime = null;
        return new SmartTaskDraft(title, dueDate, dueTime, priority);
    }

    private static boolean containsWord(String lower, String word) {
        return lower.matches(".*\\b" + Pattern.quote(word) + "\\b.*");
    }

    private static String stripWord(String title, String word) {
        return title.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", "").trim();
    }
}

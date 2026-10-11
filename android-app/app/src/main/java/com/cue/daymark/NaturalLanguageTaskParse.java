package com.cue.daymark;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight natural-language capture for quick-add.
 * Examples: "call dentist tomorrow 4pm high #work"
 * Does not require network. Unknown tokens stay in the title.
 */
final class NaturalLanguageTaskParse {
    static final class Result {
        final String title;
        final String dueDate;
        final String dueTime;
        final String priority;
        final List<String> tags;

        Result(String title, String dueDate, String dueTime, String priority, List<String> tags) {
            this.title = title;
            this.dueDate = dueDate;
            this.dueTime = dueTime;
            this.priority = priority;
            this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
        }
    }

    private static final Pattern TAG = Pattern.compile("#([\\p{L}\\p{N}_-]{1,32})");
    private static final Pattern TIME_12 = Pattern.compile(
            "(?i)\\b([1-9]|1[0-2])(?::([0-5][0-9]))?\\s*(am|pm)\\b");
    private static final Pattern TIME_24 = Pattern.compile(
            "\\b([01]?[0-9]|2[0-3]):([0-5][0-9])\\b");

    private NaturalLanguageTaskParse() { }

    static Result parse(String raw, LocalDate today) {
        if (raw == null) {
            return new Result("", null, null, "medium", Collections.emptyList());
        }
        String working = raw.trim();
        if (working.isEmpty()) {
            return new Result("", null, null, "medium", Collections.emptyList());
        }
        if (today == null) {
            today = LocalDate.now();
        }

        Set<String> tags = new LinkedHashSet<>();
        Matcher tagMatcher = TAG.matcher(working);
        StringBuffer tagSb = new StringBuffer();
        while (tagMatcher.find()) {
            tags.add(tagMatcher.group(1).toLowerCase(Locale.ROOT));
            tagMatcher.appendReplacement(tagSb, " ");
        }
        tagMatcher.appendTail(tagSb);
        working = tagSb.toString();

        String priority = "medium";
        String lower = working.toLowerCase(Locale.ROOT);
        if (containsWord(lower, "high") || containsWord(lower, "urgent")) {
            priority = "high";
            working = removeWord(working, "(?i)\\b(high|urgent)\\b");
        } else if (containsWord(lower, "low")) {
            priority = "low";
            working = removeWord(working, "(?i)\\blow\\b");
        } else if (containsWord(lower, "medium") || containsWord(lower, "med")) {
            priority = "medium";
            working = removeWord(working, "(?i)\\b(medium|med)\\b");
        }

        String dueTime = null;
        Matcher t12 = TIME_12.matcher(working);
        if (t12.find()) {
            int hour = Integer.parseInt(t12.group(1));
            int minute = t12.group(2) == null ? 0 : Integer.parseInt(t12.group(2));
            String ampm = t12.group(3).toLowerCase(Locale.ROOT);
            if ("pm".equals(ampm) && hour < 12) hour += 12;
            if ("am".equals(ampm) && hour == 12) hour = 0;
            dueTime = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
            working = (working.substring(0, t12.start()) + " " + working.substring(t12.end())).trim();
        } else {
            Matcher t24 = TIME_24.matcher(working);
            if (t24.find()) {
                dueTime = String.format(Locale.ROOT, "%02d:%02d",
                        Integer.parseInt(t24.group(1)), Integer.parseInt(t24.group(2)));
                working = (working.substring(0, t24.start()) + " " + working.substring(t24.end())).trim();
            }
        }

        String dueDate = null;
        lower = working.toLowerCase(Locale.ROOT);
        if (containsWord(lower, "today")) {
            dueDate = today.toString();
            working = removeWord(working, "(?i)\\btoday\\b");
        } else if (containsWord(lower, "tomorrow")) {
            dueDate = today.plusDays(1).toString();
            working = removeWord(working, "(?i)\\btomorrow\\b");
        } else {
            LocalDate weekday = matchWeekday(lower, today);
            if (weekday != null) {
                dueDate = weekday.toString();
                working = removeWord(working,
                        "(?i)\\b(monday|mon|tuesday|tue|wednesday|wed|thursday|thu|friday|fri|saturday|sat|sunday|sun)\\b");
            }
        }

        // Time without date → today
        if (dueTime != null && dueDate == null) {
            dueDate = today.toString();
        }

        String title = working.replaceAll("\\s+", " ").trim();
        return new Result(title, dueDate, dueTime, priority, new ArrayList<>(tags));
    }

    /** Merge tags into notes so they remain searchable without a schema bump. */
    static String notesWithTags(String notes, List<String> tags) {
        String base = notes == null ? "" : notes.trim();
        if (tags == null || tags.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder();
        for (String tag : tags) {
            if (tag == null || tag.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append('#').append(tag);
        }
        if (sb.length() == 0) return base;
        if (base.isEmpty()) return sb.toString();
        if (base.contains("#")) return base;
        return base + "\n" + sb;
    }

    private static boolean containsWord(String lowerHaystack, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(lowerHaystack).find();
    }

    private static String removeWord(String input, String regex) {
        return input.replaceAll(regex, " ").replaceAll("\\s+", " ").trim();
    }

    private static LocalDate matchWeekday(String lower, LocalDate today) {
        DayOfWeek[] days = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };
        String[][] names = {
                {"monday", "mon"}, {"tuesday", "tue"}, {"wednesday", "wed"},
                {"thursday", "thu"}, {"friday", "fri"}, {"saturday", "sat"}, {"sunday", "sun"}
        };
        for (int i = 0; i < days.length; i++) {
            for (String name : names[i]) {
                if (containsWord(lower, name)) {
                    LocalDate d = today;
                    while (d.getDayOfWeek() != days[i]) {
                        d = d.plusDays(1);
                    }
                    if (d.equals(today) && !containsWord(lower, "today")) {
                        d = d.plusDays(7);
                    }
                    return d;
                }
            }
        }
        return null;
    }
}

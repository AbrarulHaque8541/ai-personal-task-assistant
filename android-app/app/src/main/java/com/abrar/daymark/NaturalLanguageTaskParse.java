package com.abrar.daymark;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline parser for the one-line quick-add capture field. */
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

    private static final Pattern TAG = Pattern.compile("#([A-Za-z0-9][A-Za-z0-9_-]{0,23})\\b");
    private static final Pattern TIME_12 = Pattern.compile(
            "(?i)\\b([1-9]|1[0-2])(?::([0-5][0-9]))?\\s*(am|pm)\\b");
    private static final Pattern TIME_24 = Pattern.compile("\\b([01]?[0-9]|2[0-3]):([0-5][0-9])\\b");
    private static final Pattern PRIORITY = Pattern.compile("(?i)\\b(high|urgent|important|medium|med|low)\\b");
    private static final Pattern WEEKDAY = Pattern.compile(
            "(?i)\\b(next\\s+)?(monday|mon|tuesday|tue|wednesday|wed|thursday|thu|friday|fri|saturday|sat|sunday|sun)\\b");

    private NaturalLanguageTaskParse() { }

    static Result parse(String raw, LocalDate today) {
        if (today == null) today = LocalDate.now();
        if (raw == null || raw.trim().isEmpty()) {
            return new Result("", null, null, "medium", Collections.emptyList());
        }

        String working = raw.trim();
        Set<String> tags = new LinkedHashSet<>();
        Matcher tagMatcher = TAG.matcher(working);
        StringBuffer withoutTags = new StringBuffer();
        while (tagMatcher.find()) {
            tags.add(tagMatcher.group(1).toLowerCase(Locale.ROOT));
            tagMatcher.appendReplacement(withoutTags, " ");
        }
        tagMatcher.appendTail(withoutTags);
        working = withoutTags.toString();

        String priority = "medium";
        Matcher priorityMatcher = PRIORITY.matcher(working);
        if (priorityMatcher.find()) {
            String token = priorityMatcher.group(1).toLowerCase(Locale.ROOT);
            if ("high".equals(token) || "urgent".equals(token) || "important".equals(token)) {
                priority = "high";
            } else if ("low".equals(token)) {
                priority = "low";
            }
            working = working.substring(0, priorityMatcher.start()) + " "
                    + working.substring(priorityMatcher.end());
        }

        String dueTime = null;
        Matcher time12 = TIME_12.matcher(working);
        if (time12.find()) {
            int hour = Integer.parseInt(time12.group(1));
            int minute = time12.group(2) == null ? 0 : Integer.parseInt(time12.group(2));
            String ampm = time12.group(3).toLowerCase(Locale.ROOT);
            if ("pm".equals(ampm) && hour < 12) hour += 12;
            if ("am".equals(ampm) && hour == 12) hour = 0;
            dueTime = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
            working = working.substring(0, time12.start()) + " " + working.substring(time12.end());
        } else {
            Matcher time24 = TIME_24.matcher(working);
            if (time24.find()) {
                dueTime = String.format(Locale.ROOT, "%02d:%02d",
                        Integer.parseInt(time24.group(1)), Integer.parseInt(time24.group(2)));
                working = working.substring(0, time24.start()) + " " + working.substring(time24.end());
            }
        }

        String dueDate = null;
        Matcher weekday = WEEKDAY.matcher(working);
        String lower = working.toLowerCase(Locale.ROOT);
        if (containsWord(lower, "today")) {
            dueDate = today.toString();
            working = removeWord(working, "(?i)\\btoday\\b");
        } else if (containsWord(lower, "tomorrow")) {
            dueDate = today.plusDays(1).toString();
            working = removeWord(working, "(?i)\\btomorrow\\b");
        } else if (containsWord(lower, "next week")) {
            dueDate = today.plusWeeks(1).toString();
            working = removeWord(working, "(?i)\\bnext\\s+week\\b");
        } else if (weekday.find()) {
            String dayToken = weekday.group(2).toLowerCase(Locale.ROOT);
            DayOfWeek target = dayOfWeek(dayToken);
            long days = (target.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            if (days == 0) days = 7;
            dueDate = today.plusDays(days).toString();
            working = working.substring(0, weekday.start()) + " " + working.substring(weekday.end());
        }
        if (dueTime != null && dueDate == null) dueDate = today.toString();

        String title = working.replaceAll("\\s+", " ")
                .replaceAll("^[,.:;\\-]+|[,.:;\\-]+$", "").trim();
        return new Result(title, dueDate, dueTime, priority, new ArrayList<>(tags));
    }

    private static boolean containsWord(String value, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(value).find();
    }

    private static String removeWord(String value, String regex) {
        return value.replaceAll(regex, " ").replaceAll("\\s+", " ").trim();
    }

    private static DayOfWeek dayOfWeek(String token) {
        if (token.startsWith("mon")) return DayOfWeek.MONDAY;
        if (token.startsWith("tue")) return DayOfWeek.TUESDAY;
        if (token.startsWith("wed")) return DayOfWeek.WEDNESDAY;
        if (token.startsWith("thu")) return DayOfWeek.THURSDAY;
        if (token.startsWith("fri")) return DayOfWeek.FRIDAY;
        if (token.startsWith("sat")) return DayOfWeek.SATURDAY;
        return DayOfWeek.SUNDAY;
    }
}

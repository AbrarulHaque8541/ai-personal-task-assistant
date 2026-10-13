package com.abrar.daymark;

import java.time.LocalDate;

public final class NaturalLanguageTaskParseSmoke {
    private NaturalLanguageTaskParseSmoke() { }

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 10, 11);
        NaturalLanguageTaskParse.Result parsed =
                NaturalLanguageTaskParse.parse("call dentist tomorrow 4pm high #work", today);
        check("call dentist".equals(parsed.title), "title: " + parsed.title);
        check("2026-10-12".equals(parsed.dueDate), "date: " + parsed.dueDate);
        check("16:00".equals(parsed.dueTime), "time: " + parsed.dueTime);
        check("high".equals(parsed.priority), "priority: " + parsed.priority);
        check(parsed.tags.size() == 1 && "work".equals(parsed.tags.get(0)), "tags: " + parsed.tags);

        parsed = NaturalLanguageTaskParse.parse("pay rent next week 09:30 #home", today);
        check("pay rent".equals(parsed.title), "24h title: " + parsed.title);
        check("2026-10-18".equals(parsed.dueDate), "next week date: " + parsed.dueDate);
        check("09:30".equals(parsed.dueTime), "24h time: " + parsed.dueTime);
        check("home".equals(parsed.tags.get(0)), "second tag: " + parsed.tags);

        parsed = NaturalLanguageTaskParse.parse("review notes Monday low", today);
        check("review notes".equals(parsed.title), "weekday title: " + parsed.title);
        check("2026-10-12".equals(parsed.dueDate), "weekday date: " + parsed.dueDate);
        check("low".equals(parsed.priority), "low priority: " + parsed.priority);

        parsed = NaturalLanguageTaskParse.parse("buy highlighter tomorrow", today);
        check("buy highlighter".equals(parsed.title), "unmatched words stay in title");
        check("2026-10-12".equals(parsed.dueDate), "date without time");

        parsed = NaturalLanguageTaskParse.parse("call clinic 12am", today);
        check("00:00".equals(parsed.dueTime), "midnight normalization");
        check("2026-10-11".equals(parsed.dueDate), "time-only defaults to today");
        System.out.println("PASS NaturalLanguageTaskParseSmoke (16 assertions)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

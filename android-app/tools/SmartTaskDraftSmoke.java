package com.cue.daymark;

import java.time.LocalDate;

/** JDK-only checks for smart quick-capture parsing. */
public final class SmartTaskDraftSmoke {
    private SmartTaskDraftSmoke() { }

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 10, 9);
        SmartTaskDraft a = SmartTaskDraft.parse("call mom tomorrow", today);
        check("call mom".equals(a.title), "title stripped tomorrow");
        check("2026-10-10".equals(a.dueDate), "tomorrow date");

        SmartTaskDraft b = SmartTaskDraft.parse("pay rent next week high", today);
        check("pay rent".equals(b.title), "title stripped next week high");
        check("2026-10-16".equals(b.dueDate), "next week");
        check("high".equals(b.priority), "priority high");

        SmartTaskDraft c = SmartTaskDraft.parse("standup at 3:30 pm today", today);
        check("2026-10-09".equals(c.dueDate), "today");
        check("15:30".equals(c.dueTime), "3:30 pm -> 15:30");

        SmartTaskDraft d = SmartTaskDraft.parse("ship at 09:05", today);
        check("09:05".equals(d.dueTime), "24h time");
        check("2026-10-09".equals(d.dueDate), "time implies today");

        System.out.println("SmartTaskDraftSmoke OK");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}

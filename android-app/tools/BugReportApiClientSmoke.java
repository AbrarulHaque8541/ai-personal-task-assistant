package com.cue.daymark;

/** Ensures API constants and composer stay aligned (no network). */
public final class BugReportApiClientSmoke {
    private BugReportApiClientSmoke() { }

    public static void main(String[] args) {
        if (!BugReportComposer.ISSUES_API.contains("/issues")) throw new AssertionError("api");
        if (!BugReportComposer.ISSUES_NEW.contains("issues/new")) throw new AssertionError("new");
        String body = BugReportComposer.buildCrashBody("1.0.5", 6, "java.lang.Demo");
        if (!body.contains("Crash")) throw new AssertionError("crash body");
        System.out.println("BugReportApiClientSmoke OK");
    }
}

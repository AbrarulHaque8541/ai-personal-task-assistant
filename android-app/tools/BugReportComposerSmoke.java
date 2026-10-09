package com.cue.daymark;

/** JDK-only smoke for bug report URL/body builders. */
public final class BugReportComposerSmoke {
    private BugReportComposerSmoke() { }

    public static void main(String[] args) {
        String url = BugReportComposer.githubNewIssueUrl("1.0.5", 6, "shake", "tasks", null, "hello");
        if (!url.startsWith("https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/new")) {
            throw new AssertionError("bad base: " + url);
        }
        if (!url.contains("title=") || !url.contains("body=")) {
            throw new AssertionError("missing query: " + url);
        }
        String body = BugReportComposer.buildBody("1.0.5", 6, "settings", "browser", "save failed", "x");
        if (!body.contains("versionCode 6") || !body.contains("AGENTS.md")) {
            throw new AssertionError("body incomplete");
        }
        System.out.println("BugReportComposerSmoke OK");
    }
}

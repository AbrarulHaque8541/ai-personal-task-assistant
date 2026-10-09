package com.cue.daymark;

import android.net.Uri;
import android.os.Build;

/** Builds issue title/body and a prefilled GitHub new-issue URL (no secrets). */
final class BugReportComposer {
    static final String OWNER = "AbrarulHaque8541";
    static final String REPO = "ai-personal-task-assistant";
    static final String ISSUES_NEW =
            "https://github.com/" + OWNER + "/" + REPO + "/issues/new";
    static final String ISSUES_API =
            "https://api.github.com/repos/" + OWNER + "/" + REPO + "/issues";

    private static String lastTrigger = "manual";

    private BugReportComposer() { }

    static void rememberTrigger(String trigger) {
        if (trigger != null && !trigger.trim().isEmpty()) lastTrigger = trigger.trim();
    }

    static String issueTitle(String trigger, String versionName) {
        String t = trigger == null ? "report" : trigger.trim();
        String v = versionName == null ? "?" : versionName;
        return "Bug: " + t + " · " + v;
    }

    static String githubNewIssueUrl(String versionName, int versionCode, String trigger,
                                    String screen, String storageFailure, String draftHint) {
        return Uri.parse(ISSUES_NEW).buildUpon()
                .appendQueryParameter("title", issueTitle(trigger, versionName))
                .appendQueryParameter("body", buildBody(
                        versionName, versionCode, trigger, screen, storageFailure, draftHint))
                .build()
                .toString();
    }

    static String buildBody(String versionName, int versionCode, String trigger,
                            String screen, String storageFailure, String draftHint) {
        StringBuilder b = new StringBuilder();
        b.append("## Summary\n\n");
        b.append("(What went wrong)\n\n");
        b.append("## Steps\n\n1. \n2. \n\n");
        b.append("## Expected\n\n\n## Actual\n\n\n");
        b.append("## Auto-collected\n\n");
        b.append("- App: Daymark ").append(nz(versionName, "?"))
                .append(" (versionCode ").append(versionCode).append(")\n");
        b.append("- Trigger: ").append(nz(trigger, lastTrigger)).append('\n');
        b.append("- Screen: ").append(nz(screen, "?")).append('\n');
        b.append("- Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("- Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        if (storageFailure != null && !storageFailure.trim().isEmpty()) {
            b.append("- Storage: ").append(trim(storageFailure, 800)).append('\n');
        }
        if (draftHint != null && !draftHint.trim().isEmpty()) {
            b.append("- Draft length: ").append(draftHint.trim().length()).append('\n');
        }
        b.append("\n## Agent notes\n\n");
        b.append("- Do not ask for keystore secrets (GitHub Actions already has them).\n");
        b.append("- Do not ask for recovery keys.\n");
        b.append("- See AGENTS.md / RELEASE_SIGNING.md.\n");
        return b.toString();
    }

    static String buildCrashBody(String versionName, int versionCode, String stack) {
        StringBuilder b = new StringBuilder();
        b.append("## Crash (auto)\n\n");
        b.append("Uncaught exception captured on device.\n\n");
        b.append("## Auto-collected\n\n");
        b.append("- App: Daymark ").append(nz(versionName, "?"))
                .append(" (versionCode ").append(versionCode).append(")\n");
        b.append("- Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("- Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        b.append("\n## Stack\n\n```\n");
        b.append(trim(stack == null ? "(none)" : stack, 12000));
        b.append("\n```\n");
        return b.toString();
    }

    private static String nz(String v, String fb) {
        return v == null || v.isEmpty() ? fb : v;
    }

    private static String trim(String v, int max) {
        String s = v.trim();
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}

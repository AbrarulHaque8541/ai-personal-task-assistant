package com.cue.daymark;

import android.net.Uri;
import android.os.Build;

/**
 * Builds a full, paste-ready bug report and a GitHub "new issue" URL.
 * No embedded API tokens — the user submits once while logged into GitHub
 * (ideally inside Daymark WebView so they never leave the app).
 */
final class BugReportComposer {
    static final String ISSUES_NEW =
            "https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/new";

    private static String lastTrigger = "manual";

    private BugReportComposer() { }

    static void rememberTrigger(String trigger) {
        if (trigger != null && !trigger.trim().isEmpty()) {
            lastTrigger = trigger.trim();
        }
    }

    static String githubNewIssueUrl(String versionName, int versionCode, String trigger,
                                    String screen, String storageFailure, String draftHint) {
        String title = "Bug: " + (trigger == null ? "report" : trigger) + " · "
                + (versionName == null ? "?" : versionName);
        String body = buildBody(versionName, versionCode, trigger, screen, storageFailure, draftHint);
        return Uri.parse(ISSUES_NEW).buildUpon()
                .appendQueryParameter("title", title)
                .appendQueryParameter("body", body)
                .build()
                .toString();
    }

    static String buildBody(String versionName, int versionCode, String trigger,
                            String screen, String storageFailure, String draftHint) {
        StringBuilder b = new StringBuilder();
        b.append("## Summary\n\n");
        b.append("(What went wrong — one or two lines)\n\n");
        b.append("## Steps\n\n1. \n2. \n\n");
        b.append("## Expected\n\n\n");
        b.append("## Actual\n\n\n");
        b.append("## Auto-collected (device)\n\n");
        b.append("- App: Daymark ").append(nullTo(versionName, "?"))
                .append(" (versionCode ").append(versionCode).append(")\n");
        b.append("- Trigger: ").append(nullTo(trigger, lastTrigger)).append('\n');
        b.append("- Screen: ").append(nullTo(screen, "?")).append('\n');
        b.append("- Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("- Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        b.append("- ABI: ").append(Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "?").append('\n');
        if (storageFailure != null && !storageFailure.trim().isEmpty()) {
            b.append("- Storage status: ").append(trim(storageFailure, 500)).append('\n');
        }
        if (draftHint != null && !draftHint.trim().isEmpty()) {
            b.append("- Quick-capture draft length: ").append(draftHint.trim().length()).append('\n');
        }
        b.append("\n## Notes for agents\n\n");
        b.append("- Local task data stays on device; do **not** ask the user to paste recovery keys.\n");
        b.append("- Production signing secrets are already in GitHub Actions; do **not** ask for keystore files.\n");
        b.append("- Prefer fixing on a branch + PR; see AGENTS.md and RELEASE_SIGNING.md.\n");
        return b.toString();
    }

    private static String nullTo(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String trim(String value, int max) {
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }
}

package com.cue.daymark;

import android.net.Uri;
import android.os.Build;

/**
 * Builds a GitHub "new issue" URL for in-app Report a bug.
 * Opens in the system browser / Daymark WebView — no API token, no auto-post.
 */
final class BugReportLinks {
    static final String REPO_ISSUES_NEW =
            "https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/new";

    private BugReportLinks() { }

    static String newIssueUrl(String appVersionName, int appVersionCode) {
        String title = "Bug: ";
        StringBuilder body = new StringBuilder();
        body.append("## What happened\n\n");
        body.append("(Describe the bug)\n\n");
        body.append("## Steps to reproduce\n\n1. \n2. \n\n");
        body.append("## Expected\n\n\n");
        body.append("## Device\n\n");
        body.append("- App: Daymark ").append(appVersionName == null ? "?" : appVersionName)
                .append(" (").append(appVersionCode).append(")\n");
        body.append("- Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        body.append("- Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        body.append("\n## Notes\n\nLocal tasks stay on device; do not paste recovery keys.\n");
        return Uri.parse(REPO_ISSUES_NEW).buildUpon()
                .appendQueryParameter("title", title)
                .appendQueryParameter("body", body.toString())
                .build()
                .toString();
    }
}

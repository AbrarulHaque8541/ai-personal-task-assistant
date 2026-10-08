package com.cue.daymark;

import android.net.Uri;
import android.os.Build;

/**
 * Builds a GitHub "new issue" URL for in-app bug reports.
 *
 * <p>Only technical diagnostics are included. Task titles, notes, attachments,
 * recovery keys, and browsing history must never be placed in the URL.
 */
final class BugReportComposer {
    static final String ISSUES_NEW =
            "https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/new";

    private BugReportComposer() { }

    static Uri buildUri(String appVersionName, int appVersionCode, String flavorHint) {
        String version = appVersionName == null || appVersionName.trim().isEmpty()
                ? "unknown" : appVersionName.trim();
        String flavor = flavorHint == null ? "unknown" : flavorHint.trim();

        String title = "[App] Bug report — Daymark " + version;
        String body = ""
                + "## What happened\n"
                + "<!-- Describe the bug. Do not paste task text, passwords, or recovery keys. -->\n\n"
                + "## Steps to reproduce\n"
                + "1. \n2. \n3. \n\n"
                + "## Expected vs actual\n"
                + "- Expected: \n"
                + "- Actual: \n\n"
                + "## Device diagnostics (auto-filled; no task data)\n"
                + "```\n"
                + "App version: " + version + " (" + appVersionCode + ")\n"
                + "Flavor hint: " + flavor + "\n"
                + "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Device: " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL) + "\n"
                + "```\n";

        return Uri.parse(ISSUES_NEW).buildUpon()
                .appendQueryParameter("title", title)
                .appendQueryParameter("body", body)
                .appendQueryParameter("labels", "bug")
                .build();
    }

    private static String safe(String value) {
        if (value == null) return "unknown";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "unknown" : trimmed;
    }
}

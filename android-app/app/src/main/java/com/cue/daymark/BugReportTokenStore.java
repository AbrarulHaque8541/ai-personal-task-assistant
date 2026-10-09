package com.cue.daymark;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Optional GitHub PAT for automatic Issue creation.
 * Stored only in app-private prefs on device — never in APK source.
 * Owner should use a fine-grained token: this repo only, Issues Read/Write.
 */
final class BugReportTokenStore {
    private static final String PREFS = "daymark.bugreport.v1";
    private static final String KEY_TOKEN = "github_issues_token";

    private final SharedPreferences prefs;

    BugReportTokenStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean hasToken() {
        String t = prefs.getString(KEY_TOKEN, "");
        return t != null && !t.trim().isEmpty();
    }

    String getToken() {
        String t = prefs.getString(KEY_TOKEN, "");
        return t == null ? "" : t.trim();
    }

    void saveToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            clear();
            return;
        }
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply();
    }

    void clear() {
        prefs.edit().remove(KEY_TOKEN).apply();
    }
}

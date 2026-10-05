package com.cue.daymark;

/** Local browser security preferences, independent of Daymark's page-load network mode. */
final class BrowserSettingsPolicy {
    static final boolean DEFAULT_SAFE_BROWSING_ENABLED = true;

    private boolean safeBrowsingEnabled;

    BrowserSettingsPolicy() {
        this(DEFAULT_SAFE_BROWSING_ENABLED);
    }

    BrowserSettingsPolicy(boolean savedSafeBrowsingChoice) {
        safeBrowsingEnabled = savedSafeBrowsingChoice;
    }

    boolean isSafeBrowsingEnabled() {
        return safeBrowsingEnabled;
    }

    void setSafeBrowsingEnabled(boolean enabled) {
        safeBrowsingEnabled = enabled;
    }
}

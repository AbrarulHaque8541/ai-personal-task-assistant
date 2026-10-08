package com.cue.daymark;

/**
 * Pure policy for system Back while the embedded browser is active.
 * Full-screen reader is handled by the Activity before this policy runs.
 */
final class BrowserBackPolicy {
    private BrowserBackPolicy() { }

    /**
     * @return true if Back should be consumed as a single WebView history step
     */
    static boolean shouldNavigateWebHistory(boolean webMode, boolean webViewVisible, boolean canGoBack) {
        return webMode && webViewVisible && canGoBack;
    }
}

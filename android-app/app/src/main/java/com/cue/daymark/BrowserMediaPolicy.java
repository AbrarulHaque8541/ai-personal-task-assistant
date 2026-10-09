package com.cue.daymark;

/** Validation for user-initiated media downloads discovered inside WebView pages. */
final class BrowserMediaPolicy {
    private BrowserMediaPolicy() { }

    static boolean allowsHandoff(boolean onlineEnabled, boolean userGesture,
                                 String scheme, String mediaUrl) {
        return onlineEnabled
                && userGesture
                && "daymark-download".equalsIgnoreCase(scheme)
                && BrowserAddress.isAllowedWebUrl(mediaUrl);
    }
}

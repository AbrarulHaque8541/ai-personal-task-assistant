package com.cue.daymark;

/** Local, user-controlled network mode for the embedded browser. */
final class BrowserNetworkPolicy {
    static final boolean DEFAULT_ONLINE_ENABLED = false;

    private boolean onlineEnabled;

    BrowserNetworkPolicy() {
        this(DEFAULT_ONLINE_ENABLED);
    }

    BrowserNetworkPolicy(boolean savedOnlineChoice) {
        onlineEnabled = savedOnlineChoice;
    }

    boolean isOnlineEnabled() {
        return onlineEnabled;
    }

    boolean allowsRemoteLoads() {
        return onlineEnabled;
    }

    boolean shouldBlockWebViewLoads() {
        return !onlineEnabled;
    }

    void setOnlineEnabled(boolean enabled) {
        onlineEnabled = enabled;
    }
}

package com.cue.daymark;

/** JDK-only checks for the separate local Safe Browsing setting. */
public final class BrowserSettingsPolicySmoke {
    private BrowserSettingsPolicySmoke() { }

    public static void main(String[] args) {
        BrowserSettingsPolicy fresh = new BrowserSettingsPolicy();
        assert fresh.isSafeBrowsingEnabled() : "Safe Browsing must default on for new installs";
        BrowserNetworkPolicy offline = new BrowserNetworkPolicy(false);
        BrowserNetworkPolicy online = new BrowserNetworkPolicy(true);
        assert offline.shouldBlockWebViewLoads() && fresh.isSafeBrowsingEnabled()
                : "Safe Browsing must default on while Daymark page loads are Offline";
        assert !online.shouldBlockWebViewLoads() && fresh.isSafeBrowsingEnabled()
                : "Safe Browsing must default on while Daymark page loads are Online";

        fresh.setSafeBrowsingEnabled(false);
        assert !fresh.isSafeBrowsingEnabled() : "an explicit opt-out should disable Safe Browsing";
        BrowserSettingsPolicy restored = new BrowserSettingsPolicy(fresh.isSafeBrowsingEnabled());
        assert !restored.isSafeBrowsingEnabled() : "the explicit opt-out should be restorable from local preferences";

        restored.setSafeBrowsingEnabled(true);
        assert restored.isSafeBrowsingEnabled() : "the user must be able to re-enable Safe Browsing";
        System.out.println("PASS browser settings policy: Safe Browsing defaults on, explicit opt-out persists, and can be re-enabled");
    }
}

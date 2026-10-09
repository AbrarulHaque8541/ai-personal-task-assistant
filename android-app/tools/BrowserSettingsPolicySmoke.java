package com.cue.daymark;

/** JDK-only checks for the separate local Safe Browsing setting. */
public final class BrowserSettingsPolicySmoke {
    private BrowserSettingsPolicySmoke() { }

    public static void main(String[] args) {
        BrowserSettingsPolicy fresh = new BrowserSettingsPolicy();
        assert fresh.isSafeBrowsingEnabled() : "Safe Browsing must default on for new installs";
        BrowserNetworkPolicy normal = new BrowserNetworkPolicy();
        assert !normal.shouldBlockWebViewLoads() && fresh.isSafeBrowsingEnabled()
                : "normal browsing and Safe Browsing must both be enabled by default";

        fresh.setSafeBrowsingEnabled(false);
        assert !fresh.isSafeBrowsingEnabled() : "an explicit opt-out should disable Safe Browsing";
        BrowserSettingsPolicy restored = new BrowserSettingsPolicy(fresh.isSafeBrowsingEnabled());
        assert !restored.isSafeBrowsingEnabled() : "the explicit opt-out should be restorable from local preferences";

        restored.setSafeBrowsingEnabled(true);
        assert restored.isSafeBrowsingEnabled() : "the user must be able to re-enable Safe Browsing";
        System.out.println("PASS browser settings policy: Safe Browsing defaults on, explicit opt-out persists, and can be re-enabled");
    }
}

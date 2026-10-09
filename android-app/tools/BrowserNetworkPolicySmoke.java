package com.cue.daymark;

/** JDK-only regression checks for the browser's user-controlled network mode. */
public final class BrowserNetworkPolicySmoke {
    private BrowserNetworkPolicySmoke() { }

    public static void main(String[] args) {
        BrowserNetworkPolicy policy = new BrowserNetworkPolicy();
        assert policy.isOnlineEnabled() : "new installs must browse normally";
        assert policy.allowsRemoteLoads() : "normal browser mode must permit explicit user-initiated loads";
        assert !policy.shouldBlockWebViewLoads() : "normal browser mode must not block WebView network loads";

        policy.setOnlineEnabled(true);
        assert policy.isOnlineEnabled() : "explicit online choice should enable browsing";
        assert policy.allowsRemoteLoads() : "online mode should permit user-initiated loads";
        assert !policy.shouldBlockWebViewLoads() : "online mode should unblock WebView loads";

        BrowserNetworkPolicy restored = new BrowserNetworkPolicy(policy.isOnlineEnabled());
        assert restored.isOnlineEnabled() : "the saved user choice should be restorable";
        restored.setOnlineEnabled(false);
        assert !restored.isOnlineEnabled() : "the user can switch online browsing off";
        assert !restored.allowsRemoteLoads() : "turning offline blocks future remote loads";
        assert restored.shouldBlockWebViewLoads() : "turning offline blocks WebView resources";

        System.out.println("PASS browser network policy: normal browsing default, explicit navigation gate, saved state, and optional internal fail-closed blocking");
    }
}

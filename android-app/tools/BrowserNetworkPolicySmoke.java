package com.abrar.daymark;

/** JDK-only regression checks for the browser's user-controlled network mode. */
public final class BrowserNetworkPolicySmoke {
    private BrowserNetworkPolicySmoke() { }

    public static void main(String[] args) {
        BrowserNetworkPolicy policy = new BrowserNetworkPolicy();
        assert !policy.isOnlineEnabled() : "fresh installs must start Offline";
        assert !policy.allowsRemoteLoads() : "fresh installs must block remote loads";
        assert policy.shouldBlockWebViewLoads() : "fresh installs must block WebView resources";

        assert !BrowserNetworkPolicy.hasExplicitOptIn(false, false)
                : "missing preferences must remain Offline";
        assert !BrowserNetworkPolicy.hasExplicitOptIn(true, false)
                : "a legacy default-Online preference without consent must remain Offline";
        assert !BrowserNetworkPolicy.hasExplicitOptIn(false, true)
                : "a consent marker without an Online choice must remain Offline";
        assert BrowserNetworkPolicy.hasExplicitOptIn(true, true)
                : "only a saved Online choice with an explicit-consent marker may enable browsing";

        policy.setOnlineEnabled(true);
        assert policy.isOnlineEnabled() : "explicitly accepted Online choice should enable browsing";
        assert policy.allowsRemoteLoads() : "Online mode should permit user-initiated loads";
        assert !policy.shouldBlockWebViewLoads() : "Online mode should unblock WebView loads";

        BrowserNetworkPolicy restored = new BrowserNetworkPolicy(
                BrowserNetworkPolicy.hasExplicitOptIn(true, true));
        assert restored.isOnlineEnabled() : "a consented saved choice should be restorable";
        restored.setOnlineEnabled(false);
        assert !restored.isOnlineEnabled() : "the user can switch online browsing off";
        assert !restored.allowsRemoteLoads() : "turning Offline blocks future remote loads";
        assert restored.shouldBlockWebViewLoads() : "turning Offline blocks WebView resources";

        System.out.println("PASS browser network policy: fresh Offline default, explicit-consent restoration, legacy-online fail-closed behavior, and page/resource-load blocking");
    }
}

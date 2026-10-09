package com.cue.daymark;

/** JDK-only regression checks for the browser's user-controlled network mode. */
public final class BrowserNetworkPolicySmoke {
    private BrowserNetworkPolicySmoke() { }

    public static void main(String[] args) {
        BrowserNetworkPolicy policy = new BrowserNetworkPolicy();
        assert policy.isOnlineEnabled() : "fresh install should permit ordinary browsing";
        assert policy.allowsRemoteLoads() : "new installs must not stay stuck offline";
        assert !policy.shouldBlockWebViewLoads() : "default browsing must not block WebView loads";
        policy.setOnlineEnabled(false);
        assert !policy.isOnlineEnabled() : "policy can still fail closed";
        assert !policy.allowsRemoteLoads() : "blocked mode must deny remote loads";
        assert policy.shouldBlockWebViewLoads() : "blocked mode must stop WebView resources";
        policy.setOnlineEnabled(true);
        BrowserNetworkPolicy restored = new BrowserNetworkPolicy(policy.isOnlineEnabled());
        assert restored.allowsRemoteLoads() : "normal browsing state is restorable";
        assert !restored.shouldBlockWebViewLoads() : "restored normal browsing must not remain blocked";
        System.out.println("PASS browser network policy: normal browsing default and fail-closed blocked state");
    }
}

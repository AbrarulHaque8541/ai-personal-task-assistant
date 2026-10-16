package com.abrar.daymark;

import java.util.List;

/**
 * Enforces the embedded browser's network boundary.
 *
 * <p>Online consent is not navigation consent: turning Online on never loads or reloads a
 * page. A WebView is unblocked only at the explicit, validated user-navigation boundary.
 * Switching Offline immediately stops and blocks every retained tab before teardown.</p>
 */
final class BrowserWorkspaceController {
    private BrowserWorkspaceController() {}

    static void prepareForExplicitNavigation(BrowserNetworkPolicy policy, DaymarkWebView target) {
        if (policy == null || target == null || !policy.allowsRemoteLoads()) {
            throw new IllegalStateException("Explicit Online consent is required before navigation.");
        }
        target.getSettings().setBlockNetworkLoads(false);
    }

    static void enforceOffline(BrowserNetworkPolicy policy, List<DaymarkWebView> tabs) {
        if (policy == null || policy.allowsRemoteLoads() || tabs == null) return;
        for (DaymarkWebView tab : tabs) {
            if (tab == null) continue;
            tab.stopLoading();
            tab.getSettings().setBlockNetworkLoads(true);
        }
    }
}

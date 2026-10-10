package com.cue.daymark;

/**
 * Bounds automatic browser recovery so a stalled or crashed page cannot create a reload loop.
 *
 * <p>This policy is intentionally small and side-effect-free apart from its per-Activity state.
 * It never changes security settings or touches task/attachment storage.
 */
final class BrowserSelfHealingPolicy {
    private static final int MAX_AUTOMATIC_RECOVERIES_PER_ADDRESS = 1;

    private String activeAddress;
    private int automaticRecoveries;

    /** A deliberate user navigation/retry starts a fresh recovery budget for that address. */
    void onUserNavigation(String address) {
        if (address == null || address.isEmpty()) return;
        activeAddress = address;
        automaticRecoveries = 0;
    }

    /**
     * Returns true at most once for the current address until the page loads or the user retries.
     * Only HTTPS addresses are eligible; caller must still apply BrowserAddress's full URL policy.
     */
    boolean shouldRecover(String address) {
        if (address == null || !address.regionMatches(true, 0, "https://", 0, 8)) return false;
        if (!address.equals(activeAddress)) {
            activeAddress = address;
            automaticRecoveries = 0;
        }
        if (automaticRecoveries >= MAX_AUTOMATIC_RECOVERIES_PER_ADDRESS) return false;
        automaticRecoveries++;
        return true;
    }

    /** A successful main-frame load clears the budget and tracks the final (possibly redirected) URL. */
    void onPageLoaded(String address) {
        if (address == null || !address.regionMatches(true, 0, "https://", 0, 8)) return;
        activeAddress = address;
        automaticRecoveries = 0;
    }
}

package com.cue.daymark;

/** Host smoke tests for the bounded, HTTPS-only browser recovery policy. */
public final class BrowserSelfHealingPolicySmoke {
    public static void main(String[] args) {
        BrowserSelfHealingPolicy policy = new BrowserSelfHealingPolicy();

        assert !policy.shouldRecover(null) : "null address must not recover";
        assert !policy.shouldRecover("http://example.com") : "cleartext HTTP must never auto-recover";
        assert !policy.shouldRecover("file:///tmp/page.html") : "local files must never auto-recover";

        assert policy.shouldRecover("https://example.com") : "first HTTPS recovery should be allowed";
        assert !policy.shouldRecover("https://example.com") : "only one automatic recovery per address";
        assert policy.shouldRecover("https://example.org") : "a different HTTPS address gets its own budget";

        policy.onPageLoaded("https://example.org");
        assert policy.shouldRecover("https://example.org") : "successful load resets the budget";

        policy.onUserNavigation("https://example.org");
        assert policy.shouldRecover("https://example.org") : "explicit user retry resets the budget";
        assert !policy.shouldRecover("https://example.org") : "the next automatic retry must be bounded";

        System.out.println("BrowserSelfHealingPolicySmoke PASS");
    }
}

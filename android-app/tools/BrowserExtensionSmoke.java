package com.cue.daymark;

import java.util.Arrays;
import java.util.Collections;

/** Host-side tests for extension match rules and site-disable normalization. */
public final class BrowserExtensionSmoke {
    private static int assertions;

    private BrowserExtensionSmoke() { }

    public static void main(String[] args) {
        matchRulesCoverSchemeHostAndPath();
        nonWebUrlsNeverMatch();
        disabledSitesExcludeMatchingOrigins();
        siteNormalizationIsStrict();
        System.out.println("PASS browser extension smoke tests: " + assertions + " assertions");
    }

    private static void matchRulesCoverSchemeHostAndPath() {
        check(BrowserExtension.MatchRules.matches("*://*/*", "https://example.com/page"),
                "<all_urls>-style wildcard matches any HTTPS page");
        check(BrowserExtension.MatchRules.matches("https://example.com/*", "https://example.com/a/b"),
                "scheme+host+path wildcard matches");
        check(!BrowserExtension.MatchRules.matches("https://example.com/*", "https://other.com/a"),
                "host mismatch does not match");
        check(!BrowserExtension.MatchRules.matches("https://example.com/*", "http://example.com/a"),
                "scheme mismatch does not match");
        check(BrowserExtension.MatchRules.matches("*://*.example.com/*", "https://news.example.com/x"),
                "subdomain wildcard matches a subdomain");
        check(BrowserExtension.MatchRules.matches("*://*.example.com/*", "https://example.com/x"),
                "subdomain wildcard matches the bare domain");
        check(!BrowserExtension.MatchRules.matches("*://*.example.com/*", "https://notexample.com/x"),
                "subdomain wildcard does not match a lookalike domain");
        check(BrowserExtension.MatchRules.matches("https://example.com/docs/*", "https://example.com/docs/a"),
                "path prefix wildcard matches under the prefix");
        check(!BrowserExtension.MatchRules.matches("https://example.com/docs/*", "https://example.com/other"),
                "path prefix wildcard rejects other paths");
        check(!BrowserExtension.MatchRules.matches("ftp://example.com/*", "https://example.com/"),
                "unsupported schemes never match");
        check(!BrowserExtension.MatchRules.matches("", "https://example.com/"), "empty patterns never match");
        check(!BrowserExtension.MatchRules.matches(null, "https://example.com/"), "null patterns never match");
    }

    private static void nonWebUrlsNeverMatch() {
        check(!BrowserExtension.MatchRules.matches("*://*/*", "http://example.com/"),
                "cleartext pages are never matched");
        check(!BrowserExtension.MatchRules.matches("*://*/*", "file:///etc/passwd"),
                "file URLs are never matched");
        check(!BrowserExtension.MatchRules.matches("*://*/*", "javascript:alert(1)"),
                "script URLs are never matched");
        check(!BrowserExtension.MatchRules.matches("*://*/*", "https://user:pass@example.com/"),
                "credential-bearing URLs are never matched");
    }

    private static void disabledSitesExcludeMatchingOrigins() {
        BrowserExtension ext = extension(Collections.<String>emptyList());
        check(ext.matchesUrl("https://example.com/page"), "enabled extension matches its rule");
        BrowserExtension disabled = extension(Arrays.asList("example.com"));
        check(!disabled.matchesUrl("https://example.com/page"), "disabled site excludes the extension");
        check(!disabled.matchesUrl("https://sub.example.com/page"),
                "disabled site excludes subdomains of the disabled host");
        check(disabled.matchesUrl("https://other.com/page"), "other sites still match");
    }

    private static void siteNormalizationIsStrict() {
        check("example.com".equals(BrowserExtension.normalizeSiteHost("https://example.com/some/page?x=1")),
                "full URLs normalize to their host");
        check("example.com".equals(BrowserExtension.normalizeSiteHost("  EXAMPLE.com  ")),
                "hosts are trimmed and lowercased");
        check("example.com".equals(BrowserExtension.normalizeSiteHost("example.com.")),
                "a trailing dot is stripped");
        check(BrowserExtension.normalizeSiteHost("http://example.com") == null,
                "cleartext URLs are not valid disabled sites");
        check(BrowserExtension.normalizeSiteHost("file:///etc/passwd") == null,
                "file URLs are not valid disabled sites");
        check(BrowserExtension.normalizeSiteHost("https://user:pass@example.com/") == null,
                "credential-bearing URLs are not valid disabled sites");
        check(BrowserExtension.normalizeSiteHost("https://") == null, "empty hosts are rejected");
        check(BrowserExtension.normalizeSiteHost("not a host") == null, "garbage input is rejected");
        check(BrowserExtension.normalizeSiteHost(null) == null, "null input is rejected");
    }

    private static BrowserExtension extension(java.util.List<String> disabledSites) {
        return new BrowserExtension("user.test", "Test pack", "1.0.0", "test", true, false,
                Arrays.asList("*://*/*"), Collections.<String>emptyList(), "", "", "document_end", "",
                disabledSites);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}

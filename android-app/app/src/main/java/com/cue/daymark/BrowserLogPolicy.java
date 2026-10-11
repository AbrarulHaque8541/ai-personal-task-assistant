package com.cue.daymark;

/**
 * Redaction policy for browser Logcat entries.
 *
 * <p>Only a validated HTTPS origin is retained. Paths, queries, fragments, and
 * embedded credentials are intentionally omitted because URLs can contain
 * private search terms, account identifiers, or one-time tokens.
 */
final class BrowserLogPolicy {
    private static final String UNAVAILABLE_ORIGIN = "(no HTTPS origin)";

    private BrowserLogPolicy() { }

    static String redactedOrigin(String address) {
        String origin = BrowserHistory.sanitizeUrl(address);
        return origin == null ? UNAVAILABLE_ORIGIN : origin;
    }
}

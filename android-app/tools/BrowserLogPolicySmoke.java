package com.abrar.daymark;

/** Host smoke tests for origin-only browser Logcat redaction. */
public final class BrowserLogPolicySmoke {
    public static void main(String[] args) {
        String secretUrl = "https://user:pass@example.com/private/reset-token"
                + "?q=private+search&token=abc#fragment";
        String safe = BrowserLogPolicy.redactedOrigin(secretUrl);
        assert safe.equals("https://example.com") : safe;
        assert !safe.contains("user") && !safe.contains("pass")
                && !safe.contains("private") && !safe.contains("token")
                && !safe.contains("fragment") : "sensitive URL components leaked: " + safe;

        assert BrowserLogPolicy.redactedOrigin("https://example.com:8443/path?q=secret")
                .equals("https://example.com:8443");
        assert BrowserLogPolicy.redactedOrigin("http://example.com/path?q=secret")
                .equals("(no HTTPS origin)") : "insecure origins must not enter browser diagnostics";
        assert BrowserLogPolicy.redactedOrigin(null).equals("(no HTTPS origin)");
        assert BrowserLogPolicy.redactedOrigin("not a URL with private search text")
                .equals("(no HTTPS origin)");

        System.out.println("BrowserLogPolicySmoke PASS");
    }
}

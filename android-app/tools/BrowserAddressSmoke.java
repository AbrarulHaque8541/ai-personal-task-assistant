package com.cue.daymark;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public final class BrowserAddressSmoke {
    public static void main(String[] args) {
        String encoded = BrowserAddress.resolveInput("two words & notes", BrowserAddress.SearchEngine.DUCKDUCKGO);
        assert encoded.equals("https://duckduckgo.com/?q=two+words+%26+notes") : encoded;

        String google = BrowserAddress.resolveInput("weather tomorrow", BrowserAddress.SearchEngine.GOOGLE);
        assert google.startsWith("https://www.google.com/search?q=") : google;
        String brave = BrowserAddress.resolveInput("weather tomorrow", BrowserAddress.SearchEngine.BRAVE);
        assert brave.startsWith("https://search.brave.com/search?q=") : brave;

        assert BrowserAddress.resolveInput("example.com/path", BrowserAddress.SearchEngine.DUCKDUCKGO)
                .equals("https://example.com/path");
        assert BrowserAddress.resolveInput("example.com:8443/path", BrowserAddress.SearchEngine.DUCKDUCKGO)
                .equals("https://example.com:8443/path");
        assert BrowserAddress.resolveInput("https://example.com/a", BrowserAddress.SearchEngine.GOOGLE)
                .equals("https://example.com/a");
        rejects("javascript:alert(1)");
        rejects("http://example.com");
        rejects("http://example.com/after-redirect");
        rejects("file:///etc/passwd");
        rejects("content://com.example/private");
        rejects("intent://open");
        assert !BrowserAddress.isAllowedWebUrl("data:text/html,hello");
        assert !BrowserAddress.isAllowedWebUrl("http://example.com/");
        assert !BrowserAddress.isAllowedWebUrl("http://example.com/after-redirect");
        assert !BrowserAddress.isAllowedWebUrl("https://user:pass@example.com/");

        String history = BrowserHistory.add("", "https://example.com/first");
        history = BrowserHistory.add(history, "https://example.com/second");
        history = BrowserHistory.add(history, "https://example.com/first");
        List<String> visits = BrowserHistory.decode(history);
        assert visits.size() == 2 : visits;
        assert visits.get(0).equals("https://example.com/first") : visits;
        assert visits.get(1).equals("https://example.com/second") : visits;
        assert BrowserHistory.decode(BrowserHistory.clear()).isEmpty();

        String searchUrl = BrowserAddress.resolveInput("secret search phrase", BrowserAddress.SearchEngine.DUCKDUCKGO);
        String searchHistory = BrowserHistory.add("", searchUrl);
        assertStored(searchHistory, "https://duckduckgo.com/");
        assert BrowserHistory.decode(searchHistory).size() == 1;
        assert BrowserHistory.decode(searchHistory).get(0).equals("https://duckduckgo.com/");

        String tokenUrl = "https://user:password@example.com/account/reset?token=secret-token&auth=secret-auth"
                + "&key=secret-key&code=secret-code&session=secret-session&access_token=secret-access"
                + "&id_token=secret-id&api_key=secret-api#secret-fragment";
        assert BrowserHistory.sanitizeUrl(tokenUrl).equals("https://example.com/account/reset");
        String tokenHistory = BrowserHistory.add("", tokenUrl);
        assertStored(tokenHistory, "https://example.com/account/reset");
        assert BrowserHistory.decode(tokenHistory).get(0).equals("https://example.com/account/reset");

        String queryVariants = BrowserHistory.add("", "https://example.com/search?q=private+query");
        queryVariants = BrowserHistory.add(queryVariants, "https://example.com/search?q=different+private+query");
        assertStored(queryVariants, "https://example.com/search");
        assert BrowserHistory.decode(queryVariants).size() == 1 : BrowserHistory.decode(queryVariants);
        assert BrowserHistory.decode(queryVariants).get(0).equals("https://example.com/search");

        String legacyUrl = "https://legacy-user:legacy-pass@example.net/path?query=legacy-search&token=legacy-token#legacy-fragment";
        String legacyStored = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(legacyUrl.getBytes(StandardCharsets.UTF_8));
        List<String> migrated = BrowserHistory.decode(legacyStored);
        assert migrated.size() == 1 : migrated;
        assert migrated.get(0).equals("https://example.net/path") : migrated;
        String sanitizedLegacy = BrowserHistory.sanitizeSerialized(legacyStored);
        assertStored(sanitizedLegacy, "https://example.net/path");
        assert BrowserHistory.decode(sanitizedLegacy).equals(migrated);
        assert BrowserHistory.sanitizeUrl("http://example.com/path?q=secret") == null;

        for (int i = 0; i < BrowserHistory.MAX_ENTRIES + 5; i++) {
            history = BrowserHistory.add(history, "https://example.com/page/" + i);
        }
        assert BrowserHistory.decode(history).size() == BrowserHistory.MAX_ENTRIES;
        assert BrowserHistory.decode(history).get(0).equals(
                "https://example.com/page/" + (BrowserHistory.MAX_ENTRIES + 4));

        System.out.println("PASS browser address/history smoke tests: encoding, engines, HTTPS validation, query-free history, legacy sanitization, and clear");
    }

    private static void rejects(String value) {
        boolean rejected = false;
        try {
            BrowserAddress.resolveInput(value, BrowserAddress.SearchEngine.DUCKDUCKGO);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assert rejected : "Expected rejection: " + value;
    }

    private static void assertStored(String serialized, String expectedUrl) {
        String[] lines = serialized.split("\\n");
        assert lines.length == 1 : "Expected one sanitized stored URL, got " + lines.length;
        String stored = new String(Base64.getUrlDecoder().decode(lines[0]), StandardCharsets.UTF_8);
        assert stored.equals(expectedUrl) : "Sensitive URL data persisted: " + stored;
        assert !stored.contains("?") && !stored.contains("#") && !stored.contains("@")
                : "Query, fragment, and userinfo must not be stored: " + stored;
    }
}

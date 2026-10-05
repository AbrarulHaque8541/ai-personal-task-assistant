package com.cue.daymark;

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

        for (int i = 0; i < BrowserHistory.MAX_ENTRIES + 5; i++) {
            history = BrowserHistory.add(history, "https://example.com/page/" + i);
        }
        assert BrowserHistory.decode(history).size() == BrowserHistory.MAX_ENTRIES;
        assert BrowserHistory.decode(history).get(0).equals(
                "https://example.com/page/" + (BrowserHistory.MAX_ENTRIES + 4));

        System.out.println("PASS browser address/history smoke tests: encoding, selected engine, URL validation, and local clear");
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
}

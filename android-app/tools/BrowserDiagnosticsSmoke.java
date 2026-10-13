package com.abrar.daymark;

import java.util.List;

/** Host smoke tests for the bounded, privacy-preserving browser diagnostics buffer. */
public final class BrowserDiagnosticsSmoke {
    public static void main(String[] args) {
        assert BrowserDiagnostics.redactUrl("https://user:secret@example.com/reset/tok?token=abc#frag")
                .equals("https://example.com/reset/tok") : "credentials, query, and fragment must be redacted";
        assert BrowserDiagnostics.redactUrl("https://example.com/search?q=1#top")
                .equals("https://example.com/search") : "query and fragment must be redacted";
        assert BrowserDiagnostics.redactUrl("https://example.com/a@b/path?x=1")
                .equals("https://example.com/a@b/path") : "an @ inside the path is not a credential and must be kept";
        assert BrowserDiagnostics.redactUrl(null).isEmpty() && BrowserDiagnostics.redactUrl("   ").isEmpty()
                : "null/blank URLs must become empty strings";
        assert BrowserDiagnostics.redactUrl("https://example.com/very/" + "x".repeat(300))
                .length() <= 200 : "redacted URLs must be bounded";

        BrowserDiagnostics diagnostics = new BrowserDiagnostics();
        for (int i = 0; i < 60; i++) {
            diagnostics.record(1000L + i, "load requested", "https://example.com/page?token=" + i, null);
        }
        List<BrowserDiagnostics.Event> events = diagnostics.events();
        assert events.size() == BrowserDiagnostics.MAX_EVENTS : "the buffer must stay bounded";
        assert events.get(0).sequence == 21 && events.get(events.size() - 1).sequence == 60
                : "oldest events are dropped, sequences stay monotonic";
        assert events.get(0).url.equals("https://example.com/page") : "recorded URLs must be redacted";
        assert events.get(0).detail.isEmpty() : "null details must be stored as empty";

        String report = diagnostics.report("Daymark version: test\nAndroid API: 36");
        assert report.contains("Daymark version: test") && report.contains("load requested")
                && report.contains("https://example.com/page") : "report must include environment and redacted events";
        assert !report.contains("token=") : "report must never contain query strings";

        BrowserDiagnostics empty = new BrowserDiagnostics();
        assert empty.report(null).contains("No navigation events recorded yet") : "empty report must explain itself";

        events.clear();
        assert diagnostics.events().size() == BrowserDiagnostics.MAX_EVENTS : "events() must return a defensive copy";

        System.out.println("BrowserDiagnosticsSmoke PASS");
    }
}

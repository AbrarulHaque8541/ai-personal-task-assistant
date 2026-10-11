package com.cue.daymark;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded in-memory record of browser navigation lifecycle events for the
 * user-triggered diagnostics mode (PROJECT_PLAN P1 #7, supports issue #221).
 *
 * <p>Privacy rules enforced here:
 * <ul>
 *   <li>URLs are stored only after {@link #redactUrl}: no credentials, query
 *       string, or fragment. The path is kept because it is usually needed to
 *       tell "search results" apart from a login callback or an API route.</li>
 *   <li>Records never leave this process: no persistence, no network, no task
 *       data, no platform logging. The only export path is the explicit,
 *       user-tapped Copy action in the diagnostics dialog.</li>
 *   <li>The buffer is capped at {@link #MAX_EVENTS} entries; the oldest events
 *       are dropped first.</li>
 * </ul>
 */
final class BrowserDiagnostics {
    static final int MAX_EVENTS = 40;
    private static final int MAX_TEXT_LENGTH = 200;

    static final class Event {
        final long sequence;
        final long timestampMs;
        final String stage;
        final String url;
        final String detail;

        Event(long sequence, long timestampMs, String stage, String url, String detail) {
            this.sequence = sequence;
            this.timestampMs = timestampMs;
            this.stage = stage;
            this.url = url;
            this.detail = detail;
        }
    }

    private final Deque<Event> events = new ArrayDeque<>();
    private long nextSequence = 1;

    /**
     * Returns the URL without credentials, query string, or fragment.
     * Null or blank input becomes an empty string; output is bounded.
     */
    static String redactUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return "";
        String result;
        int schemeEnd = trimmed.indexOf("://");
        if (schemeEnd < 0) {
            result = stripSensitiveTail(trimmed);
        } else {
            String prefix = trimmed.substring(0, schemeEnd + 3);
            String rest = trimmed.substring(schemeEnd + 3);
            // Only strip userinfo before the first path slash: an '@' inside a path
            // segment (common in e-mail slugs) is not a credential.
            int slash = rest.indexOf('/');
            int at = rest.indexOf('@');
            if (at >= 0 && (slash < 0 || at < slash)) rest = rest.substring(at + 1);
            result = prefix + stripSensitiveTail(rest);
        }
        if (result.length() > MAX_TEXT_LENGTH) result = result.substring(0, MAX_TEXT_LENGTH);
        return result;
    }

    private static String stripSensitiveTail(String value) {
        int cut = value.length();
        int query = value.indexOf('?');
        if (query >= 0) cut = Math.min(cut, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) cut = Math.min(cut, fragment);
        return value.substring(0, cut);
    }

    /** Records one bounded lifecycle event. Null-safe; the URL is redacted before storage. */
    synchronized void record(long timestampMs, String stage, String url, String detail) {
        while (events.size() >= MAX_EVENTS) events.removeFirst();
        events.addLast(new Event(nextSequence++, timestampMs,
                bounded(stage), redactUrl(url), bounded(detail)));
    }

    /** Returns a defensive copy of the recorded events, oldest first. */
    synchronized List<Event> events() {
        return new ArrayList<>(events);
    }

    /** Builds the user-facing report; the environment block is supplied by the caller. */
    synchronized String report(String environmentBlock) {
        StringBuilder builder = new StringBuilder();
        if (environmentBlock != null && !environmentBlock.isEmpty()) {
            builder.append(environmentBlock.trim()).append('\n');
        }
        if (events.isEmpty()) {
            builder.append("No navigation events recorded yet in this session.");
            return builder.toString();
        }
        for (Event event : events) {
            builder.append('#').append(event.sequence)
                    .append(' ').append(Instant.ofEpochMilli(event.timestampMs))
                    .append(' ').append(event.stage);
            if (!event.url.isEmpty()) builder.append(' ').append(event.url);
            if (!event.detail.isEmpty()) builder.append(" — ").append(event.detail);
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private static String bounded(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) return trimmed.substring(0, MAX_TEXT_LENGTH);
        return trimmed;
    }
}

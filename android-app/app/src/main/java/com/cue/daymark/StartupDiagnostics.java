package com.cue.daymark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records non-fatal failures that were previously swallowed by empty catch blocks.
 *
 * <p>The round-2 audit found two silent catch blocks: the startup orphan-attachment
 * cleanup and the export-stage document cleanup. Both deliberately continue (the
 * work is retried on a later launch), but swallowing the exception entirely means a
 * persistent failure is invisible. This class keeps a bounded, in-memory record of
 * such events with a stable code so they can be surfaced in diagnostics.
 *
 * <p>Pure and JDK-only so it is testable without a device.
 */
final class StartupDiagnostics {
    /** Startup orphan-attachment cleanup failed; retried on a later launch. */
    static final String ORPHAN_CLEANUP_FAILED = "startup.orphan_cleanup_failed";
    /** A staged export document could not be deleted. */
    static final String EXPORT_STAGE_CLEANUP_FAILED = "export.stage_cleanup_failed";
    /** The updater recovery record could not be cleared after a failed verification. */
    static final String UPDATER_RECOVERY_CLEAR_FAILED = "updater.recovery_clear_failed";
    /** Listing verified updater artifacts failed; recovery is retried on a later launch. */
    static final String UPDATER_ARTIFACT_LIST_FAILED = "updater.artifact_list_failed";
    /** The app version could not be read for the diagnostics panel. */
    static final String PACKAGE_INFO_FAILED = "diagnostics.package_info_failed";
    /** A bounded automatic browser recovery was attempted. */
    static final String BROWSER_RECOVERY_ATTEMPTED = "browser.self_heal_attempted";
    /** Automatic browser recovery was exhausted or unavailable. */
    static final String BROWSER_RECOVERY_EXHAUSTED = "browser.self_heal_exhausted";
    /** Recreating a crashed WebView failed. */
    static final String BROWSER_RENDERER_RECREATE_FAILED = "browser.self_heal_renderer_recreate_failed";
    /** Retrying a stalled page raised a runtime exception. */
    static final String BROWSER_RECOVERY_RETRY_FAILED = "browser.self_heal_retry_failed";
    /** Bounded so a repeated failure cannot grow memory without limit. */
    static final int MAX_EVENTS = 32;

    private static final List<String> EVENTS = new ArrayList<>();

    private StartupDiagnostics() { }

    /** Record a non-fatal failure under a stable code. Never throws. */
    static synchronized void record(String code, Throwable error) {
        if (code == null || code.isEmpty()) return;
        String detail = error == null ? code : code + ": " + error.getClass().getSimpleName();
        EVENTS.add(detail);
        while (EVENTS.size() > MAX_EVENTS) EVENTS.remove(0);
    }

    /** Snapshot of recorded events, oldest first. */
    static synchronized List<String> events() {
        return Collections.unmodifiableList(new ArrayList<>(EVENTS));
    }

    /** Whether any event with the given code has been recorded. */
    static synchronized boolean hasEvent(String code) {
        for (String event : EVENTS) {
            if (event.equals(code) || event.startsWith(code + ":")) return true;
        }
        return false;
    }

    static synchronized void clear() {
        EVENTS.clear();
    }
}

package com.cue.daymark;

/** JDK-only checks for the non-fatal failure diagnostics recorder. */
public final class StartupDiagnosticsSmoke {
    private StartupDiagnosticsSmoke() { }

    public static void main(String[] args) {
        StartupDiagnostics.clear();
        assert StartupDiagnostics.events().isEmpty() : "recorder must start empty";

        StartupDiagnostics.record(StartupDiagnostics.ORPHAN_CLEANUP_FAILED, new java.io.IOException("boom"));
        assert StartupDiagnostics.hasEvent(StartupDiagnostics.ORPHAN_CLEANUP_FAILED)
                : "a recorded orphan-cleanup failure must be visible";
        assert StartupDiagnostics.events().get(0).startsWith(StartupDiagnostics.ORPHAN_CLEANUP_FAILED)
                : "the event must carry its stable code";
        assert StartupDiagnostics.events().get(0).contains("IOException")
                : "the event must carry the exception type";

        StartupDiagnostics.record(StartupDiagnostics.EXPORT_STAGE_CLEANUP_FAILED, null);
        assert StartupDiagnostics.hasEvent(StartupDiagnostics.EXPORT_STAGE_CLEANUP_FAILED)
                : "a null-cause failure must still be recorded";

        StartupDiagnostics.record(null, new RuntimeException());
        StartupDiagnostics.record("", new RuntimeException());
        assert StartupDiagnostics.events().size() == 2 : "blank codes must be ignored";

        for (int i = 0; i < StartupDiagnostics.MAX_EVENTS * 2; i++) {
            StartupDiagnostics.record(StartupDiagnostics.ORPHAN_CLEANUP_FAILED, new RuntimeException());
        }
        assert StartupDiagnostics.events().size() == StartupDiagnostics.MAX_EVENTS
                : "the recorder must stay bounded";

        StartupDiagnostics.clear();
        assert StartupDiagnostics.events().isEmpty() : "clear must empty the recorder";
        System.out.println("PASS startup diagnostics: non-fatal failures recorded with stable codes, bounded, clearable");
    }
}

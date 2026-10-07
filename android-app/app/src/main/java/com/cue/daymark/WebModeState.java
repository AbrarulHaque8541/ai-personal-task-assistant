package com.cue.daymark;

/**
 * Pure state contract for the task/Web mode toggle across Activity recreation.
 *
 * <p>The round-2 audit found that {@code webMode} was never written to
 * {@code onSaveInstanceState}, so a rotation or process recreation silently
 * dropped the user back into task mode (issue #30's web-mode half). This class
 * holds the key and the two invariants so they are testable without a device.
 */
final class WebModeState {
    /** Saved-state key for the task/Web mode flag. */
    static final String KEY = "web_mode";

    private WebModeState() { }

    /** Restore the mode flag; a missing value means task mode. */
    static boolean restore(Boolean saved) {
        return Boolean.TRUE.equals(saved);
    }

    /**
     * The composer text to show for a restored mode. Web mode must never be
     * prefilled with the local task draft, and task mode must never be prefilled
     * with a web query.
     */
    static String composerTextForRestoredMode(boolean webMode, String taskDraft) {
        if (webMode) return "";
        return taskDraft == null ? "" : taskDraft;
    }
}

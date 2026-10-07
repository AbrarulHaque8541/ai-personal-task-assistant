package com.cue.daymark;

/**
 * Pure state contract for the shared composer's per-mode drafts.
 *
 * <p>The round-2 audit found the composer text was lost on Activity recreation
 * (issue #30): {@code quickCaptureInput} is built programmatically with no view
 * ID, so Android's view-hierarchy state saving cannot restore it, and the
 * Activity only persisted {@code taskDraft} — never the Web-mode query. This
 * class holds the saved-state keys and the two invariants so they are testable
 * without a device.
 */
final class ComposerDraftPolicy {
    /** Saved-state key for the task-mode draft. */
    static final String TASK_DRAFT_KEY = "composer_task_draft";
    /** Saved-state key for the Web-mode query. */
    static final String WEB_QUERY_KEY = "composer_web_query";

    private ComposerDraftPolicy() { }

    /** Restore a saved draft; a missing value means empty. */
    static String restore(String saved) {
        return saved == null ? "" : saved;
    }

    /**
     * The composer text to show for a mode. Each mode keeps its own draft, so
     * switching modes never leaks a task draft into a web query or vice versa.
     */
    static String textForMode(boolean webMode, String taskDraft, String webQueryDraft) {
        return webMode ? restore(webQueryDraft) : restore(taskDraft);
    }
}

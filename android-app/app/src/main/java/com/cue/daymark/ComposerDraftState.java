package com.cue.daymark;

import android.os.Bundle;

/**
 * Pure save/restore contract for the unsaved task composer draft (issue #30).
 *
 * <p>The draft is Activity instance state only: it must survive configuration
 * changes without creating a task and must never be written to external storage
 * or sent over the network.
 */
final class ComposerDraftState {
    static final String KEY = "composer.task_draft.v1";

    private ComposerDraftState() { }

    static void save(Bundle outState, String draft) {
        if (outState == null) return;
        String value = draft == null ? "" : draft;
        // Bound the saved payload so a pathological paste cannot bloat the instance state.
        if (value.length() > 4096) {
            value = value.substring(0, 4096);
        }
        outState.putString(KEY, value);
    }

    static String restore(Bundle savedInstanceState) {
        if (savedInstanceState == null || !savedInstanceState.containsKey(KEY)) {
            return "";
        }
        String value = savedInstanceState.getString(KEY, "");
        return value == null ? "" : value;
    }
}

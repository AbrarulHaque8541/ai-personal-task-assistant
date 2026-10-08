package com.cue.daymark;

import android.os.Bundle;

/** Unsaved task composer draft across Activity recreation (issue #30). Instance state only. */
final class ComposerDraftState {
    static final String KEY = "composer.task_draft.v1";

    private ComposerDraftState() { }

    static void save(Bundle outState, String draft) {
        if (outState == null) return;
        String value = draft == null ? "" : draft;
        if (value.length() > 4096) value = value.substring(0, 4096);
        outState.putString(KEY, value);
    }

    static String restore(Bundle savedInstanceState) {
        if (savedInstanceState == null || !savedInstanceState.containsKey(KEY)) return "";
        String value = savedInstanceState.getString(KEY, "");
        return value == null ? "" : value;
    }
}

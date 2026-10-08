package com.cue.daymark;

/** JDK-only checks for the task/Web mode saved-state contract. */
public final class WebModeStateSmoke {
    private WebModeStateSmoke() { }

    public static void main(String[] args) {
        assert "web_mode".equals(WebModeState.KEY) : "the saved-state key must stay stable";

        assert !WebModeState.restore(null) : "a missing saved value must restore task mode";
        assert !WebModeState.restore(Boolean.FALSE) : "a saved false must restore task mode";
        assert WebModeState.restore(Boolean.TRUE) : "a saved true must restore Web mode";

        assert "".equals(WebModeState.composerTextForRestoredMode(true, "buy milk"))
                : "Web mode must never be prefilled with the local task draft";
        assert "buy milk".equals(WebModeState.composerTextForRestoredMode(false, "buy milk"))
                : "task mode must restore the local task draft";
        assert "".equals(WebModeState.composerTextForRestoredMode(false, null))
                : "a null draft must restore as empty";
        System.out.println("PASS web mode state: mode survives recreation and the task draft is never prefilled into Web mode");
    }
}

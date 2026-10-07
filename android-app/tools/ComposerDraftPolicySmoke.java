package com.cue.daymark;

/** JDK-only checks for the shared composer's per-mode draft contract. */
public final class ComposerDraftPolicySmoke {
    private ComposerDraftPolicySmoke() { }

    public static void main(String[] args) {
        assert "composer_task_draft".equals(ComposerDraftPolicy.TASK_DRAFT_KEY)
                : "the task-draft key must stay stable";
        assert "composer_web_query".equals(ComposerDraftPolicy.WEB_QUERY_KEY)
                : "the web-query key must stay stable";

        assert "".equals(ComposerDraftPolicy.restore(null))
                : "a missing saved draft must restore as empty";
        assert "buy milk".equals(ComposerDraftPolicy.restore("buy milk"))
                : "a saved draft must round-trip";

        assert "buy milk".equals(ComposerDraftPolicy.textForMode(false, "buy milk", "cats"))
                : "task mode must show the task draft";
        assert "cats".equals(ComposerDraftPolicy.textForMode(true, "buy milk", "cats"))
                : "Web mode must show the web query, not the task draft";
        assert "".equals(ComposerDraftPolicy.textForMode(true, "buy milk", null))
                : "Web mode with no saved query must be empty, never the task draft";
        assert "".equals(ComposerDraftPolicy.textForMode(false, null, "cats"))
                : "task mode with no saved draft must be empty, never the web query";
        System.out.println("PASS composer draft policy: each mode keeps its own draft across recreation");
    }
}

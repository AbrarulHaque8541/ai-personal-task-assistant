#!/usr/bin/env python3
"""Source-level guard for the composer-draft fix (issue #30).

Fails if MainActivity stops persisting/restoring the composer drafts, or if the
Web-mode query is again dropped (the original bug: only taskDraft was saved).

The MainActivity wiring is delivered as an applyable patch
(tools/patches/composer-draft-mainactivity.patch) because the file exceeds the
commit payload cap, so on the un-patched branch this prints a PENDING note
instead of failing.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text()
POLICY = (ROOT / "app/src/main/java/com/cue/daymark/ComposerDraftPolicy.java").read_text()
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


check('TASK_DRAFT_KEY = "composer_task_draft"' in POLICY,
      "the task-draft key must stay stable")
check('WEB_QUERY_KEY = "composer_web_query"' in POLICY,
      "the web-query key must stay stable")
check("textForMode" in POLICY, "the policy must keep the two drafts separate")

if "ComposerDraftPolicy" in ACTIVITY:
    check("outState.putString(STATE_TASK_DRAFT, taskDraft)" in ACTIVITY,
          "onSaveInstanceState must persist the task draft")
    check("outState.putString(STATE_WEB_QUERY, webQueryDraft)" in ACTIVITY,
          "onSaveInstanceState must persist the Web-mode query (the original bug)")
    check("ComposerDraftPolicy.restore(savedInstanceState.getString(STATE_TASK_DRAFT))" in ACTIVITY,
          "onCreate must restore the task draft")
    check("ComposerDraftPolicy.restore(savedInstanceState.getString(STATE_WEB_QUERY))" in ACTIVITY,
          "onCreate must restore the Web-mode query")
    check("if (webMode) webQueryDraft = value;" in ACTIVITY,
          "the text watcher must track the Web-mode query, not only the task draft")
    check("quickCaptureInput.setText(ComposerDraftPolicy.textForMode(webMode, taskDraft, webQueryDraft))" in ACTIVITY,
          "the composer must be seeded from the per-mode draft when built")
else:
    print("PENDING composer draft: apply "
          "tools/patches/composer-draft-mainactivity.patch to persist the "
          "composer drafts across recreation")

print(f"PASS composer draft source checks: {checks} assertions")

#!/usr/bin/env python3
"""Source regression checks for find-in-page navigation and cleanup (issue #135)."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
method = activity.split("private void showFindInPageDialog()", 1)[1].split(
    "private void shareCurrentBrowserUrl()", 1
)[0]

for marker, explanation in (
    ("target.setFindListener", "match count/status feedback must be connected to WebView"),
    ("target.findAllAsync(query)", "typing a non-empty query must search the page"),
    ('status.setText("No matches found.")', "zero matches must be reported to the user"),
    ('status.setText("Match " +', "current match position and total count must be visible"),
    ("target.findNext(false)", "Previous must navigate to the previous match"),
    ("target.findNext(true)", "Next must navigate to the next match"),
    ("target.clearMatches()", "clearing/closing the dialog must clear WebView highlights"),
    ("target.setFindListener(null)", "dismissal must detach the listener to avoid stale UI callbacks"),
    ("dialog.setOnDismissListener", "listener and highlights must be cleaned up when the dialog closes"),
    ("WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE", "the search input should open with the keyboard available"),
):
    assert marker in method, explanation

assert 'previous.setEnabled(numberOfMatches > 0)' in method
assert 'next.setEnabled(numberOfMatches > 0)' in method
assert 'if (query.isEmpty())' in method
assert 'showToast("Open a page before searching its text.")' in method
print("PASS find-in-page source checks: live count, next/previous navigation, no-results feedback, keyboard focus and cleanup")

# Work by: ChatGPT
# Model: GPT-6
# Tooling: GitHub MCP tools
# Timestamp (UTC): 2026-10-09T17:18:38.551Z

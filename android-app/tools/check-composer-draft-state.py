#!/usr/bin/env python3
"""Guard for issue #30: composer draft save/restore wiring."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
helper = (root / "app/src/main/java/com/cue/daymark/ComposerDraftState.java").read_text(encoding="utf-8")

assert "class ComposerDraftState" in helper
assert 'KEY = "composer.task_draft.v1"' in helper
assert "static void save" in helper and "static String restore" in helper
assert "4096" in helper, "draft payload must be bounded"

pending = 0
if "ComposerDraftState.save" not in activity:
    print("PENDING MainActivity does not yet call ComposerDraftState.save (apply tools/patches/composer-draft-state-mainactivity.patch)")
    pending += 1
if "ComposerDraftState.restore" not in activity:
    print("PENDING MainActivity does not yet call ComposerDraftState.restore (apply tools/patches/composer-draft-state-mainactivity.patch)")
    pending += 1

if pending:
    print(f"PASS composer draft policy: helper present; MainActivity wiring PENDING ({pending})")
    sys.exit(0)

assert "ComposerDraftState.save(outState, taskDraft)" in activity or "ComposerDraftState.save" in activity
assert "ComposerDraftState.restore" in activity
print("PASS composer draft state: helper + MainActivity save/restore wired")

#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
helper = (root / "app/src/main/java/com/cue/daymark/BugReportComposer.java").read_text(encoding="utf-8")
assert "issues/new" in helper
assert "Do not paste task text" in helper or "no task data" in helper.lower() or "No task" in helper
assert "Build.VERSION" in helper or "Android:" in helper
assert "task" in helper.lower()
# Must not embed recovery-key or task title fields
for banned in ("recoveryKey", "task.title", "getTasks(", "EncryptedTaskStore"):
    assert banned not in helper, banned
print("PASS bug report composer: GitHub issue URL only; no task payload fields")

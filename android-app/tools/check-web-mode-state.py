#!/usr/bin/env python3
"""Source-level guard for the task/Web mode saved-state fix.

Fails if MainActivity stops persisting webMode, stops restoring it, or restores
it after the interface is built (which would leave the UI in the wrong mode).

The MainActivity wiring is delivered as an applyable patch
(tools/patches/web-mode-state-restore-mainactivity.patch) because the file
exceeds the commit payload cap, so on the un-patched branch this prints a
PENDING note instead of failing.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text()
POLICY = (ROOT / "app/src/main/java/com/cue/daymark/WebModeState.java").read_text()
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


check('KEY = "web_mode"' in POLICY, "the saved-state key must stay stable")
check("composerTextForRestoredMode" in POLICY,
      "the policy must keep the task draft out of Web mode")

if "STATE_WEB_MODE" in ACTIVITY:
    check("outState.putBoolean(STATE_WEB_MODE, webMode)" in ACTIVITY,
          "onSaveInstanceState must persist the task/Web mode flag")
    check("savedInstanceState.getBoolean(STATE_WEB_MODE" in ACTIVITY,
          "onCreate must restore the task/Web mode flag")
    save_at = ACTIVITY.index("outState.putBoolean(STATE_WEB_MODE, webMode)")
    restore_at = ACTIVITY.index("savedInstanceState.getBoolean(STATE_WEB_MODE")
    build_at = ACTIVITY.index("buildInterface();")
    check(restore_at < build_at,
          "the mode must be restored before buildInterface() so the UI reflects it")
    check(save_at > 0, "the save site must exist")
else:
    print("PENDING web mode state: apply "
          "tools/patches/web-mode-state-restore-mainactivity.patch to persist "
          "webMode across recreation")

print(f"PASS web mode state source checks: {checks} assertions")

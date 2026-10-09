#!/usr/bin/env python3
"""Source-level guard for the task/Web mode saved-state fix.

Fails if MainActivity stops persisting webMode, stops restoring it, or restores
it after the interface is built (which would leave the UI in the wrong mode).

The MainActivity wiring is a required assertion: if the wiring marker is absent, this check fails (issue #196).
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
    raise AssertionError(
        "web-mode-state: required MainActivity wiring is missing; "
        "this check now fails instead of reporting PENDING (issue #196)")

print(f"PASS web mode state source checks: {checks} assertions")

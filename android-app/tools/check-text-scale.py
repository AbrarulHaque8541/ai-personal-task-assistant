#!/usr/bin/env python3
"""Source-level guard for the system-font-scale fix.

Fails if MainActivity stops folding the device font scale into its text scale, or
if the pure TextScalePolicy loses its clamp.

The MainActivity wiring is a required assertion: if the wiring marker is absent, this check fails (issue #196).
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text()
POLICY = (ROOT / "app/src/main/java/com/cue/daymark/TextScalePolicy.java").read_text()
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


check("MIN_SCALE" in POLICY and "MAX_SCALE" in POLICY,
      "the effective scale must be clamped at both ends")
check("appScale * system" in POLICY,
      "the system font scale must multiply the app choice")

if "TextScalePolicy.combined(" in ACTIVITY:
    check(ACTIVITY.count("TextScalePolicy.combined(") >= 2,
          "both the startup and the in-app text-size change must apply the policy")
    check("getResources().getConfiguration().fontScale" in ACTIVITY,
          "the device font scale must be read from the configuration")
else:
    raise AssertionError(
        "text-scale: required MainActivity wiring is missing; "
        "this check now fails instead of reporting PENDING (issue #196)")

print(f"PASS text scale source checks: {checks} assertions")

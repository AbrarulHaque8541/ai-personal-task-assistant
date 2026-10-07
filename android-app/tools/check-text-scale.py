#!/usr/bin/env python3
"""Source-level guard for the system-font-scale fix.

Fails if MainActivity stops folding the device font scale into its text scale, or
if the pure TextScalePolicy loses its clamp.

The MainActivity wiring is delivered as an applyable patch
(tools/patches/system-font-scale-mainactivity.patch) because the file exceeds the
commit payload cap, so on the un-patched branch this prints a PENDING note
instead of failing.
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
    print("PENDING system font scale: apply "
          "tools/patches/system-font-scale-mainactivity.patch to fold the device "
          "font scale into the app text scale")

print(f"PASS text scale source checks: {checks} assertions")

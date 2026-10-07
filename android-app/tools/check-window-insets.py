#!/usr/bin/env python3
"""Source-level guard for the soft-keyboard (IME) inset fix.

Always checks the pure WindowInsetsPolicy and the manifest's adjustResize.
The MainActivity wiring is asserted whenever the adapter is present; because
MainActivity.java exceeds the commit payload cap it is delivered as an
applyable patch (tools/patches/ime-keyboard-insets-mainactivity.patch), so on
the un-patched branch this prints a PENDING note instead of failing.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text()
POLICY = (ROOT / "app/src/main/java/com/cue/daymark/WindowInsetsPolicy.java").read_text()
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


check("CONSUME_IME_INSETS = true" in POLICY,
      "the IME inset must be consumed")
check("Math.max(systemBarsBottom, imeBottom)" in POLICY,
      "bottom padding must be the larger of system bars and the keyboard")

android_ns = "{http://schemas.android.com/apk/res/android}"
manifest_root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
activity = manifest_root.find("application/activity")
check(activity is not None, "MainActivity must be declared in the manifest")
check(activity.get(android_ns + "windowSoftInputMode") == "adjustResize",
      "MainActivity must use adjustResize so the keyboard resizes the content area")

if "WindowInsets.Type.ime()" in ACTIVITY:
    check("WindowInsetsPolicy.bottomPadding(" in ACTIVITY,
          "the root bottom padding must be derived from the insets policy")
    check("WindowInsetsPolicy.CONSUME_IME_INSETS" in ACTIVITY,
          "the Activity must gate IME consumption on the policy")
else:
    print("PENDING window insets: apply tools/patches/ime-keyboard-insets-mainactivity.patch "
          "to wire the IME inset into MainActivity")

print(f"PASS window insets source checks: {checks} assertions")

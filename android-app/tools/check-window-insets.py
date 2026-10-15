#!/usr/bin/env python3
"""Source-level guard for the soft-keyboard (IME) inset fix.

IME / system-bar inset application lives in Api30Window (loaded only on
API 30+) so Android 8–9 never class-load WindowInsets.Type. This check
accepts that isolation while still requiring MainActivity to wire the
adapter and the pure WindowInsetsPolicy + adjustResize contract.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/abrar/daymark/MainActivity.java").read_text()
POLICY = (ROOT / "app/src/main/java/com/abrar/daymark/WindowInsetsPolicy.java").read_text()
API30 = (ROOT / "app/src/main/java/com/abrar/daymark/Api30Window.java").read_text()
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

# Prefer isolated API-30 adapter (cold-start safe on Android 8–9).
if "WindowInsets.Type.ime()" in API30 and "Api30Window.applyInsets" in ACTIVITY:
    check("WindowInsetsPolicy.bottomPadding(" in API30,
          "Api30Window must derive bottom padding from the insets policy")
    check("WindowInsetsPolicy.CONSUME_IME_INSETS" in API30,
          "Api30Window must gate IME consumption on the policy")
    check("Build.VERSION.SDK_INT >= 30" in ACTIVITY or "SDK_INT >= 30" in ACTIVITY,
          "MainActivity must only touch Api30Window after an API 30 check")
elif "WindowInsets.Type.ime()" in ACTIVITY:
    check("WindowInsetsPolicy.bottomPadding(" in ACTIVITY,
          "the root bottom padding must be derived from the insets policy")
    check("WindowInsetsPolicy.CONSUME_IME_INSETS" in ACTIVITY,
          "the Activity must gate IME consumption on the policy")
else:
    raise AssertionError(
        "window-insets: required MainActivity/Api30Window wiring is missing "
        "(issue #196)")

print(f"PASS window insets source checks: {checks} assertions")

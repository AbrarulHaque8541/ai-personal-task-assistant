#!/usr/bin/env python3
"""Source-level guard for IME insets and API-specific window class isolation."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/abrar/daymark/MainActivity.java").read_text()
POLICY = (ROOT / "app/src/main/java/com/abrar/daymark/WindowInsetsPolicy.java").read_text()
API29 = (ROOT / "app/src/main/java/com/abrar/daymark/Api29Window.java").read_text()
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

# The platform-specific API calls are intentionally isolated in helper classes.
# Check the actual Activity wiring rather than demanding API-30 types inside MainActivity.
check("if (Build.VERSION.SDK_INT >= 29)" in ACTIVITY
      and "Api29Window.relaxBarContrast(window);" in ACTIVITY,
      "API-29 contrast calls must be guarded and routed through Api29Window")
check("@TargetApi(29)" in API29
      and "setStatusBarContrastEnforced(false)" in API29
      and "setNavigationBarContrastEnforced(false)" in API29,
      "API-29 helper must own only API-29 contrast calls")
check("@TargetApi(30)" in API30,
      "API-30 helper must declare its minimum platform level")
check("if (Build.VERSION.SDK_INT >= 30) Api30Window.setDecorFits" in ACTIVITY,
      "API-30 decor fitting must be guarded before the helper is loaded")
check("Api30Window.applyInsets(root, insets, dp(8))" in ACTIVITY,
      "the root must wire insets through the API-30 helper")
check("WindowInsetsPolicy.bottomPadding(" in API30,
      "API-30 insets must use the tested bottom-padding policy")
check("WindowInsetsPolicy.CONSUME_IME_INSETS" in API30,
      "API-30 insets must consume the IME when policy requires it")
check("WindowInsets.Type.ime()" not in ACTIVITY,
      "API-30 framework types must remain isolated from MainActivity")

print(f"PASS window insets/API-level source checks: {checks} assertions")

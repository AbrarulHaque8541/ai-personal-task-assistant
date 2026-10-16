#!/usr/bin/env python3
"""Source guard for keyboard insets and API-specific window helper isolation."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/abrar/daymark/MainActivity.java").read_text(encoding="utf-8")
API29 = (ROOT / "app/src/main/java/com/abrar/daymark/Api29Window.java").read_text(encoding="utf-8")
API30 = (ROOT / "app/src/main/java/com/abrar/daymark/Api30Window.java").read_text(encoding="utf-8")
POLICY = (ROOT / "app/src/main/java/com/abrar/daymark/WindowInsetsPolicy.java").read_text(encoding="utf-8")
checks = 0

def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)

check("CONSUME_IME_INSETS = true" in POLICY, "the IME inset must be consumed")
check("Math.max(systemBarsBottom, imeBottom)" in POLICY, "bottom padding must be the larger of system bars and the keyboard")
android_ns = "{http://schemas.android.com/apk/res/android}"
manifest_root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
activity = manifest_root.find("application/activity")
check(activity is not None, "MainActivity must be declared in the manifest")
check(activity.get(android_ns + "windowSoftInputMode") == "adjustResize", "MainActivity must use adjustResize")
check("if (Build.VERSION.SDK_INT >= 29)" in ACTIVITY and "Api29Window.relaxBarContrast(window);" in ACTIVITY,
      "API-29 contrast calls must be SDK-guarded and routed through Api29Window")
check('@SuppressLint("NewApi")' in API29 and "setStatusBarContrastEnforced(false)" in API29
      and "setNavigationBarContrastEnforced(false)" in API29,
      "API-29 helper must own only API-29 contrast calls")
check("WindowInsets.Type.ime()" not in ACTIVITY and "WindowInsets.Type.ime()" not in API29,
      "API-30 framework symbols must not be referenced by Activity/API-29 helper")
check('@SuppressLint("NewApi")' in API30 and "WindowInsets.Type.ime()" in API30
      and "WindowInsetsPolicy.bottomPadding(" in API30,
      "API-30 IME symbols must remain isolated in API-30 helper and use the tested policy")
check("if (Build.VERSION.SDK_INT >= 30) Api30Window.setDecorFits" in ACTIVITY,
      "API-30 decor fitting must be guarded before loading its helper")
check("if (Build.VERSION.SDK_INT >= 30) {\n                Api30Window.applyInsets(root, insets, dp(8));" in ACTIVITY,
      "API-30 inset application must be guarded before loading its helper")
print(f"PASS window insets/API-level source checks: {checks} assertions")

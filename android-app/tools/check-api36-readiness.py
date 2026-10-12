#!/usr/bin/env python3
"""Source configuration guard for the API 36 migration proposal.

This deliberately does not claim to test predictive Back, edge-to-edge layout,
WebView rendering, or any other API-36 runtime behavior.
"""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
toolchain = (root / "build.gradle.kts").read_text(encoding="utf-8")
wrapper = (root / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
workflow = (root.parent / ".github/workflows/android-release-assets.yml").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")

assert re.search(r"compileSdk\s*=\s*36\b", gradle), "compileSdk must be 36"
assert re.search(r"targetSdk\s*=\s*36\b", gradle), "targetSdk must be 36"
assert 'id("com.android.application") version "8.12.2" apply false' in toolchain, "AGP must officially support API 36"
assert "gradle-8.13-bin.zip" in wrapper, "Gradle must match the AGP 8.12 minimum"
assert '[[ "$target_sdk" == "36" ]]' in workflow, "release artifact target validator must agree"
assert "OnBackInvokedCallback" in activity and "registerOnBackInvokedCallback" in activity, (
    "API 33+ predictive Back callback must be registered"
)
assert "unregisterOnBackInvokedCallback" in activity, "Back callback must be unregistered during teardown"
assert "handleDaymarkBackNavigation()" in activity, "legacy and predictive Back must share one handler"
assert "registerDaymarkBackCallback();" in activity, "callback registration must be wired into Activity setup"
assert "unregisterDaymarkBackCallback();" in activity, "callback cleanup must be wired into Activity teardown"
assert re.search(r'@SuppressLint\("GestureBackNavigation"\)\s+@Override\s+public void onBackPressed\(', activity), (
    "lint suppression must be scoped to the pre-API-33 legacy back override"
)

print("PASS API 36 target, AGP 8.12.2/Gradle 8.13 toolchain, platform predictive-Back callback, and scoped legacy-lint assertions; runtime acceptance remains NOT TESTED")

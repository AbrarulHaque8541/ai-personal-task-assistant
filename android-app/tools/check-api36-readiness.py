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
workflow = (root.parent / ".github/workflows/android-release-assets.yml").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")

assert re.search(r"compileSdk\s*=\s*36\b", gradle), "compileSdk must be 36"
assert re.search(r"targetSdk\s*=\s*36\b", gradle), "targetSdk must be 36"
assert '[[ "$target_sdk" == "36" ]]' in workflow, "release artifact target validator must agree"
assert "OnBackInvokedCallback" in activity and "registerOnBackInvokedCallback" in activity, (
    "API 33+ predictive Back callback must be registered"
)
assert "unregisterOnBackInvokedCallback" in activity, "Back callback must be unregistered during teardown"
assert "handleDaymarkBackNavigation()" in activity, "legacy and predictive Back must share one handler"
assert "registerDaymarkBackCallback();" in activity, "callback registration must be wired into Activity setup"
assert "unregisterDaymarkBackCallback();" in activity, "callback cleanup must be wired into Activity teardown"

print("PASS API 36 configuration and predictive-Back source assertions; runtime acceptance remains NOT TESTED")

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

assert re.search(r"compileSdk\s*=\s*36\b", gradle), (
    "compileSdk must be 36 for the API 36 readiness branch"
)
assert re.search(r"targetSdk\s*=\s*36\b", gradle), (
    "targetSdk must be 36 for current Play update policy"
)
assert '[[ "$target_sdk" == "36" ]]' in workflow, (
    "device-test artifact validator must agree on target SDK 36"
)
assert "public void onBackPressed()" in activity, (
    "API 36 review must account for the Activity's existing custom Back semantics"
)

print(
    "PASS API 36 source configuration and artifact target assertions; "
    "API 36 runtime acceptance remains NOT TESTED"
)

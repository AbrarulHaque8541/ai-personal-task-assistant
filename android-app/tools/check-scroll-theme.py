#!/usr/bin/env python3
"""Guard the scroll-responsive theme wiring and its host regression coverage."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
activity = (root / "app/src/main/java/com/abrar/daymark/MainActivity.java").read_text(encoding="utf-8")
core = (root / "tools/run-core-tests.sh").read_text(encoding="utf-8")
source_guard = (root / "tools/check-v1-source.sh").read_text(encoding="utf-8")
assert (root / "app/src/main/java/com/abrar/daymark/ScrollThemeColor.java").is_file(), "scroll theme helper is missing"
assert (root / "tools/ScrollThemeColorSmoke.java").is_file(), "scroll theme smoke test is missing"
assert "ScrollThemeColor.gradientColors(" in activity, "task content must use the scroll gradient"
assert "palette.background, palette.accentSoft, palette.surfaceAlt, highContrast" in activity, "gradient must honor active palette and high-contrast mode"
assert "setOnScrollChangeListener" in activity and "setTaskChromeHidden(true)" in activity, "existing task scroll chrome behavior must remain wired"
assert "ScrollThemeColor.java" in core and "ScrollThemeColorSmoke.java" in core, "core test script must compile helper and smoke test"
assert "com.abrar.daymark.ScrollThemeColorSmoke" in core, "core test script must execute the smoke test"
assert "check-scroll-theme.py" in source_guard, "canonical source checks must run the scroll theme guard"
print("PASS scroll theme wiring and regression guard")

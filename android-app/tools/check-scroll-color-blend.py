#!/usr/bin/env python3
"""Source guard for scroll-responsive task-home colour blending."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
activity = (root / "app/src/main/java/com/abrar/daymark/MainActivity.java").read_text(encoding="utf-8")

assert "scrollThemeStops = highContrast ? null : ScrollColorBlend.stopsFor(palette.dark);" in activity, (
    "high-contrast mode must bypass the decorative scroll palette"
)
assert "updateScrollTheme(y);" in activity, "task ScrollView must drive the blend from actual scroll position"
assert "private void updateScrollTheme(int scrollY)" in activity, "the scroll colour update method must be wired"
assert "webMode || highContrast || scrollThemeStops == null" in activity, (
    "browser mode and high-contrast mode must retain the base theme"
)
assert "ScrollColorBlend.progress(scrollY, scrollRange)" in activity, (
    "scroll position must be clamped through the pure-Java policy"
)
assert "ScrollColorBlend.colorAt(" in activity, "palette colours must be smoothly interpolated"
assert "appliedScrollThemeStep == step" in activity, "updates must be throttled to bounded scroll steps"
assert "root.setBackgroundColor(color);" in activity and "taskScrollView.setBackgroundColor(color);" in activity, (
    "the task surface background must follow the interpolated colour"
)
assert "updateScrollTheme(taskScrollView == null ? 0 : taskScrollView.getScrollY());" in activity, (
    "switching between task and browser workspace must reset/reapply the correct background"
)

print("PASS scroll colour source guard: scroll wiring, bounded updates, browser reset, and high-contrast bypass")

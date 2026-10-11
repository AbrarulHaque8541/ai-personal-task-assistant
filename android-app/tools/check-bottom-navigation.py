#!/usr/bin/env python3
"""Source guard for the bottom navigation bar and multi-page architecture.

Asserts that:
1. MainActivity includes bottomNavigationBar in root layout.
2. 4 core destinations are wired: Tasks, Web, Templates, Settings.
3. Accessible content descriptions are present for screen readers.
4. Active tab styling switches seamlessly between Tasks and Web.
5. Bottom nav auto-hides smoothly alongside browser chrome on web scroll.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


# 1. Structure and layout
check("private LinearLayout bottomNavigationBar;" in ACTIVITY, "bottomNavigationBar field missing")
check("bottomNavigationBar = buildBottomNavigationBar();" in ACTIVITY, "bottomNavigationBar construction missing")
check("root.addView(bottomNavigationBar" in ACTIVITY, "bottomNavigationBar must be added to root layout")

# 2. Tabs and destinations
check('buildNavTab("✓", "Tasks"' in ACTIVITY, "Tasks tab missing")
check('buildNavTab("🌐", "Web"' in ACTIVITY, "Web tab missing")
check('buildNavTab("📑", "Templates"' in ACTIVITY, "Templates tab missing")
check('buildNavTab("⚙", "Settings"' in ACTIVITY, "Settings tab missing")

# 3. Mode switching and dialog integration
check("view -> setWebMode(false)" in ACTIVITY, "Tasks tab must switch to tasks mode")
check("view -> setWebMode(true)" in ACTIVITY, "Web tab must switch to web mode")
check("view -> showTaskTemplatesDialog()" in ACTIVITY, "Templates tab must open templates dialog")
check("view -> showSettingsDialog()" in ACTIVITY, "Settings tab must open settings dialog")

# 4. Active tab synchronization and accessibility
check("syncBottomNavSelection();" in ACTIVITY, "syncBottomNavSelection must be called in syncModeUi")
check("Tasks page, selected." in ACTIVITY, "Tasks selected accessibility description missing")
check("Web browser page, selected." in ACTIVITY, "Web selected accessibility description missing")

# 5. Chrome auto-hide on web scroll
check("View[] chrome = {browserAddressBarView, browserToolbarScroll, browserProviderRow, bottomNavigationBar};" in ACTIVITY,
      "bottomNavigationBar must be animated alongside browser chrome")

print(f"PASS bottom navigation checks: {checks} assertions")

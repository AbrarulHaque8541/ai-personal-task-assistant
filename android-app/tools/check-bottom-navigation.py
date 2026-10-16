#!/usr/bin/env python3
"""Source guard for the bottom navigation bar and multi-page architecture.

Asserts that:
1. MainActivity includes bottomNavigationBar in root layout.
2. 3 persistent destinations are wired: Tasks, Web, Settings; templates are contextual.
3. Accessible content descriptions are present for screen readers.
4. Active tab styling switches seamlessly between Tasks and Web.
5. Bottom nav auto-hides smoothly alongside browser chrome on web scroll.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/abrar/daymark/MainActivity.java").read_text(encoding="utf-8")
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
check('buildNavTab("⚙", "Settings"' in ACTIVITY, "Settings tab missing")

# 3. Mode switching and dialog integration
check("view -> setWebMode(false)" in ACTIVITY, "Tasks tab must switch to tasks mode")
check("view -> setWebMode(true)" in ACTIVITY, "Web tab must switch to web mode")
check("view -> showSettingsDialog()" in ACTIVITY, "Settings tab must open settings dialog")
check('addSettingsRow(content, "Task templates"' in ACTIVITY, "Templates must be available contextually in Settings")
check('buildNavTab("📑", "Templates"' not in ACTIVITY, "Templates must not occupy persistent bottom navigation")
check('Button moreButton = plainButton("More")' not in ACTIVITY, "Duplicate top-right More action must be removed")
check('Button addTaskShortcut = compactButton("+ Task"' in ACTIVITY, "Compact task creation shortcut missing")
check('Button searchTasksShortcut = compactButton("⌕ Search"' in ACTIVITY, "Compact task search shortcut missing")
check("if (y <= dp(24) || delta < -dp(10))" in ACTIVITY, "Header restores at top or deliberate upward scroll")
check("y > dp(120) && delta > dp(8)" in ACTIVITY, "Header collapses only after meaningful downward scroll")
check("mainHandler.postDelayed(taskChromeRestoreRunnable, 700L)" not in ACTIVITY, "Header must not auto-restore and flicker during a pause")
check('Button moreButton = plainButton("More")' not in ACTIVITY, "No duplicate More button remains in header")
check('compactButton("ⓘ", 18' not in ACTIVITY, "Browser privacy icon uses a text view, not a button factory")
check('Button chooseProvider = compactButton("Choose search provider"' not in ACTIVITY, "Browser home must not show redundant provider CTA")
check("Arrays.asList(browserHomeButton, browserTabButton, browserOverflowButton)" in ACTIVITY, "Browser toolbar should keep only essential compact controls")
check('"Save page as task", "Back", "Forward", "Reload"' in ACTIVITY, "Navigation controls must remain reachable in overflow")

# 4. Active tab synchronization and accessibility
check("syncBottomNavSelection();" in ACTIVITY, "syncBottomNavSelection must be called in syncModeUi")
check("Tasks page, selected." in ACTIVITY, "Tasks selected accessibility description missing")
check("Web browser page, selected." in ACTIVITY, "Web selected accessibility description missing")

# 5. Chrome auto-hide on web scroll
check("View[] chrome = {browserAddressBarView, browserToolbarScroll, browserProviderRow, bottomNavigationBar};" in ACTIVITY,
      "bottomNavigationBar must be animated alongside browser chrome")

print(f"PASS bottom navigation checks: {checks} assertions")

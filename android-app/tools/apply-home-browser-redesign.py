#!/usr/bin/env python3
"""Idempotent UX redesign: denser task home, smoother transitions, quiet browser chrome."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
ma_path = root / "app/src/main/java/com/abrar/daymark/MainActivity.java"
ma = ma_path.read_text(encoding="utf-8")
changed = False

old_dash = (
    "    private void addDashboardHeading(LinearLayout content) {\n"
    "        dateHeading = text(\"YOUR DAY\", 10, palette.muted, Typeface.BOLD);\n"
    "        dateHeading.setLetterSpacing(0.14f);\n"
    "        content.addView(dateHeading, bottomMargin(dp(7)));\n"
    "\n"
    "        TextView heading = text(\"What do you want\\nto get done?\", 29, palette.text, Typeface.BOLD);\n"
    "        heading.setLineSpacing(0, 0.96f);\n"
    "        content.addView(heading, bottomMargin(dp(5)));\n"
    "\n"
    "        TextView subtitle = text(\"Add one task at a time. You can change it later.\", 15, palette.muted, Typeface.NORMAL);\n"
    "        content.addView(subtitle, bottomMargin(dp(17)));\n"
    "\n"
    "        LinearLayout stats = horizontalCard(palette.surface);\n"
    "        addStat(stats, \"Open tasks\", openCount = text(\"0\", 23, palette.accent, Typeface.BOLD));\n"
    "        addStat(stats, \"Due today\", dueTodayCount = text(\"0\", 23, palette.text, Typeface.BOLD));\n"
    "        content.addView(stats, bottomMargin(dp(14)));\n"
    "    }"
)

new_dash = (
    "    private void addDashboardHeading(LinearLayout content) {\n"
    "        // Compact heading: maximize task-list viewport (owner feedback).\n"
    "        dateHeading = text(\"YOUR DAY\", 9, palette.muted, Typeface.BOLD);\n"
    "        dateHeading.setLetterSpacing(0.12f);\n"
    "        content.addView(dateHeading, bottomMargin(dp(4)));\n"
    "\n"
    "        TextView heading = text(\"What do you want to get done?\", 22, palette.text, Typeface.BOLD);\n"
    "        heading.setLineSpacing(0, 1.0f);\n"
    "        content.addView(heading, bottomMargin(dp(8)));\n"
    "\n"
    "        LinearLayout stats = horizontalCard(palette.surface);\n"
    "        addStat(stats, \"Open\", openCount = text(\"0\", 18, palette.accent, Typeface.BOLD));\n"
    "        addStat(stats, \"Today\", dueTodayCount = text(\"0\", 18, palette.text, Typeface.BOLD));\n"
    "        content.addView(stats, bottomMargin(dp(10)));\n"
    "    }"
)

if "maximize task-list viewport" not in ma:
    if old_dash not in ma:
        sys.exit("dashboard block missing or already changed")
    ma = ma.replace(old_dash, new_dash, 1)
    changed = True

if 'text("Tasks are encrypted on this device", 13' in ma and 'text("Web requests only after your tap' in ma:
    ma = ma.replace(
        "card.setPadding(dp(13), dp(11), dp(13), dp(11));",
        "card.setPadding(dp(10), dp(7), dp(10), dp(7));",
        1,
    )
    ma = ma.replace(
        'copy.addView(text("Tasks are encrypted on this device", 13, palette.text, Typeface.BOLD));\n'
        '        copy.addView(text("Web requests only after your tap · no task sync", 12, palette.muted, Typeface.NORMAL));',
        'copy.addView(text("Encrypted on device · web only after your tap", 12, palette.text, Typeface.NORMAL));',
        1,
    )
    changed = True

old_anim = (
    "        target.animate().alpha(1f).translationY(0f)\n"
    "                .setDuration(180L).start();"
)
new_anim = (
    "        target.animate().alpha(1f).translationY(0f)\n"
    "                .setDuration(220L)\n"
    "                .setInterpolator(new android.view.animation.DecelerateInterpolator())\n"
    "                .start();"
)
if "DecelerateInterpolator()" not in ma and old_anim in ma:
    ma = ma.replace(old_anim, new_anim, 1)
    changed = True

old_status = 'browserStatus = text("Ready. No page has been requested.", 11, palette.muted, Typeface.NORMAL);'
new_status = 'browserStatus = text("", 11, palette.muted, Typeface.NORMAL);'
if old_status in ma:
    ma = ma.replace(old_status, new_status, 1)
    changed = True

chrome_old = (
    ".setDuration(180L).withEndAction(() -> {\n"
    "                            if (taskChromeHidden && !webMode) view.setVisibility(View.GONE);"
)
chrome_new = (
    ".setDuration(160L).setInterpolator(new android.view.animation.DecelerateInterpolator())\n"
    "                        .withEndAction(() -> {\n"
    "                            if (taskChromeHidden && !webMode) view.setVisibility(View.GONE);"
)
if chrome_old in ma:
    ma = ma.replace(chrome_old, chrome_new, 1)
    changed = True

if changed:
    ma_path.write_text(ma, encoding="utf-8")
    print("home/browser redesign applied")
else:
    print("home/browser redesign already applied")

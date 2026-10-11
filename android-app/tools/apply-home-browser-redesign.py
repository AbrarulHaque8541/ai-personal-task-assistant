#!/usr/bin/env python3
"""Idempotent UX redesign: denser task home, smoother transitions, quiet browser chrome."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
ma_path = root / "app/src/main/java/com/cue/daymark/MainActivity.java"
ma = ma_path.read_text(encoding="utf-8")
changed = False

# --- Compact dashboard: less fixed visual weight ---
old_dash = '''    private void addDashboardHeading(LinearLayout content) {
        dateHeading = text("YOUR DAY", 10, palette.muted, Typeface.BOLD);
        dateHeading.setLetterSpacing(0.14f);
        content.addView(dateHeading, bottomMargin(dp(7)));

        TextView heading = text("What do you want\nto get done?", 29, palette.text, Typeface.BOLD);
        heading.setLineSpacing(0, 0.96f);
        content.addView(heading, bottomMargin(dp(5)));

        TextView subtitle = text("Add one task at a time. You can change it later.", 15, palette.muted, Typeface.NORMAL);
        content.addView(subtitle, bottomMargin(dp(17)));

        LinearLayout stats = horizontalCard(palette.surface);
        addStat(stats, "Open tasks", openCount = text("0", 23, palette.accent, Typeface.BOLD));
        addStat(stats, "Due today", dueTodayCount = text("0", 23, palette.text, Typeface.BOLD));
        content.addView(stats, bottomMargin(dp(14)));
    }'''

new_dash = '''    private void addDashboardHeading(LinearLayout content) {
        // Compact heading: maximize task-list viewport (owner feedback: less half-screen chrome).
        dateHeading = text("YOUR DAY", 9, palette.muted, Typeface.BOLD);
        dateHeading.setLetterSpacing(0.12f);
        content.addView(dateHeading, bottomMargin(dp(4)));

        TextView heading = text("What do you want to get done?", 22, palette.text, Typeface.BOLD);
        heading.setLineSpacing(0, 1.0f);
        content.addView(heading, bottomMargin(dp(8)));

        LinearLayout stats = horizontalCard(palette.surface);
        addStat(stats, "Open", openCount = text("0", 18, palette.accent, Typeface.BOLD));
        addStat(stats, "Today", dueTodayCount = text("0", 18, palette.text, Typeface.BOLD));
        content.addView(stats, bottomMargin(dp(10)));
    }'''

if "maximize task-list viewport" not in ma:
    if old_dash not in ma:
        sys.exit("dashboard block missing or already changed")
    ma = ma.replace(old_dash, new_dash, 1)
    changed = True

# --- Privacy: one compact line (full text still in settings / about) ---
old_priv = '''    private void addPrivacyCard(LinearLayout content) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(11), dp(13), dp(11));
        card.setBackground(shape(palette.accentSoft, 12, palette.accentSoft));

        TextView shield = text("●", 11, palette.accent, Typeface.BOLD);
        shield.setGravity(Gravity.CENTER);
        card.addView(shield, new LinearLayout.LayoutParams(dp(22), dp(22)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(8), 0, 0, 0);
        copy.addView(text("Tasks are encrypted on this device", 13, palette.text, Typeface.BOLD));
        copy.addView(text("Web requests only after your tap · no task sync", 12, palette.muted, Typeface.NORMAL));
        card.addView(copy, new LinearLayout.LayoutParams('''

# only replace the padding + two-line copy with one line if still two-line
if 'text("Tasks are encrypted on this device", 13' in ma and 'text("Web requests only after your tap' in ma:
    ma = ma.replace(
        'card.setPadding(dp(13), dp(11), dp(13), dp(11));',
        'card.setPadding(dp(10), dp(7), dp(10), dp(7));',
        1,
    )
    ma = ma.replace(
        'copy.addView(text("Tasks are encrypted on this device", 13, palette.text, Typeface.BOLD));\n'
        '        copy.addView(text("Web requests only after your tap · no task sync", 12, palette.muted, Typeface.NORMAL));',
        'copy.addView(text("Encrypted on device · web only after your tap", 12, palette.text, Typeface.NORMAL));',
        1,
    )
    changed = True

# --- Smoother mode transition ---
old_anim = '''        target.animate().alpha(1f).translationY(0f)
                .setDuration(180L).start();'''
new_anim = '''        target.animate().alpha(1f).translationY(0f)
                .setDuration(220L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();'''
if "DecelerateInterpolator()" not in ma and old_anim in ma:
    ma = ma.replace(old_anim, new_anim, 1)
    changed = True

# --- Quiet browser status (not a permanent noise strip) ---
old_status = 'browserStatus = text("Ready. No page has been requested.", 11, palette.muted, Typeface.NORMAL);'
new_status = 'browserStatus = text("", 11, palette.muted, Typeface.NORMAL);'
if old_status in ma:
    ma = ma.replace(old_status, new_status, 1)
    changed = True

# --- Faster chrome hide/show (still animated) ---
if '.setDuration(180L).withEndAction(() -> {\n                            if (taskChromeHidden && !webMode) view.setVisibility(View.GONE);'
    in ma:
    ma = ma.replace(
        '.setDuration(180L).withEndAction(() -> {\n                            if (taskChromeHidden && !webMode) view.setVisibility(View.GONE);',
        '.setDuration(160L).setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(() -> {\n                            if (taskChromeHidden && !webMode) view.setVisibility(View.GONE);',
        1,
    )
    changed = True

if changed:
    ma_path.write_text(ma, encoding="utf-8")
    print("home/browser redesign applied")
else:
    print("home/browser redesign already applied")

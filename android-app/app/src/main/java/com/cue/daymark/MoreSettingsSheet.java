package com.cue.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Modern grouped More sheet: primary actions first, card sections, About + updates.
 */
final class MoreSettingsSheet {
    interface Host {
        MainActivity.Palette palette();
        int dp(int value);
        String versionSummary();
        String themeLabel();
        String textSizeLabel();
        String highContrastSubtitle();
        String permissionLabel();
        boolean webMode();
        void onAppearance();
        void onTextSize();
        void onHighContrast();
        void onAccessibility();
        void onPermissions();
        void onBackup();
        void onBrowserExtensions();
        void onCheckUpdates();
        void onAbout();
    }

    private final Activity activity;
    private final Host host;

    MoreSettingsSheet(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    void show() {
        MainActivity.Palette p = host.palette();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(host.dp(16), host.dp(8), host.dp(16), host.dp(12));

        LinearLayout hero = card(p, host.dp(16));
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.addView(label("Daymark", 18, p.text, Typeface.BOLD));
        TextView ver = label(host.versionSummary(), 12, p.muted, Typeface.NORMAL);
        ver.setPadding(0, host.dp(4), 0, 0);
        hero.addView(ver);
        TextView blurb = label(
                "Local tasks · encrypted storage · optional HTTPS browser",
                12, p.muted, Typeface.NORMAL);
        blurb.setPadding(0, host.dp(6), 0, 0);
        hero.addView(blurb);
        root.addView(hero, marginBottom(host.dp(14)));

        section(root, "QUICK ACTIONS", p);
        LinearLayout primary = card(p, host.dp(12));
        primary.setOrientation(LinearLayout.VERTICAL);
        addRow(primary, p, "Check for updates", "GitHub signed release · download if available",
                true, host::onCheckUpdates);
        divider(primary, p);
        addRow(primary, p, "Encrypted backup", "Export or restore a .dmbackup file",
                true, host::onBackup);
        divider(primary, p);
        addRow(primary, p, "About this app", "Version, package, privacy, releases",
                false, host::onAbout);
        root.addView(primary, marginBottom(host.dp(14)));

        section(root, "LOOK & FEEL", p);
        LinearLayout look = card(p, host.dp(12));
        look.setOrientation(LinearLayout.VERTICAL);
        addRow(look, p, "Appearance", host.themeLabel(), false, host::onAppearance);
        divider(look, p);
        addRow(look, p, "Text size", host.textSizeLabel(), false, host::onTextSize);
        divider(look, p);
        addRow(look, p, "High contrast", host.highContrastSubtitle(), false, host::onHighContrast);
        root.addView(look, marginBottom(host.dp(14)));

        section(root, "ACCESS & SAFETY", p);
        LinearLayout access = card(p, host.dp(12));
        access.setOrientation(LinearLayout.VERTICAL);
        addRow(access, p, "Accessibility", "TalkBack labels and motion", false, host::onAccessibility);
        divider(access, p);
        addRow(access, p, "Permission status", host.permissionLabel(), false, host::onPermissions);
        root.addView(access, marginBottom(host.dp(14)));

        section(root, "BROWSER", p);
        LinearLayout browser = card(p, host.dp(12));
        browser.setOrientation(LinearLayout.VERTICAL);
        String browserSub = host.webMode()
                ? "Manage page scripts and matching sites"
                : "Open Web mode, then manage extensions";
        addRow(browser, p, "Browser & extensions", browserSub, false, host::onBrowserExtensions);
        root.addView(browser, marginBottom(host.dp(8)));

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("More")
                .setView(scroll)
                .setNegativeButton("Done", null)
                .create();
        root.setAlpha(0f);
        root.setTranslationY(host.dp(10));
        dialog.show();
        root.animate().alpha(1f).translationY(0f).setDuration(180L).start();
    }

    private void section(LinearLayout parent, String title, MainActivity.Palette p) {
        TextView h = label(title, 11, p.muted, Typeface.BOLD);
        h.setLetterSpacing(0.08f);
        h.setPadding(host.dp(4), host.dp(2), host.dp(4), host.dp(8));
        parent.addView(h);
    }

    private LinearLayout card(MainActivity.Palette p, int radiusDp) {
        LinearLayout card = new LinearLayout(activity);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.surface);
        bg.setCornerRadius(host.dp(radiusDp));
        bg.setStroke(host.dp(1), p.line);
        card.setBackground(bg);
        card.setPadding(host.dp(4), host.dp(4), host.dp(4), host.dp(4));
        return card;
    }

    private void divider(LinearLayout parent, MainActivity.Palette p) {
        View line = new View(activity);
        line.setBackgroundColor(p.line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, host.dp(1)));
        lp.leftMargin = host.dp(12);
        lp.rightMargin = host.dp(12);
        parent.addView(line, lp);
    }

    private void addRow(LinearLayout parent, MainActivity.Palette p,
                        String title, String subtitle, boolean emphasize, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.dp(12), host.dp(12), host.dp(10), host.dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title + ". " + subtitle);

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        int titleColor = emphasize ? p.accent : p.text;
        copy.addView(label(title, emphasize ? 15 : 14, titleColor, Typeface.BOLD));
        TextView detail = label(subtitle, 12, p.muted, Typeface.NORMAL);
        detail.setPadding(0, host.dp(3), 0, 0);
        copy.addView(detail);

        TextView chevron = label(">", 16, p.muted, Typeface.NORMAL);
        chevron.setGravity(Gravity.CENTER);

        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(chevron, new LinearLayout.LayoutParams(host.dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> action.run());
        parent.addView(row);
    }

    private TextView label(String value, int sp, int color, Typeface typeface) {
        TextView tv = new TextView(activity);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(typeface);
        return tv;
    }

    private LinearLayout.LayoutParams marginBottom(int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = bottom;
        return lp;
    }
}

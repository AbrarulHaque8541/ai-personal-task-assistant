package com.cue.daymark;

/**
 * Pure policy combining the app's own text-size choice with the device font scale.
 *
 * <p>The round-2 audit found the app ignored the system font scale entirely: every
 * {@code setTextSize(... * textScale)} call used only the in-app compact/standard/
 * extra-large choice, so a user who enlarged text in Android Settings saw no change.
 * The effective scale is the product of the two, clamped so an extreme combination
 * cannot produce unreadable or overflowing text.
 */
final class TextScalePolicy {
    /** Smallest effective scale; below this the UI becomes unreadable. */
    static final float MIN_SCALE = 0.85f;
    /** Largest effective scale; above this labels overflow their controls. */
    static final float MAX_SCALE = 2.0f;

    private TextScalePolicy() { }

    /**
     * Effective text scale = app choice x system font scale, clamped to
     * [{@link #MIN_SCALE}, {@link #MAX_SCALE}]. A non-positive system scale is
     * treated as 1.0 so a malformed configuration cannot collapse the text.
     */
    static float combined(float appScale, float systemFontScale) {
        float system = systemFontScale <= 0f ? 1.0f : systemFontScale;
        float effective = appScale * system;
        if (effective < MIN_SCALE) return MIN_SCALE;
        if (effective > MAX_SCALE) return MAX_SCALE;
        return effective;
    }
}

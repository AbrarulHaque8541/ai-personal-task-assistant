package com.abrar.daymark;

/** Pure color blending for a restrained scroll-responsive background gradient. */
public final class ScrollThemeColor {
    private ScrollThemeColor() {}

    public static int[] gradientColors(int background, int accentSoft, int surfaceAlt,
            boolean highContrast) {
        if (highContrast) {
            return new int[] { background, background, background, background, background };
        }
        int accentTint = blend(background, accentSoft, 0.40f);
        int mixedTint = blend(accentTint, surfaceAlt, 0.30f);
        int coolTint = blend(background, surfaceAlt, 0.35f);
        // Returning to base at the end avoids a visible seam near the bottom edge.
        return new int[] { background, accentTint, mixedTint, coolTint, background };
    }

    static int blend(int from, int to, float amount) {
        float ratio = Math.max(0f, Math.min(1f, amount));
        int alpha = channel(from, 24, to, ratio);
        int red = channel(from, 16, to, ratio);
        int green = channel(from, 8, to, ratio);
        int blue = channel(from, 0, to, ratio);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int channel(int from, int shift, int to, float ratio) {
        int a = (from >>> shift) & 0xff;
        int b = (to >>> shift) & 0xff;
        return Math.round(a + (b - a) * ratio);
    }
}

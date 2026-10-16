package com.abrar.daymark;

/**
 * Pure-Java scroll-position colour interpolation for the native task-home surface.
 * Kept independent of Android APIs so endpoint and clamping behaviour are host-tested.
 */
public final class ScrollColorBlend {
    /** Number of UI update intervals; limits background updates while scrolling. */
    public static final int MAX_STEPS = 24;

    private static final int[] LIGHT_STOPS = {
            0xFFF2F5F7, // cool paper
            0xFFEEF5F2, // faint mint
            0xFFF0F2F8, // soft blue-grey
            0xFFF4F1EC  // warm neutral
    };
    private static final int[] DARK_STOPS = {
            0xFF0E1216, // near-black slate
            0xFF111B1D, // subtle teal
            0xFF151A24, // blue-slate
            0xFF1A171F  // muted plum charcoal
    };

    private ScrollColorBlend() { }

    /** Returns a defensive copy so callers cannot mutate the shared palette. */
    public static int[] stopsFor(boolean dark) {
        return (dark ? DARK_STOPS : LIGHT_STOPS).clone();
    }

    /** Maps a vertical scroll position to [0, 1], clamping overscroll and empty content. */
    public static float progress(int scrollY, int scrollRange) {
        if (scrollRange <= 0 || scrollY <= 0) return 0.0f;
        if (scrollY >= scrollRange) return 1.0f;
        return (float) scrollY / (float) scrollRange;
    }

    /** Interpolates ARGB channels between adjacent stops for a clamped progress value. */
    public static int colorAt(int[] stops, float progress) {
        if (stops == null || stops.length < 2) {
            throw new IllegalArgumentException("at least two colour stops are required");
        }
        float bounded;
        if (Float.isNaN(progress) || progress <= 0.0f) bounded = 0.0f;
        else if (progress >= 1.0f) bounded = 1.0f;
        else bounded = progress;

        float scaled = bounded * (stops.length - 1);
        int startIndex = Math.min((int) scaled, stops.length - 2);
        float fraction = scaled - startIndex;
        return interpolate(stops[startIndex], stops[startIndex + 1], fraction);
    }

    private static int interpolate(int from, int to, float fraction) {
        int a = channel(from, to, 24, fraction);
        int r = channel(from, to, 16, fraction);
        int g = channel(from, to, 8, fraction);
        int b = channel(from, to, 0, fraction);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, int shift, float fraction) {
        int start = (from >>> shift) & 0xFF;
        int end = (to >>> shift) & 0xFF;
        return Math.max(0, Math.min(255, Math.round(start + (end - start) * fraction)));
    }
}

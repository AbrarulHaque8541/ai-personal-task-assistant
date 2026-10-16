package com.abrar.daymark;

/** JDK-only regression checks for the scroll-responsive colour blend. */
public final class ScrollColorBlendSmoke {
    private ScrollColorBlendSmoke() { }

    public static void main(String[] args) {
        assert ScrollColorBlend.MAX_STEPS == 24 : "UI updates should stay bounded";
        assert ScrollColorBlend.progress(-10, 100) == 0.0f : "negative overscroll clamps to top";
        assert ScrollColorBlend.progress(0, 100) == 0.0f : "top starts at zero";
        assert ScrollColorBlend.progress(50, 100) == 0.5f : "middle maps to half progress";
        assert ScrollColorBlend.progress(100, 100) == 1.0f : "bottom reaches full progress";
        assert ScrollColorBlend.progress(200, 100) == 1.0f : "overscroll clamps to bottom";
        assert ScrollColorBlend.progress(50, 0) == 0.0f : "non-scrollable content stays at base colour";

        int[] stops = {0xFF000000, 0xFF808080, 0xFFFFFFFF};
        assert ScrollColorBlend.colorAt(stops, -1.0f) == stops[0] : "negative progress clamps";
        assert ScrollColorBlend.colorAt(stops, 0.0f) == stops[0] : "first stop is exact";
        assert ScrollColorBlend.colorAt(stops, 0.5f) == stops[1] : "middle stop is exact";
        assert ScrollColorBlend.colorAt(stops, 1.0f) == stops[2] : "last stop is exact";
        assert ScrollColorBlend.colorAt(stops, 2.0f) == stops[2] : "progress above one clamps";
        assert ScrollColorBlend.colorAt(stops, Float.NaN) == stops[0] : "NaN falls back to base colour";

        int midpoint = ScrollColorBlend.colorAt(new int[]{0xFF000000, 0xFFFFFFFF}, 0.5f);
        assert midpoint == 0xFF808080 : "ARGB interpolation is channel-linear";

        for (boolean dark : new boolean[]{false, true}) {
            int[] palette = ScrollColorBlend.stopsFor(dark);
            assert palette.length == 4 : "both palettes use four restrained stops";
            assert ScrollColorBlend.colorAt(palette, 0.0f) == palette[0];
            assert ScrollColorBlend.colorAt(palette, 1.0f) == palette[palette.length - 1];
            for (int color : palette) {
                assert ((color >>> 24) & 0xFF) == 0xFF : "theme colours must remain opaque";
            }
            int original = palette[0];
            palette[0] = 0;
            assert ScrollColorBlend.stopsFor(dark)[0] == original : "palette must be defensively copied";
        }

        boolean rejectedNull = false;
        try {
            ScrollColorBlend.colorAt(null, 0.5f);
        } catch (IllegalArgumentException expected) {
            rejectedNull = true;
        }
        assert rejectedNull : "null palette must fail clearly";

        boolean rejectedOneStop = false;
        try {
            ScrollColorBlend.colorAt(new int[]{0xFFFFFFFF}, 0.5f);
        } catch (IllegalArgumentException expected) {
            rejectedOneStop = true;
        }
        assert rejectedOneStop : "single-stop palette must fail clearly";

        System.out.println("PASS scroll colour blend: clamping, endpoints, interpolation, palette immutability");
    }
}

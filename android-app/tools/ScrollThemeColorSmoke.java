package com.abrar.daymark;

/** Host-side regression checks for the scroll-responsive theme gradient. */
public final class ScrollThemeColorSmoke {
    private ScrollThemeColorSmoke() {}

    public static void main(String[] args) {
        int lightBackground = 0xfff2f5f7;
        int lightAccent = 0xffe2f0ec;
        int lightSurface = 0xffebf0f3;
        int[] light = ScrollThemeColor.gradientColors(
                lightBackground, lightAccent, lightSurface, false);
        check(light.length == 5, "gradient should use five smooth stops");
        check(light[0] == lightBackground && light[4] == lightBackground,
                "light gradient must start and end at the base color");
        check(light[1] != lightBackground && light[2] != light[1] && light[3] != lightBackground,
                "light gradient should blend through distinct subtle tints");
        check(maxChannelDelta(lightBackground, light[1]) <= 16,
                "light accent tint must remain restrained");
        check(maxChannelDelta(lightBackground, light[2]) <= 16,
                "light mixed tint must remain restrained");
        check(maxChannelDelta(lightBackground, light[3]) <= 16,
                "light cool tint must remain restrained");

        int darkBackground = 0xff0e1216;
        int darkAccent = 0xff20342f;
        int darkSurface = 0xff1e262e;
        int[] dark = ScrollThemeColor.gradientColors(
                darkBackground, darkAccent, darkSurface, false);
        check(dark[0] == darkBackground && dark[4] == darkBackground,
                "dark gradient must start and end at the base color");
        for (int color : dark) {
            check(maxChannelDelta(darkBackground, color) <= 16, "dark tint must stay subtle");
        }

        int[] accessible = ScrollThemeColor.gradientColors(
                lightBackground, lightAccent, lightSurface, true);
        for (int color : accessible) {
            check(color == lightBackground, "high-contrast mode must suppress decorative color shifts");
        }
        check(ScrollThemeColor.blend(lightBackground, lightAccent, -1f) == lightBackground,
                "blend ratio below zero must clamp");
        check(ScrollThemeColor.blend(lightBackground, lightAccent, 2f) == lightAccent,
                "blend ratio above one must clamp");
        System.out.println("PASS scroll theme color smoke");
    }

    private static int maxChannelDelta(int a, int b) {
        int red = Math.abs(((a >>> 16) & 0xff) - ((b >>> 16) & 0xff));
        int green = Math.abs(((a >>> 8) & 0xff) - ((b >>> 8) & 0xff));
        int blue = Math.abs((a & 0xff) - (b & 0xff));
        return Math.max(red, Math.max(green, blue));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

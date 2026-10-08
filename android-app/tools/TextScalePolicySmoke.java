package com.cue.daymark;

/** JDK-only checks for the app-choice x system-font-scale policy. */
public final class TextScalePolicySmoke {
    private TextScalePolicySmoke() { }

    public static void main(String[] args) {
        assert TextScalePolicy.combined(1.0f, 1.0f) == 1.0f
                : "standard text at the default system scale must stay 1.0";
        assert TextScalePolicy.combined(1.25f, 1.0f) == 1.25f
                : "the app's extra-large choice must be honoured at the default system scale";
        assert Math.abs(TextScalePolicy.combined(1.25f, 1.3f) - 1.625f) < 0.0001f
                : "the system font scale must multiply the app choice";
        assert TextScalePolicy.combined(0.9f, 0.5f) == TextScalePolicy.MIN_SCALE
                : "an extreme small combination must clamp to MIN_SCALE";
        assert TextScalePolicy.combined(1.25f, 2.0f) == TextScalePolicy.MAX_SCALE
                : "an extreme large combination must clamp to MAX_SCALE";
        assert TextScalePolicy.combined(1.0f, 0f) == 1.0f
                : "a non-positive system scale must be treated as 1.0";
        assert TextScalePolicy.combined(1.0f, -1f) == 1.0f
                : "a negative system scale must be treated as 1.0";
        System.out.println("PASS text scale policy: system font scale multiplies the app choice and is clamped");
    }
}

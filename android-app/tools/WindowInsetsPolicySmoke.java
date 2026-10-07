package com.cue.daymark;

/** JDK-only checks for the window-insets (soft keyboard) policy. */
public final class WindowInsetsPolicySmoke {
    private WindowInsetsPolicySmoke() { }

    public static void main(String[] args) {
        assert WindowInsetsPolicy.CONSUME_IME_INSETS
                : "the IME inset must be consumed so the keyboard does not cover the composer";
        assert !WindowInsetsPolicy.DECOR_FITS_SYSTEM_WINDOWS
                : "content must be laid out edge-to-edge and padded by consumed insets";
        assert WindowInsetsPolicy.bottomPadding(48, 0) == 48
                : "with no keyboard the system-bar inset must be used";
        assert WindowInsetsPolicy.bottomPadding(48, 720) == 720
                : "with the keyboard open the larger IME inset must win";
        assert WindowInsetsPolicy.bottomPadding(0, 0) == 0
                : "no insets means no bottom padding";
        assert WindowInsetsPolicy.shouldApplyImeInsets(720)
                : "a present IME inset must be applied";
        assert !WindowInsetsPolicy.shouldApplyImeInsets(0)
                : "an absent IME inset must not add padding";
        System.out.println("PASS window insets policy: IME inset consumed, bottom padding = max(system bars, keyboard)");
    }
}

package com.cue.daymark;

/**
 * Pure policy for how the Activity consumes window insets.
 *
 * <p>The round-2 audit found that the insets listener only read
 * {@code systemBars() | displayCutout()} and never requested
 * {@code WindowInsets.Type.ime()}, and that {@code adjustResize} was never set.
 * As a result the composer and the browser result area slid behind the soft
 * keyboard. This policy centralises the decision so it is testable without a
 * device.
 */
final class WindowInsetsPolicy {
    /** The IME (soft keyboard) inset must be consumed so content is not covered. */
    static final boolean CONSUME_IME_INSETS = true;
    /** Content must be laid out edge-to-edge and padded by the insets we consume. */
    static final boolean DECOR_FITS_SYSTEM_WINDOWS = false;

    private WindowInsetsPolicy() { }

    /**
     * Bottom padding for the root view: the larger of the system-bar inset and the
     * IME inset, so the keyboard never covers the composer or the browser viewport.
     */
    static int bottomPadding(int systemBarsBottom, int imeBottom) {
        if (!CONSUME_IME_INSETS) return systemBarsBottom;
        return Math.max(systemBarsBottom, imeBottom);
    }

    /** Whether the IME inset is currently present and must be applied. */
    static boolean shouldApplyImeInsets(int imeBottom) {
        return CONSUME_IME_INSETS && imeBottom > 0;
    }
}

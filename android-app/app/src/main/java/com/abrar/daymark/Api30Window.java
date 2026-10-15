package com.abrar.daymark;

import android.annotation.SuppressLint;
import android.graphics.Insets;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

/**
 * API 29/30 window calls isolated from the launcher activity.
 *
 * <p>{@code Insets} and {@code WindowInsets.Type} do not exist before API 29/30.
 * Referencing them from a method that always runs crashes cold start on
 * Android 8 and 9. Callers must check the SDK version before loading this class.
 */
@SuppressLint("NewApi")
final class Api30Window {
    private Api30Window() { }

    static void relaxBarContrast(Window window) {
        window.setStatusBarContrastEnforced(false);
        window.setNavigationBarContrastEnforced(false);
    }

    static void setDecorFits(Window window, boolean fits) {
        window.setDecorFitsSystemWindows(fits);
    }

    static void applyInsets(View root, WindowInsets insets, int sidePad) {
        Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int imeBottom = WindowInsetsPolicy.CONSUME_IME_INSETS
                ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
        root.setPadding(sidePad + bars.left, bars.top, sidePad + bars.right,
                WindowInsetsPolicy.bottomPadding(bars.bottom, imeBottom));
    }
}

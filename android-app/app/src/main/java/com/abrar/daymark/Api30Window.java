package com.abrar.daymark;

import android.annotation.TargetApi;
import android.graphics.Insets;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

/**
 * API 30 window/insets calls isolated from the launcher activity.
 *
 * <p>{@code Insets} and {@code WindowInsets.Type} are API 30 platform types.
 * Callers must check the SDK version before loading this class. API-29-only
 * contrast methods live in {@link Api29Window} so API 29 never loads this class.
 */
@TargetApi(30)
final class Api30Window {
    private Api30Window() { }

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

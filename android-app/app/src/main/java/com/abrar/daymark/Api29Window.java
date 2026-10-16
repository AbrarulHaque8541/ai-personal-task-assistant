package com.abrar.daymark;

import android.annotation.SuppressLint;
import android.view.Window;

/**
 * API 29 window calls isolated from the launcher activity and higher-API helpers.
 *
 * <p>Keeping contrast methods here prevents class-loading {@code Api30Window}
 * on Android 10 (API 29), where {@code WindowInsets.Type} does not exist.
 */
@SuppressLint("NewApi")
final class Api29Window {
    private Api29Window() { }

    static void relaxBarContrast(Window window) {
        window.setStatusBarContrastEnforced(false);
        window.setNavigationBarContrastEnforced(false);
    }
}

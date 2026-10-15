package com.abrar.daymark;

import android.annotation.TargetApi;
import android.view.Window;

/**
 * API 29-only window calls. Keep this separate from API-30 types so Android 10
 * never has to load a class that references {@code WindowInsets.Type}.
 */
@TargetApi(29)
final class Api29Window {
    private Api29Window() { }

    static void relaxBarContrast(Window window) {
        window.setStatusBarContrastEnforced(false);
        window.setNavigationBarContrastEnforced(false);
    }
}

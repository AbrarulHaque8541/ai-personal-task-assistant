package com.abrar.daymark;

import android.annotation.TargetApi;
import android.app.Activity;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * API 33+ back callback isolated from {@code MainActivity}.
 *
 * <p>A field or direct reference to {@code OnBackInvokedCallback} inside the
 * launcher activity makes Android 8–12 refuse to load the activity at all
 * ({@code NoClassDefFoundError}) before {@code onCreate} runs. This class is
 * loaded only after an SDK version check.
 */
@TargetApi(33)
final class Api33Back {
    private Api33Back() { }

    static Object register(Activity activity, Runnable onBack) {
        OnBackInvokedCallback callback = onBack::run;
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        return callback;
    }

    static void unregister(Activity activity, Object handle) {
        if (!(handle instanceof OnBackInvokedCallback)) return;
        activity.getOnBackInvokedDispatcher()
                .unregisterOnBackInvokedCallback((OnBackInvokedCallback) handle);
    }
}

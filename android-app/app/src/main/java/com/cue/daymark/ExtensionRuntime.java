package com.cue.daymark;

import android.content.Context;
import android.webkit.WebView;

import java.util.List;

/** Applies enabled local extension packs to a WebView page. */
final class ExtensionRuntime {
    private final ExtensionStore store;

    private ExtensionRuntime(ExtensionStore store) {
        this.store = store;
    }

    static ExtensionRuntime create(Context context) {
        return new ExtensionRuntime(new ExtensionStore(context));
    }

    ExtensionStore store() {
        return store;
    }

    void onPageFinished(WebView webView, String url) {
        if (webView == null || url == null) return;
        if (!store.isGloballyEnabled()) return;
        List<BrowserExtension> matching = store.enabledMatching(url);
        if (matching.isEmpty()) return;
        ExtensionInjector.apply(webView, url, matching);
    }
}

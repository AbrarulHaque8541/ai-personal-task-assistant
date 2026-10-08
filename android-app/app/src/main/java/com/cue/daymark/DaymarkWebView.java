package com.cue.daymark;

import android.app.Activity;
import android.net.http.SslError;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceError;
import android.webkit.DownloadListener;
import android.graphics.Bitmap;

/** A narrow wrapper around Android System WebView; no native bridge or request interception is used. */
final class DaymarkWebView extends WebView {
    interface Listener {
        void onPageStarted(String url);
        void onPageFinished(String url);
        void onNavigationBlocked(String url);
        void onOfflineNavigationBlocked();
        void onHttpNavigationBlocked(String url, boolean redirect);
        void onLoadError();
        void onDownloadRequested(String url, String userAgent, String contentDisposition, String mimeType, long contentLength);
        void onRendererGone();
    }

    private final ExtensionRuntime extensionRuntime;

    DaymarkWebView(Activity activity, BrowserNetworkPolicy networkPolicy,
                   boolean safeBrowsingEnabled, Listener listener) {
        super(activity);
        this.extensionRuntime = ExtensionRuntime.create(activity);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        WebSettings settings = getSettings();
        settings.setBlockNetworkLoads(true);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSafeBrowsingEnabled(safeBrowsingEnabled);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        // Viewport/zoom/pop-up policy: see BrowserViewportPolicy. Multiple windows stay
        // disabled so target="_blank" result links load in this WebView (and still pass the
        // HTTPS/offline guard) instead of being silently dropped.
        settings.setSupportMultipleWindows(BrowserViewportPolicy.SUPPORT_MULTIPLE_WINDOWS);
        settings.setUseWideViewPort(BrowserViewportPolicy.USE_WIDE_VIEW_PORT);
        settings.setLoadWithOverviewMode(BrowserViewportPolicy.LOAD_WITH_OVERVIEW_MODE);
        settings.setSupportZoom(BrowserViewportPolicy.SUPPORT_ZOOM);
        settings.setBuiltInZoomControls(BrowserViewportPolicy.BUILT_IN_ZOOM_CONTROLS);
        settings.setDisplayZoomControls(BrowserViewportPolicy.DISPLAY_ZOOM_CONTROLS);

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!networkPolicy.allowsRemoteLoads()) {
                    listener.onOfflineNavigationBlocked();
                    return true;
                }
                if (request.isForMainFrame()
                        && "http".equalsIgnoreCase(request.getUrl().getScheme())) {
                    listener.onHttpNavigationBlocked(url, request.isRedirect());
                    return true;
                }
                if (BrowserAddress.isAllowedWebUrl(url)) return false;
                listener.onNavigationBlocked(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!networkPolicy.allowsRemoteLoads()) {
                    listener.onOfflineNavigationBlocked();
                    return;
                }
                listener.onPageStarted(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                listener.onPageFinished(url);
                // Local Daymark packs only (CSS/userscript-style). No chrome.* APIs, no request interception.
                if (extensionRuntime != null && networkPolicy.allowsRemoteLoads()) {
                    extensionRuntime.onPageFinished(view, url);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                listener.onLoadError();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                if (!networkPolicy.allowsRemoteLoads()) {
                    listener.onOfflineNavigationBlocked();
                    return;
                }
                if ("http".equalsIgnoreCase(request.getUrl().getScheme())) {
                    listener.onHttpNavigationBlocked(request.getUrl().toString(), request.isRedirect());
                } else {
                    listener.onLoadError();
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                listener.onRendererGone();
                return true;
            }
        });

        setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.deny();
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                         android.os.Message resultMsg) {
                // Multiple windows are disabled, so target="_blank" / window.open navigations
                // are loaded by this WebView and still pass through shouldOverrideUrlLoading's
                // HTTPS/offline guard. Returning false here would silently drop them.
                return BrowserViewportPolicy.allowsSeparateWindow();
            }
        });
        setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                listener.onDownloadRequested(url, userAgent, contentDisposition, mimeType, contentLength));
    }
}

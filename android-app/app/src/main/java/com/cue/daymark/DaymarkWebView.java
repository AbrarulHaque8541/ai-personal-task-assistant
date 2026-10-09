package com.cue.daymark;

import android.app.Activity;
import android.net.http.SslError;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
        void onMediaDownloadRequested(String url);
        void onShowFullscreen(View view, WebChromeClient.CustomViewCallback callback);
        void onHideFullscreen();
        void onRendererGone();
    }

    private final ExtensionRuntime extensionRuntime;
    private final Listener listener;

    boolean isShowingCustomView() {
        return fullscreenView != null;
    }

    void hideCustomView() {
        if (fullscreenView == null) return;
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenView = null;
        fullscreenCallback = null;
        listener.onHideFullscreen();
        if (callback != null) callback.onCustomViewHidden();
    }

    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    DaymarkWebView(Activity activity, BrowserNetworkPolicy networkPolicy,
                   boolean safeBrowsingEnabled, Listener listener) {
        super(activity);
        this.listener = listener;
        this.extensionRuntime = ExtensionRuntime.create(activity);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        WebSettings settings = getSettings();
        settings.setBlockNetworkLoads(true);
        settings.setJavaScriptEnabled(true);
        // Many sites (including Google) render poorly or blank with the default WebView UA.
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(BrowserViewportPolicy.allowsSeparateWindow());
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(safeBrowsingEnabled);
        }

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return true;
                String url = request.getUrl().toString();
                if (!networkPolicy.allowsRemoteLoads()) {
                    listener.onOfflineNavigationBlocked();
                    return true;
                }
                if ("http".equalsIgnoreCase(request.getUrl().getScheme())) {
                    listener.onHttpNavigationBlocked(url, request.isRedirect());
                    return true;
                }
                if (!url.startsWith("https://")) {
                    listener.onNavigationBlocked(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                listener.onPageStarted(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (extensionRuntime != null) {
                    extensionRuntime.onPageFinished(view, url);
                }
                listener.onPageFinished(url);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                listener.onLoadError();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    listener.onLoadError();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            android.webkit.WebResourceResponse errorResponse) {
                if (request != null && request.isForMainFrame() && errorResponse != null
                        && errorResponse.getStatusCode() >= 400) {
                    // Surface hard failures; soft 404 pages still finish via onPageFinished.
                }
            }

            @Override
            public void onFormResubmission(WebView view, android.os.Message dontResend, android.os.Message resend) {
                if (dontResend != null) dontResend.sendToTarget();
            }

            @Override
            public void onUnhandledKeyEvent(WebView view, android.view.KeyEvent event) {
                // default
            }

            @Override
            public void onScaleChanged(WebView view, float oldScale, float newScale) { }

            @Override
            public void onReceivedLoginRequest(WebView view, String realm, String account, String args) { }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                listener.onRendererGone();
                return true;
            }
        });

        setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                listener.onShowFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                if (fullscreenView == null) return;
                fullscreenView = null;
                fullscreenCallback = null;
                listener.onHideFullscreen();
            }

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
                return BrowserViewportPolicy.allowsSeparateWindow();
            }
        });
        setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                listener.onDownloadRequested(url, userAgent, contentDisposition, mimeType, contentLength));
    }
}

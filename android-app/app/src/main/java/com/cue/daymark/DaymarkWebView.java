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
        void onLoadError(int errorCode, String description, String failingUrl);
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
                if (request == null || request.getUrl() == null) return true;
                String url = request.getUrl().toString();
                if ("daymark-download".equalsIgnoreCase(request.getUrl().getScheme())) {
                    String mediaUrl = request.getUrl().getQueryParameter("url");
                    if (BrowserMediaPolicy.allowsHandoff(networkPolicy.allowsRemoteLoads(),
                            request.hasGesture(), request.getUrl().getScheme(),
                            request.getUrl().getHost(), mediaUrl)) {
                        listener.onMediaDownloadRequested(mediaUrl);
                    }
                    return true;
                }
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
                listener.onLoadError(-1, "certificate error: " + error.getPrimaryError(), error.getUrl());
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
                    listener.onLoadError(error.getErrorCode(),
                            error.getDescription() == null ? "" : error.getDescription().toString(),
                            request.getUrl().toString());
                }
            }

            @Override
            public void onFormResubmission(WebView view, android.os.Message dontResend, android.os.Message resend) {
                // Never silently re-POST form data; avoids accidental duplicate submissions.
                if (dontResend != null) dontResend.sendToTarget();
            }

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

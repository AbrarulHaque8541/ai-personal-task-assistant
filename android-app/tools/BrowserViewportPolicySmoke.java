package com.cue.daymark;

/** JDK-only checks for the embedded browser viewport, zoom, and pop-up policy. */
public final class BrowserViewportPolicySmoke {
    private BrowserViewportPolicySmoke() { }

    public static void main(String[] args) {
        assert !BrowserViewportPolicy.SUPPORT_MULTIPLE_WINDOWS
                : "multiple windows must stay disabled so target=_blank result links load in place";
        assert BrowserViewportPolicy.loadsNewWindowRequestsInPlace()
                : "target=_blank navigations must be handled by the current WebView";
        assert !BrowserViewportPolicy.allowsSeparateWindow()
                : "a second WebView would bypass the single-WebView HTTPS/offline guard";
        assert BrowserViewportPolicy.USE_WIDE_VIEW_PORT
                : "wide viewport must be enabled so desktop result pages fit the device width";
        assert BrowserViewportPolicy.LOAD_WITH_OVERVIEW_MODE
                : "overview mode must be enabled so the full page width is visible on first paint";
        assert BrowserViewportPolicy.SUPPORT_ZOOM
                : "pinch-zoom must be enabled so long result pages remain readable";
        assert BrowserViewportPolicy.BUILT_IN_ZOOM_CONTROLS
                : "built-in zoom controls must be available for discoverability";
        assert !BrowserViewportPolicy.DISPLAY_ZOOM_CONTROLS
                : "on-screen zoom controls must not cover page content";
        System.out.println("PASS browser viewport policy: target=_blank loads in place, wide viewport + overview + pinch-zoom enabled");
    }
}

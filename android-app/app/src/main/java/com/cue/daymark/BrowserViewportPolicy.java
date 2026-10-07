package com.cue.daymark;

/**
 * Pure policy for the embedded browser's viewport, zoom, and pop-up handling.
 *
 * <p>Two user-visible defects are governed here:
 *
 * <ol>
 *   <li><b>Dropped result links.</b> Search engines render result links with
 *       {@code target="_blank"}. With {@code setSupportMultipleWindows(true)} and an
 *       {@code onCreateWindow} that returns {@code false}, Android System WebView
 *       silently discards those navigations: no page opens and no error is surfaced.
 *       Disabling multiple windows makes WebView load such links in the current view,
 *       where the existing HTTPS/offline guard in {@code shouldOverrideUrlLoading}
 *       still runs. Opening a second WebView instead would bypass that guard.</li>
 *   <li><b>Cut-off pages.</b> Without a wide viewport and overview mode, desktop
 *       result pages render at a ~980px virtual width that does not fit the device,
 *       and with zoom disabled the user cannot pinch to read the rest.</li>
 * </ol>
 */
final class BrowserViewportPolicy {
    /**
     * Multiple windows must stay disabled so {@code target="_blank"} links load in the
     * current WebView instead of being dropped.
     */
    static final boolean SUPPORT_MULTIPLE_WINDOWS = false;
    /** Fit desktop pages to the device width instead of a 980px virtual viewport. */
    static final boolean USE_WIDE_VIEW_PORT = true;
    /** Scale the page so its full width is visible on first paint. */
    static final boolean LOAD_WITH_OVERVIEW_MODE = true;
    /** Allow pinch-to-zoom so long result pages remain readable. */
    static final boolean SUPPORT_ZOOM = true;
    /** Show the on-screen zoom controls for discoverability. */
    static final boolean BUILT_IN_ZOOM_CONTROLS = true;
    /** Keep the on-screen zoom controls from covering page content. */
    static final boolean DISPLAY_ZOOM_CONTROLS = false;

    private BrowserViewportPolicy() { }

    /**
     * Whether a {@code target="_blank"} / {@code window.open} navigation is loaded in the
     * current WebView. True whenever multiple windows are disabled, which is the only
     * configuration in which the HTTPS guard in {@code shouldOverrideUrlLoading} runs.
     */
    static boolean loadsNewWindowRequestsInPlace() {
        return !SUPPORT_MULTIPLE_WINDOWS;
    }

    /**
     * Whether the WebView may open a separate window. Always false: a second WebView
     * would bypass the single-WebView HTTPS/offline guard.
     */
    static boolean allowsSeparateWindow() {
        return SUPPORT_MULTIPLE_WINDOWS;
    }
}

package com.cue.daymark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Built-in Daymark packs — CSS/JS only, no native bridge. */
final class BuiltInExtensions {
    private BuiltInExtensions() { }

    static List<BrowserExtension> all() {
        List<BrowserExtension> list = new ArrayList<>();
        list.add(new BrowserExtension(
                "daymark.builtin.calm_reading",
                "Calm reading",
                "1.0.0",
                "Wider line-height and constrained measure for article-like pages.",
                true,
                true,
                Arrays.asList("*://*/*"),
                "html,body{scroll-behavior:smooth;} body{line-height:1.65!important;max-width:46rem;margin-left:auto!important;margin-right:auto!important;padding:0 1rem;} p,li{line-height:1.7!important;} img,video{max-width:100%;height:auto;}",
                "",
                "document_end"));
        list.add(new BrowserExtension(
                "daymark.builtin.hide_noise",
                "Hide common noise",
                "1.0.0",
                "Best-effort CSS hide for frequent clutter selectors (not a full adblocker).",
                false,
                true,
                Arrays.asList("*://*/*"),
                "[class*='cookie' i],[id*='cookie' i],[class*='newsletter' i],[class*='promo' i],.ad,.ads,.advert,.advertisement,[aria-label*='advert' i]{display:none!important;}",
                "",
                "document_end"));
        list.add(new BrowserExtension(
                "daymark.builtin.link_outline",
                "Link highlighter",
                "1.0.0",
                "Draws a soft outline on links for focus and accessibility.",
                false,
                true,
                Arrays.asList("*://*/*"),
                "a[href]{outline:1px dashed rgba(0,120,215,.35);outline-offset:2px;} a[href]:focus{outline:2px solid rgba(0,120,215,.8);}",
                "",
                "document_end"));
        return list;
    }
}

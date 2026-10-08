package com.cue.daymark;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts a subset of EasyList/ABP *cosmetic* rules (##selector) into CSS for WebView injection.
 * Network rules (||ads.example.com^) are NOT applied — Daymark does not intercept requests.
 * Research: same approach as WebView adblock userscripts that only hide elements.
 */
final class CosmeticFilterToCss {
    private CosmeticFilterToCss() { }

    static String toCss(String filterListText) {
        if (filterListText == null || filterListText.isEmpty()) return "";
        List<String> selectors = new ArrayList<>();
        String[] lines = filterListText.split("\r?\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("!") || t.startsWith("[")) continue;
            // domain##selector or ##selector
            int idx = t.indexOf("##");
            if (idx < 0) continue;
            if (t.contains("#@#") || t.contains("#?#") || t.contains("#$#")) continue; // skip extended
            String selector = t.substring(idx + 2).trim();
            if (selector.isEmpty() || selector.length() > 300) continue;
            if (selector.contains("{") || selector.contains("}")) continue;
            selectors.add(selector);
            if (selectors.size() >= 500) break; // keep packs small
        }
        if (selectors.isEmpty()) return "";
        StringBuilder css = new StringBuilder();
        for (int i = 0; i < selectors.size(); i++) {
            if (i > 0) css.append(',');
            css.append(selectors.get(i));
        }
        css.append("{display:none!important;}\n");
        return css.toString();
    }

    static BrowserExtension packFromFilterList(String name, String filterListText) {
        String css = toCss(filterListText);
        if (css.isEmpty()) {
            throw new IllegalArgumentException("No cosmetic ## rules found (network-only lists cannot run in WebView).");
        }
        String id = "import.cosmetic." + (name == null ? "list" : name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "."));
        return new BrowserExtension(
                id,
                name == null ? "Cosmetic filter pack" : name,
                "1.0.0",
                "Cosmetic element-hide rules only. Network blocking is not available in Daymark WebView.",
                true,
                false,
                java.util.Collections.singletonList("*://*/*"),
                css,
                "",
                "document_end");
    }
}

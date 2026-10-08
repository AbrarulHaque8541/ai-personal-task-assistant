package com.cue.daymark;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Middle-layer converter: Chrome-style extension fragments → Daymark pack.
 *
 * Reality check (research-backed):
 * - Full Chrome Web Store .crx / chrome.* APIs need a Chromium embed (Kiwi-class) — heavy.
 * - What works on System WebView: content_scripts CSS/JS + userscripts (WebMonkey path).
 * - This importer extracts only content_scripts / CSS that can run via page injection.
 * Background service workers, chrome.tabs, chrome.storage sync, native messaging: not portable.
 */
final class ExtensionChromeImport {
    private ExtensionChromeImport() { }

    /**
     * @param manifestJson Chrome extension manifest.json text (MV2/MV3 subset)
     * @param joinedContentJs optional concatenation of content script files (in manifest order)
     * @param joinedContentCss optional concatenation of content CSS files
     */
    static BrowserExtension fromManifestAndContent(String manifestJson,
                                                   String joinedContentJs,
                                                   String joinedContentCss) throws Exception {
        if (manifestJson == null || manifestJson.trim().isEmpty()) {
            throw new IllegalArgumentException("manifest.json is required.");
        }
        JSONObject manifest = new JSONObject(manifestJson.trim());
        String name = manifest.optString("name", "Imported extension").trim();
        String version = manifest.optString("version", "1.0.0").trim();
        String description = manifest.optString("description", "Converted content_scripts only").trim();
        String idBase = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".");
        if (idBase.length() > 40) idBase = idBase.substring(0, 40);
        String id = "import.chrome." + idBase;

        List<String> matches = new ArrayList<>();
        StringBuilder css = new StringBuilder();
        StringBuilder js = new StringBuilder();

        if (joinedContentCss != null && !joinedContentCss.isEmpty()) {
            css.append(joinedContentCss);
        }
        if (joinedContentJs != null && !joinedContentJs.isEmpty()) {
            js.append(joinedContentJs);
        }

        // MV2 content_scripts
        JSONArray contentScripts = manifest.optJSONArray("content_scripts");
        if (contentScripts != null) {
            for (int i = 0; i < contentScripts.length(); i++) {
                JSONObject cs = contentScripts.optJSONObject(i);
                if (cs == null) continue;
                JSONArray m = cs.optJSONArray("matches");
                if (m != null) {
                    for (int j = 0; j < m.length() && matches.size() < 32; j++) {
                        String pat = m.optString(j, "").trim();
                        if (!pat.isEmpty()) matches.add(pat);
                    }
                }
                // File names listed in manifest are not auto-loaded here — caller supplies joined bodies.
                // We still record matches so the pack only runs on intended sites.
            }
        }

        // MV3 may only declare content_scripts the same way for static scripts.
        if (matches.isEmpty()) matches.add("*://*/*");

        if (css.length() == 0 && js.length() == 0) {
            throw new IllegalArgumentException(
                    "No CSS/JS payload. Export the extension's content script/CSS files and paste them, "
                            + "or use a userscript. Background-only extensions cannot run in WebView.");
        }

        // Strip obvious chrome.* calls that will throw at runtime (best-effort warning in description).
        String jsBody = js.toString();
        boolean usesChromeApi = jsBody.contains("chrome.") || jsBody.contains("browser.");
        if (usesChromeApi) {
            description = description + " [Warning: contains chrome./browser. APIs — those calls will fail in WebView; "
                    + "only pure DOM/CSS parts will work.]";
            // Do not auto-delete code — user can still benefit from DOM parts; failures are try/caught per pack.
        }

        return new BrowserExtension(
                id,
                name + " (converted)",
                version,
                description,
                true,
                false,
                matches,
                css.toString(),
                jsBody,
                "document_end");
    }
}

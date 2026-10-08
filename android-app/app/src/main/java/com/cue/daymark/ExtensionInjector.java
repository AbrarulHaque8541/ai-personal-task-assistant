package com.cue.daymark;

import android.webkit.WebView;

import java.util.List;
import java.util.Locale;

/** Builds and applies page-local CSS/JS for enabled packs. No native bridge. */
final class ExtensionInjector {
    private ExtensionInjector() { }

    static void apply(WebView webView, String pageUrl, List<BrowserExtension> enabledMatching) {
        if (webView == null || enabledMatching == null || enabledMatching.isEmpty()) return;
        if (!BrowserAddress.isAllowedWebUrl(pageUrl)) return;
        StringBuilder css = new StringBuilder();
        StringBuilder js = new StringBuilder();
        for (BrowserExtension ext : enabledMatching) {
            if (ext.css != null && !ext.css.isEmpty()) {
                css.append("\n/* ").append(safeComment(ext.id)).append(" */\n").append(ext.css).append('\n');
            }
            if (ext.js != null && !ext.js.isEmpty()) {
                js.append("\ntry{\n").append(ext.js).append("\n}catch(e){}\n");
            }
        }
        if (css.length() > 0) {
            String injectCss = "(function(){try{var id='daymark-ext-css';var el=document.getElementById(id);"
                    + "if(!el){el=document.createElement('style');el.id=id;"
                    + "(document.head||document.documentElement).appendChild(el);}el.textContent="
                    + jsonString(css.toString())
                    + ";}catch(e){}})();"
                    ;
            webView.evaluateJavascript(injectCss, null);
        }
        if (js.length() > 0) {
            String injectJs = "(function(){try{" + js + "}catch(e){}})();";
            webView.evaluateJavascript(injectJs, null);
        }
    }

    private static String safeComment(String id) {
        if (id == null) return "ext";
        return id.replace('*', '_').replace('/', '_');
    }

    /** Minimal JSON string escape for embedding in evaluateJavascript. */
    static String jsonString(String value) {
        if (value == null) return "\"\"";
        StringBuilder sb = new StringBuilder(value.length() + 16);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\u2028': sb.append("\\u2028"); break;
                case '\u2029': sb.append("\\u2029"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}

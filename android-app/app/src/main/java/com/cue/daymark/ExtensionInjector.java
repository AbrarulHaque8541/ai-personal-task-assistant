package com.cue.daymark;

import android.webkit.WebView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies page-local CSS/JS for enabled packs. No native bridge.
 * Injects a small SPA/MutationObserver bootstrap so pushState pages still get CSS re-applied.
 */
final class ExtensionInjector {
    private ExtensionInjector() { }

    static void apply(WebView webView, String pageUrl, List<BrowserExtension> enabledMatching) {
        if (webView == null || enabledMatching == null || enabledMatching.isEmpty()) return;
        if (!BrowserAddress.isAllowedWebUrl(pageUrl)) return;

        List<BrowserExtension> limited = new ArrayList<>();
        for (BrowserExtension ext : enabledMatching) {
            if (limited.size() >= BrowserExtension.MAX_ENABLED_PER_PAGE) break;
            limited.add(ext);
        }

        StringBuilder css = new StringBuilder();
        StringBuilder js = new StringBuilder();
        for (BrowserExtension ext : limited) {
            if (ext.css != null && !ext.css.isEmpty()) {
                css.append("\n/* ").append(safeComment(ext.id)).append(" */\n").append(ext.css).append('\n');
            }
            if (ext.js != null && !ext.js.isEmpty()) {
                // Per-pack IIFE + duplicate-run guard (pack id).
                js.append("(function(){try{")
                        .append("var __dmId=").append(jsonString(ext.id)).append(";")
                        .append("if(window.__daymarkRan&&window.__daymarkRan[__dmId])return;")
                        .append("window.__daymarkRan=window.__daymarkRan||{};window.__daymarkRan[__dmId]=1;")
                        .append(ext.js)
                        .append("}catch(e){}})();\n");
            }
        }

        // Bootstrap: CSS apply helper + history/MutationObserver re-apply (consensus Must).
        String bootstrap = "(function(){try{"
                + "if(window.__daymarkBoot)return;window.__daymarkBoot=1;"
                + "window.__daymarkApplyCss=function(css){try{"
                + "var id='daymark-ext-css';var el=document.getElementById(id);"
                + "if(!el){el=document.createElement('style');el.id=id;"
                + "(document.head||document.documentElement).appendChild(el);}"
                + "el.textContent=css||'';}catch(e){}};"
                + "window.GM_addStyle=function(css){try{"
                + "var s=document.createElement('style');s.textContent=String(css||'');"
                + "(document.head||document.documentElement).appendChild(s);}catch(e){}};"
                + "var _ps=history.pushState,_rs=history.replaceState;"
                + "function _dmNav(){try{if(window.__daymarkLastCss)window.__daymarkApplyCss(window.__daymarkLastCss);}catch(e){}}"
                + "history.pushState=function(){_ps.apply(this,arguments);_dmNav();};"
                + "history.replaceState=function(){_rs.apply(this,arguments);_dmNav();};"
                + "window.addEventListener('popstate',_dmNav);"
                + "try{var mo=new MutationObserver(function(){_dmNav();});"
                + "mo.observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
                + "}catch(e){}})();"
                ;
        webView.evaluateJavascript(bootstrap, null);

        if (css.length() > 0) {
            String cssText = css.toString();
            String injectCss = "(function(){try{"
                    + "window.__daymarkLastCss=" + jsonString(cssText) + ";"
                    + "if(window.__daymarkApplyCss)window.__daymarkApplyCss(window.__daymarkLastCss);"
                    + "else{var id='daymark-ext-css';var el=document.getElementById(id);"
                    + "if(!el){el=document.createElement('style');el.id=id;"
                    + "(document.head||document.documentElement).appendChild(el);}el.textContent=window.__daymarkLastCss;}"
                    + "}catch(e){}})();"
                    ;
            webView.evaluateJavascript(injectCss, null);
        }
        if (js.length() > 0) {
            webView.evaluateJavascript("(function(){try{" + js + "}catch(e){}})();", null);
        }
    }

    private static String safeComment(String id) {
        if (id == null) return "ext";
        return id.replace('*', '_').replace('/', '_');
    }

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

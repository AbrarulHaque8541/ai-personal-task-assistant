package com.cue.daymark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Local Daymark extension pack (userscript/CSS). Not a Chrome Web Store add-on. */
final class BrowserExtension {
    final String id;
    final String name;
    final String version;
    final String description;
    final boolean enabled;
    final boolean builtIn;
    final List<String> matches;
    final String css;
    final String js;
    final String runAt;

    BrowserExtension(String id, String name, String version, String description,
                     boolean enabled, boolean builtIn, List<String> matches,
                     String css, String js, String runAt) {
        this.id = id == null ? "" : id.trim();
        this.name = name == null ? "Extension" : name.trim();
        this.version = version == null ? "0" : version.trim();
        this.description = description == null ? "" : description.trim();
        this.enabled = enabled;
        this.builtIn = builtIn;
        this.matches = matches == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(matches));
        this.css = css == null ? "" : css;
        this.js = js == null ? "" : js;
        String at = runAt == null ? "document_end" : runAt.trim().toLowerCase(Locale.ROOT);
        this.runAt = "document_start".equals(at) ? "document_start" : "document_end";
    }

    boolean matchesUrl(String url) {
        if (url == null || url.isEmpty() || matches.isEmpty()) return false;
        if (!BrowserAddress.isAllowedWebUrl(url)) return false;
        for (String pattern : matches) {
            if (MatchRules.matches(pattern, url)) return true;
        }
        return false;
    }

    BrowserExtension withEnabled(boolean value) {
        return new BrowserExtension(id, name, version, description, value, builtIn, matches, css, js, runAt);
    }

    /** Simple host/path matchers used by Daymark packs and limited userscript @match lines. */
    static final class MatchRules {
        private MatchRules() { }

        static boolean matches(String pattern, String url) {
            if (pattern == null || url == null) return false;
            String p = pattern.trim();
            if (p.isEmpty()) return false;
            if ("<all_urls>".equals(p) || "*://*/*".equals(p)) {
                return BrowserAddress.isAllowedWebUrl(url);
            }
            try {
                java.net.URI uri = new java.net.URI(url);
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                // *://host/*
                if (p.startsWith("*://")) {
                    String rest = p.substring(4);
                    int slash = rest.indexOf('/');
                    String hostPat = slash < 0 ? rest : rest.substring(0, slash);
                    String pathPat = slash < 0 ? "/*" : rest.substring(slash);
                    return hostMatches(hostPat, host) && pathMatches(pathPat, path);
                }
                if (p.startsWith("https://")) {
                    String rest = p.substring("https://".length());
                    int slash = rest.indexOf('/');
                    String hostPat = slash < 0 ? rest : rest.substring(0, slash);
                    String pathPat = slash < 0 ? "/*" : rest.substring(slash);
                    return hostMatches(hostPat, host) && pathMatches(pathPat, path);
                }
                return false;
            } catch (Exception ignored) {
                return false;
            }
        }

        private static boolean hostMatches(String pattern, String host) {
            if (pattern == null || host == null) return false;
            String p = pattern.toLowerCase(Locale.ROOT);
            if ("*".equals(p)) return true;
            if (p.startsWith("*.")) {
                String suffix = p.substring(1); // .example.com
                return host.endsWith(suffix) || host.equals(p.substring(2));
            }
            return host.equals(p);
        }

        private static boolean pathMatches(String pattern, String path) {
            if (pattern == null) return false;
            if ("/*".equals(pattern) || "*".equals(pattern)) return true;
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                return path.startsWith(prefix);
            }
            return path.equals(pattern);
        }
    }
}

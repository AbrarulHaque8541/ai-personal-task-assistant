package com.cue.daymark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Local Daymark extension pack (userscript/CSS). Not a Chrome Web Store add-on. */
final class BrowserExtension {
    static final int MAX_ENABLED_PER_PAGE = 8;
    static final int MAX_CSS_CHARS = 120_000;
    static final int MAX_JS_CHARS = 80_000;

    final String id;
    final String name;
    final String version;
    final String description;
    final boolean enabled;
    final boolean builtIn;
    final List<String> matches;
    final List<String> excludes;
    final String css;
    final String js;
    final String runAt;
    /** Human-readable import warnings (unsupported grants, etc.). */
    final String warnings;

    BrowserExtension(String id, String name, String version, String description,
                     boolean enabled, boolean builtIn, List<String> matches,
                     String css, String js, String runAt) {
        this(id, name, version, description, enabled, builtIn, matches,
                Collections.<String>emptyList(), css, js, runAt, "");
    }

    BrowserExtension(String id, String name, String version, String description,
                     boolean enabled, boolean builtIn, List<String> matches, List<String> excludes,
                     String css, String js, String runAt, String warnings) {
        this.id = id == null ? "" : id.trim();
        this.name = name == null ? "Extension" : name.trim();
        this.version = version == null ? "0" : version.trim();
        this.description = description == null ? "" : description.trim();
        this.enabled = enabled;
        this.builtIn = builtIn;
        this.matches = matches == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(matches));
        this.excludes = excludes == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(excludes));
        String c = css == null ? "" : css;
        String j = js == null ? "" : js;
        if (c.length() > MAX_CSS_CHARS) c = c.substring(0, MAX_CSS_CHARS);
        if (j.length() > MAX_JS_CHARS) j = j.substring(0, MAX_JS_CHARS);
        this.css = c;
        this.js = j;
        String at = runAt == null ? "document_end" : runAt.trim().toLowerCase(Locale.ROOT);
        this.runAt = "document_start".equals(at) ? "document_start" : "document_end";
        this.warnings = warnings == null ? "" : warnings.trim();
    }

    boolean matchesUrl(String url) {
        if (url == null || url.isEmpty() || matches.isEmpty()) return false;
        if (!BrowserAddress.isAllowedWebUrl(url)) return false;
        for (String pattern : excludes) {
            if (MatchRules.matches(pattern, url)) return false;
        }
        for (String pattern : matches) {
            if (MatchRules.matches(pattern, url)) return true;
        }
        return false;
    }

    BrowserExtension withEnabled(boolean value) {
        return new BrowserExtension(id, name, version, description, value, builtIn,
                matches, excludes, css, js, runAt, warnings);
    }

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
                String suffix = p.substring(1);
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

package com.cue.daymark;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Daymark JSON packs and a minimal userscript header subset (@grant none only). */
final class ExtensionPackageParser {
    private static final int MAX_PACK_CHARS = 200_000;
    /** Total decompressed budget across every archive entry, including skipped file types (issue #190). */
    private static final long MAX_ARCHIVE_DECOMPRESSED_BYTES = 4L * 1024 * 1024;
    /** Decompressed budget for a single archive entry, including skipped file types (issue #190). */
    private static final long MAX_ENTRY_DECOMPRESSED_BYTES = 2L * 1024 * 1024;
    /** Effective-timing disclosure appended whenever an import requests document_start (issue #192). */
    private static final String TIMING_NOTE =
            "document_start run timing is not supported: Daymark injects after the page has loaded (document_end, best effort)";
    private static final Pattern USERSCRIPT_HEADER = Pattern.compile(
            "(?s)==UserScript==\\s*(.*?)==/UserScript==");
    // Keys may contain hyphens (@run-at); w alone never matched them, so
    // @run-at directives were silently ignored before this character class.
    private static final Pattern META = Pattern.compile(
            "@([\\w-]+)\\s+(.+)");
    // Supported match patterns: * or https scheme, sane wildcard-or-DNS host, required path.
    // Imported manifests fail closed on anything else instead of broadening scope (issue #194).
    private static final Pattern SUPPORTED_MATCH = Pattern.compile(
            "^(\\*|https)://(\\*|(\\*\\.)?[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*)/.*$");

    private ExtensionPackageParser() { }

    static BrowserExtension parseDaymarkJson(String raw, boolean builtIn) throws Exception {
        if (raw == null) throw new IllegalArgumentException("Empty extension pack.");
        String text = raw.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Empty extension pack.");
        if (text.length() > MAX_PACK_CHARS) {
            throw new IllegalArgumentException("Extension pack is too large (max 200 KB text).");
        }
        JSONObject o = new JSONObject(text);
        String id = o.optString("id", "").trim();
        String name = o.optString("name", "").trim();
        if (id.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("Extension pack requires id and name.");
        }
        if (!id.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalArgumentException("Extension id must be 1–80 safe characters.");
        }
        List<String> matches = readStringList(o.optJSONArray("matches"), 32);
        List<String> excludes = readStringList(o.optJSONArray("excludes"), 32);
        validateScopePatterns(matches, "Daymark extension matches", true);
        validateScopePatterns(excludes, "Daymark extension excludes", false);
        List<String> disabledSites = new ArrayList<>();
        for (String site : readStringList(o.optJSONArray("disabledSites"), 64)) {
            String host = BrowserExtension.normalizeSiteHost(site);
            if (host != null && !disabledSites.contains(host)) disabledSites.add(host);
        }
        String css = o.optString("css", "");
        String js = o.optString("js", "");
        if (css.length() + js.length() > MAX_PACK_CHARS) {
            throw new IllegalArgumentException("Extension script/css too large.");
        }
        String warnings = o.optString("warnings", "");
        // Storage round trips re-parse this path, so the disclosure must stay idempotent.
        if (o.optString("runAt", "document_end").toLowerCase(Locale.ROOT).contains("start")
                && !warnings.contains("document_start run timing is not supported")) {
            warnings = warnings.isEmpty() ? TIMING_NOTE : warnings + " " + TIMING_NOTE;
        }
        return new BrowserExtension(
                id, name, o.optString("version", "1.0.0"), o.optString("description", ""),
                o.optBoolean("enabled", true), builtIn, matches, excludes, css, js,
                "document_end", warnings, disabledSites);
    }

    /**
     * Userscript import: @name @match @exclude @run-at @grant none only.
     * Unsupported grants become warnings; body still imported for DOM-only use at user risk.
     */
    /** Import a WebExtension manifest by translating only page-local content_scripts. */
    static BrowserExtension parseWebExtensionManifest(String raw) throws Exception {
        return buildWebExtension(new JSONObject(requirePackText(raw)), null);
    }

    /** Import ZIP/XPI/CRX3 packages without executing privileged extension APIs. */
    static BrowserExtension parseWebExtensionArchive(byte[] archive) throws Exception {
        if (archive == null || archive.length == 0 || archive.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Extension archive is empty or exceeds the 5 MB import limit.");
        }
        byte[] zipBytes = normalizeZipBytes(archive);
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int entryCount = 0;
            long totalDecompressed = 0;
            int totalText = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > 128) {
                    throw new IllegalArgumentException("Extension archive contains too many files or directory entries.");
                }
                String originalPath = entry.getName();
                String path = originalPath == null ? "" : originalPath.replace('\\', '/');
                // Path and type checks decide storage only. Every entry is still drained within
                // the decompressed budget, so ignored files cannot bypass resource limits (issue #190).
                boolean store = !entry.isDirectory() && originalPath != null
                        && !path.startsWith("/") && !path.contains("../") && path.indexOf('\\') < 0
                        && (path.equals("manifest.json") || path.endsWith(".js") || path.endsWith(".css"));
                long entryDecompressed = 0;
                ByteArrayOutputStream out = store ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryDecompressed += read;
                    totalDecompressed += read;
                    if (entryDecompressed > MAX_ENTRY_DECOMPRESSED_BYTES) {
                        throw new IllegalArgumentException("Extension archive entry '" + path
                                + "' expands beyond the 2 MB decompressed entry limit.");
                    }
                    if (totalDecompressed > MAX_ARCHIVE_DECOMPRESSED_BYTES) {
                        throw new IllegalArgumentException(
                                "Extension archive expands beyond the 4 MB total decompressed limit.");
                    }
                    if (store) {
                        totalText += read;
                        if (totalText > MAX_PACK_CHARS) {
                            throw new IllegalArgumentException("Extension code is too large (max 200 KB text).");
                        }
                        out.write(buffer, 0, read);
                    }
                }
                if (store) files.put(path, out.toString("UTF-8"));
            }
        }
        String manifest = files.get("manifest.json");
        if (manifest == null) throw new IllegalArgumentException("Extension archive does not contain manifest.json.");
        return buildWebExtension(new JSONObject(manifest), files);
    }

    private static BrowserExtension buildWebExtension(JSONObject manifest, Map<String, String> files) {
        String name = manifest.optString("name", "").trim();
        String version = manifest.optString("version", "1.0.0").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Extension manifest requires a name.");
        JSONArray scripts = manifest.optJSONArray("content_scripts");
        if (scripts == null || scripts.length() == 0) {
            throw new IllegalArgumentException("No content_scripts found. This extension needs APIs or UI Daymark cannot execute.");
        }
        List<String> matches = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        StringBuilder css = new StringBuilder();
        StringBuilder js = new StringBuilder();
        // Daymark injects only after page finish, so the effective timing is always document_end.
        String runAt = "document_end";
        boolean requestedStartTiming = false;
        StringBuilder warnings = new StringBuilder("Imported in Daymark compatibility mode; only page-local content scripts are supported.");
        for (int i = 0; i < scripts.length() && i < 32; i++) {
            JSONObject item = scripts.optJSONObject(i);
            if (item == null) continue;
            List<String> entryMatches = readValidatedPatterns(item.optJSONArray("matches"), "matches");
            List<String> entryExcludes = readValidatedPatterns(item.optJSONArray("exclude_matches"), "exclude_matches");
            if (entryMatches.isEmpty()) throw new IllegalArgumentException(
                    "Content script declares no valid match rules; Daymark fails closed instead of"
                            + " running imported code on all sites.");
            for (String pattern : entryMatches) {
                if (matches.size() < 32 && !matches.contains(pattern)) matches.add(pattern);
            }
            for (String pattern : entryExcludes) {
                if (excludes.size() < 32 && !excludes.contains(pattern)) excludes.add(pattern);
            }
            String itemRunAt = item.optString("run_at", "document_idle").toLowerCase(Locale.ROOT);
            if (itemRunAt.contains("start")) requestedStartTiming = true;
            appendFiles(css, item.optJSONArray("css"), files, "CSS", warnings);
            appendFiles(js, item.optJSONArray("js"), files, "JS", warnings);
        }
        if (requestedStartTiming) warnings.append(' ').append(TIMING_NOTE).append('.');
        if (manifest.has("background")) warnings.append(" Background/service worker was not imported.");
        if (manifest.has("action") || manifest.has("browser_action") || manifest.has("page_action")) warnings.append(" Toolbar actions/popups were not imported.");
        if (manifest.has("permissions") || manifest.has("host_permissions")) warnings.append(" Requested permissions were not granted.");
        // Content-derived SHA-256 digest: distinct manifests can no longer collide and overwrite
        // each other through the 32-bit String.hashCode id (issue #193).
        String id = "webext." + sha256Hex(manifest.toString() + "\nCSS:\n" + css + "\nJS:\n" + js);
        return new BrowserExtension(id, name, version, manifest.optString("description", ""), true, false,
                matches, excludes, css.toString(), js.toString(), runAt, warnings.toString());
    }

    /** Validate explicit scopes and never replace an absent scope with an all-sites wildcard. */
    private static void validateScopePatterns(List<String> patterns, String field, boolean required) {
        if (patterns == null || patterns.isEmpty()) {
            if (required) throw new IllegalArgumentException(field + " requires at least one supported match scope.");
            return;
        }
        for (String pattern : patterns) {
            if (!isSupportedMatchPattern(pattern)) {
                throw new IllegalArgumentException(field + " contains an unsupported match scope: " + pattern);
            }
        }
    }

    /** Fail closed on empty or unsupported match patterns instead of silently broadening scope (issue #194). */
    private static List<String> readValidatedPatterns(JSONArray values, String field) {
        List<String> out = new ArrayList<>();
        if (values == null) return out;
        for (int i = 0; i < values.length() && out.size() < 32; i++) {
            String value = values.optString(i, "").trim();
            if (value.isEmpty()) throw new IllegalArgumentException(
                    "Content script " + field + " contains an empty pattern; invalid match scope fails closed.");
            if (!isSupportedMatchPattern(value)) throw new IllegalArgumentException(
                    "Unsupported match pattern '" + value + "' in " + field
                            + "; Daymark supports HTTPS-only patterns with a declared path.");
            if (!out.contains(value)) out.add(value);
        }
        return out;
    }

    static boolean isSupportedMatchPattern(String value) {
        if (value == null) return false;
        String pattern = value.trim();
        if (pattern.equals("<all_urls>")) return true;
        Matcher matcher = SUPPORTED_MATCH.matcher(pattern);
        if (!matcher.matches()) return false;
        int pathStart = pattern.indexOf('/', pattern.indexOf("://") + 3);
        if (pathStart < 0) return false;
        String path = pattern.substring(pathStart);
        return path.indexOf('*') < 0 || (path.endsWith("*") && path.lastIndexOf('*') == path.length() - 1);
    }

    private static void appendFiles(StringBuilder output, JSONArray values, Map<String, String> files,
                                    String kind, StringBuilder warnings) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            String path = values.optString(i, "").trim();
            if (path.isEmpty()) continue;
            String code = files == null ? null : files.get(path);
            if (code == null) {
                warnings.append(' ').append(kind).append(" file ").append(path).append(" was not found in the archive.");
                continue;
            }
            if (output.length() + code.length() > MAX_PACK_CHARS) throw new IllegalArgumentException("Imported extension code is too large.");
            output.append("\n/* ").append(path.replace("*/", "* /")).append(" */\n").append(code).append('\n');
        }
    }

    /** Full 64-hex-character (256-bit) SHA-256 digest, stable for identical input content. */
    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (int i = 0; i < hash.length; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", hash[i] & 0xff));
            }
            return hex.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable on this runtime", unavailable);
        }
    }

    private static String requirePackText(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("Empty extension manifest.");
        if (raw.length() > MAX_PACK_CHARS) throw new IllegalArgumentException("Extension manifest is too large.");
        return raw;
    }

    private static byte[] normalizeZipBytes(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K') return bytes;
        if (bytes.length >= 12 && bytes[0] == 'C' && bytes[1] == 'r' && bytes[2] == '2' && bytes[3] == '4') {
            int version = (bytes[4] & 0xff) | ((bytes[5] & 0xff) << 8) | ((bytes[6] & 0xff) << 16) | ((bytes[7] & 0xff) << 24);
            int header = (bytes[8] & 0xff) | ((bytes[9] & 0xff) << 8) | ((bytes[10] & 0xff) << 16) | ((bytes[11] & 0xff) << 24);
            if (version == 3 && header >= 0 && header <= bytes.length - 12) {
                return java.util.Arrays.copyOfRange(bytes, 12 + header, bytes.length);
            }
        }
        throw new IllegalArgumentException("Unsupported package. Use a ZIP/XPI/CRX3, Daymark JSON, or compatible userscript.");
    }

    static BrowserExtension parseUserScript(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty userscript.");
        }
        if (raw.length() > MAX_PACK_CHARS) {
            throw new IllegalArgumentException("Userscript too large.");
        }
        Matcher header = USERSCRIPT_HEADER.matcher(raw);
        String name = "Imported script";
        String version = "1.0.0";
        String description = "Imported userscript";
        // Daymark injects only after page finish, so the effective timing is always document_end.
        String runAt = "document_end";
        boolean requestedStartTiming = false;
        List<String> matches = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        List<String> grants = new ArrayList<>();
        String id = "userscript." + sha256Hex(raw);
        String body = raw.trim();
        if (header.find()) {
            String block = header.group(1);
            body = raw.substring(header.end()).trim();
            Matcher meta = META.matcher(block);
            while (meta.find()) {
                String key = meta.group(1).toLowerCase(Locale.ROOT);
                String value = meta.group(2).trim();
                switch (key) {
                    case "name": name = value; break;
                    case "version": version = value; break;
                    case "description": description = value; break;
                    case "match":
                    case "include":
                        if (matches.size() < 32) matches.add(value);
                        break;
                    case "exclude":
                        if (excludes.size() < 32) excludes.add(value);
                        break;
                    case "run-at":
                    case "runat":
                        if (value.contains("start")) requestedStartTiming = true;
                        break;
                    case "grant":
                        grants.add(value);
                        break;
                    default: break;
                }
            }
        }
        validateScopePatterns(matches, "Userscript matches", true);
        validateScopePatterns(excludes, "Userscript excludes", false);

        StringBuilder warnings = new StringBuilder();
        boolean unsafeGrant = false;
        for (String g : grants) {
            String gl = g.toLowerCase(Locale.ROOT);
            if ("none".equals(gl) || "gm_addstyle".equals(gl)) continue;
            unsafeGrant = true;
            if (warnings.length() > 0) warnings.append(';');
            warnings.append("unsupported @grant ").append(g);
        }
        if (unsafeGrant) {
            description = description + " [Daymark: only @grant none / GM_addStyle; other GM APIs are not provided.]";
        }
        // Remote @require is refused by policy — strip nothing, but warn if present in header block.
        if (raw.contains("@require") || raw.contains("@resource")) {
            if (warnings.length() > 0) warnings.append(';');
            warnings.append("@require/@resource not fetched (offline packs only)");
        }
        if (requestedStartTiming) {
            if (warnings.length() > 0) warnings.append(';');
            warnings.append(TIMING_NOTE);
        }

        return new BrowserExtension(id, name, version, description, true, false,
                matches, excludes, "", body, runAt, warnings.toString());
    }

    static String toDaymarkJson(BrowserExtension ext) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", ext.id);
        o.put("name", ext.name);
        o.put("version", ext.version);
        o.put("description", ext.description);
        o.put("enabled", ext.enabled);
        JSONArray matches = new JSONArray();
        for (String m : ext.matches) matches.put(m);
        o.put("matches", matches);
        JSONArray excludes = new JSONArray();
        for (String m : ext.excludes) excludes.put(m);
        o.put("excludes", excludes);
        JSONArray disabledSites = new JSONArray();
        for (String site : ext.disabledSites) disabledSites.put(site);
        o.put("disabledSites", disabledSites);
        o.put("css", ext.css);
        o.put("js", ext.js);
        o.put("runAt", ext.runAt);
        if (ext.warnings != null && !ext.warnings.isEmpty()) o.put("warnings", ext.warnings);
        return o.toString(2);
    }

    private static List<String> readStringList(JSONArray arr, int max) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length() && out.size() < max; i++) {
            String m = arr.optString(i, "").trim();
            if (!m.isEmpty()) out.add(m);
        }
        return out;
    }
}

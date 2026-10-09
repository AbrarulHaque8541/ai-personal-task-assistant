package com.cue.daymark;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Daymark JSON packs and a minimal userscript header subset (@grant none only). */
final class ExtensionPackageParser {
    private static final int MAX_PACK_CHARS = 200_000;
    private static final int MAX_ARCHIVE_ENTRY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ARCHIVE_DECOMPRESSED_BYTES = 5 * 1024 * 1024;
    private static final Pattern USERSCRIPT_HEADER = Pattern.compile(
            "(?s)==UserScript==\\s*(.*?)==/UserScript==");
    // Keys may contain hyphens (@run-at); w alone never matched them, so
    // @run-at directives were silently ignored before this character class.
    private static final Pattern META = Pattern.compile(
            "@([\\w-]+)\\s+(.+)");

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
        requireValidMatchScopes(matches, "Daymark extension");
        List<String> excludes = readStringList(o.optJSONArray("excludes"), 32);
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
        return new BrowserExtension(
                id, name, o.optString("version", "1.0.0"), o.optString("description", ""),
                o.optBoolean("enabled", true), builtIn, matches, excludes, css, js,
                o.optString("runAt", "document_end"), o.optString("warnings", ""), disabledSites);
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
            int totalDecompressed = 0;
            int totalText = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > 128) {
                    throw new IllegalArgumentException("Extension archive contains too many entries.");
                }
                String originalPath = entry.getName();
                String path = originalPath == null ? "" : originalPath.replace('\\\\', '/');
                boolean safePath = !entry.isDirectory() && !path.startsWith("/")
                        && !path.contains("../") && originalPath != null && originalPath.indexOf('\\\\') < 0;
                boolean importText = safePath && (path.equals("manifest.json")
                        || path.endsWith(".js") || path.endsWith(".css"));
                ByteArrayOutputStream out = importText ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[8192];
                int read;
                int entryDecompressed = 0;
                while ((read = zip.read(buffer)) != -1) {
                    entryDecompressed += read;
                    totalDecompressed += read;
                    if (entryDecompressed > MAX_ARCHIVE_ENTRY_BYTES) {
                        throw new IllegalArgumentException("Extension archive entry exceeds the 2 MB decompressed limit.");
                    }
                    if (totalDecompressed > MAX_ARCHIVE_DECOMPRESSED_BYTES) {
                        throw new IllegalArgumentException("Extension archive exceeds the 5 MB total decompressed limit.");
                    }
                    if (importText) {
                        totalText += read;
                        if (totalText > MAX_PACK_CHARS) {
                            throw new IllegalArgumentException("Extension code is too large (max 200 KB text).");
                        }
                        out.write(buffer, 0, read);
                    }
                }
                if (importText) files.put(path, out.toString("UTF-8"));
                zip.closeEntry();
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
        String runAt = "document_end";
        StringBuilder warnings = new StringBuilder("Imported in Daymark compatibility mode; only page-local content scripts are supported.");
        for (int i = 0; i < scripts.length() && i < 32; i++) {
            JSONObject item = scripts.optJSONObject(i);
            if (item == null) continue;
            appendPatterns(matches, item.optJSONArray("matches"));
            appendPatterns(excludes, item.optJSONArray("exclude_matches"));
            String itemRunAt = item.optString("run_at", "document_idle").toLowerCase(Locale.ROOT);
            if (itemRunAt.contains("start")) runAt = "document_start";
            else if ("document_end".equals(itemRunAt)) runAt = "document_end";
            appendFiles(css, item.optJSONArray("css"), files, "CSS", warnings);
            appendFiles(js, item.optJSONArray("js"), files, "JS", warnings);
        }
        requireValidMatchScopes(matches, "WebExtension manifest");
        if (manifest.has("background")) warnings.append(" Background/service worker was not imported.");
        if (manifest.has("action") || manifest.has("browser_action") || manifest.has("page_action")) warnings.append(" Toolbar actions/popups were not imported.");
        if (manifest.has("permissions") || manifest.has("host_permissions")) warnings.append(" Requested permissions were not granted.");
        String id = "webext." + Integer.toHexString(manifest.toString().hashCode());
        return new BrowserExtension(id, name, version, manifest.optString("description", ""), true, false,
                matches, excludes, css.toString(), js.toString(), runAt, warnings.toString());
    }

    /** Reject absent or unsupported scopes instead of silently widening an import to every HTTPS site. */
    private static void requireValidMatchScopes(List<String> matches, String source) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException(source + " requires at least one supported match scope; no all-sites default is applied.");
        }
        for (String pattern : matches) {
            if (!isSupportedMatchPattern(pattern)) {
                throw new IllegalArgumentException(source + " contains an unsupported match scope: " + pattern);
            }
        }
    }

    private static boolean isSupportedMatchPattern(String pattern) {
        if (pattern == null) return false;
        String value = pattern.trim();
        if ("<all_urls>".equals(value) || "*://*/*".equals(value)) return true;
        String rest;
        if (value.startsWith("https://")) rest = value.substring("https://".length());
        else if (value.startsWith("*://")) rest = value.substring("*://".length());
        else return false;

        int slash = rest.indexOf('/');
        if (slash <= 0) return false;
        String host = rest.substring(0, slash);
        String path = rest.substring(slash);
        if (host.isEmpty() || path.isEmpty() || path.indexOf('?') >= 0 || path.indexOf('#') >= 0
                || path.indexOf('\\') >= 0 || path.chars().anyMatch(Character::isWhitespace)) return false;
        if (host.contains("@") || host.contains(":") || host.contains("..")) return false;
        String hostBody = host.startsWith("*.") ? host.substring(2) : host;
        if (!"*".equals(host) && !host.matches("[A-Za-z0-9.-]+")
                && !host.matches("\\*\\.[A-Za-z0-9.-]+")) return false;
        if (hostBody.startsWith(".") || hostBody.endsWith(".") || hostBody.isEmpty()) return false;
        if (path.indexOf('*') >= 0 && !(path.endsWith("*") && path.indexOf('*') == path.length() - 1)) return false;
        return true;
    }

    private static void appendPatterns(List<String> target, JSONArray values) {
        if (values == null) return;
        for (int i = 0; i < values.length() && target.size() < 32; i++) {
            String value = values.optString(i, "").trim();
            if (!value.isEmpty()) target.add(value);
        }
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
        String runAt = "document_end";
        List<String> matches = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        List<String> grants = new ArrayList<>();
        String id = "userscript." + Integer.toHexString(raw.hashCode());
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
                        if (value.contains("start")) runAt = "document_start";
                        else runAt = "document_end";
                        break;
                    case "grant":
                        grants.add(value);
                        break;
                    default: break;
                }
            }
        }
        requireValidMatchScopes(matches, "Userscript");

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

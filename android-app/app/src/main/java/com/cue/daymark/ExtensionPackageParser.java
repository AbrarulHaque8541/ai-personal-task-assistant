package com.cue.daymark;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Parses Daymark JSON packs and a minimal userscript header subset (@grant none only). */
final class ExtensionPackageParser {
    private static final int MAX_PACK_CHARS = 200_000;
    private static final Pattern USERSCRIPT_HEADER = Pattern.compile(
            "(?s)==UserScript==\\s*(.*?)==/UserScript==");
    private static final Pattern META = Pattern.compile(
            "@(\\w+)\\s+(.+)");

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
        if (matches.isEmpty()) matches.add("*://*/*");
        String css = o.optString("css", "");
        String js = o.optString("js", "");
        if (css.length() + js.length() > MAX_PACK_CHARS) {
            throw new IllegalArgumentException("Extension script/css too large.");
        }
        return new BrowserExtension(
                id, name, o.optString("version", "1.0.0"), o.optString("description", ""),
                o.optBoolean("enabled", true), builtIn, matches, excludes, css, js,
                o.optString("runAt", "document_end"), o.optString("warnings", ""));
    }

    /**
     * Userscript import: @name @match @exclude @run-at @grant none only.
     * Unsupported grants become warnings; body still imported for DOM-only use at user risk.
     */
    /**
     * Compatibility importer for unpacked Chrome/Firefox-style WebExtension manifests.
     * Only content_scripts (CSS/JS) are converted into a Daymark page-local pack.
     * Background/service-worker, action, popup, native messaging, webRequest and other
     * privileged APIs are intentionally not executed; they are surfaced as warnings.
     */
    static BrowserExtension parseWebExtensionManifest(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("Empty extension manifest.");
        if (raw.length() > MAX_PACK_CHARS) throw new IllegalArgumentException("Extension manifest is too large.");
        JSONObject manifest = new JSONObject(raw);
        String name = manifest.optString("name", "").trim();
        String version = manifest.optString("version", "1.0.0").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Extension manifest requires a name.");
        JSONArray scripts = manifest.optJSONArray("content_scripts");
        if (scripts == null || scripts.length() == 0) {
            throw new IllegalArgumentException("No content_scripts found. This extension needs privileged browser APIs or UI that Daymark cannot execute.");
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
            JSONArray itemMatches = item.optJSONArray("matches");
            if (itemMatches != null) {
                for (int j = 0; j < itemMatches.length() && matches.size() < 32; j++) {
                    String value = itemMatches.optString(j, "").trim();
                    if (!value.isEmpty()) matches.add(value);
                }
            }
            JSONArray itemExcludes = item.optJSONArray("exclude_matches");
            if (itemExcludes != null) {
                for (int j = 0; j < itemExcludes.length() && excludes.size() < 32; j++) {
                    String value = itemExcludes.optString(j, "").trim();
                    if (!value.isEmpty()) excludes.add(value);
                }
            }
            String itemRunAt = item.optString("run_at", "document_idle").toLowerCase(Locale.ROOT);
            if (itemRunAt.contains("start")) runAt = "document_start";
            else if ("document_end".equals(itemRunAt)) runAt = "document_end";
            else if ("document_idle".equals(itemRunAt) && "document_end".equals(runAt)) runAt = "document_end";
            appendPackFiles(css, js, item.optJSONArray("css"), item.optJSONArray("js"), warnings);
        }
        if (matches.isEmpty()) matches.add("*://*/*");
        String id = "webext." + Integer.toHexString(raw.hashCode());
        if (manifest.has("background")) warnings.append(" Background/service worker was not imported.");
        if (manifest.has("action") || manifest.has("browser_action") || manifest.has("page_action")) warnings.append(" Extension toolbar actions/popups were not imported.");
        if (manifest.has("permissions") || manifest.has("host_permissions")) warnings.append(" Extension permissions were not granted; Daymark uses only page-local injection.");
        return new BrowserExtension(id, name, version, manifest.optString("description", ""), true, false,
                matches, excludes, css.toString(), js.toString(), runAt, warnings.toString());
    }

    private static void appendPackFiles(StringBuilder css, StringBuilder js, JSONArray cssFiles,
                                        JSONArray jsFiles, StringBuilder warnings) {
        if (cssFiles != null) {
            for (int i = 0; i < cssFiles.length(); i++) {
                String path = cssFiles.optString(i, "").trim();
                if (!path.isEmpty()) warnings.append(" CSS file ").append(path).append(" must be bundled as raw CSS to import; file paths are not fetched.");
            }
        }
        if (jsFiles != null) {
            for (int i = 0; i < jsFiles.length(); i++) {
                String path = jsFiles.optString(i, "").trim();
                if (!path.isEmpty()) warnings.append(" JS file ").append(path).append(" must be bundled as raw JS to import; file paths are not fetched.");
            }
        }
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
        if (matches.isEmpty()) matches.add("*://*/*");

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

package com.cue.daymark;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Daymark JSON packs and a minimal userscript header subset. */
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
        List<String> matches = new ArrayList<>();
        JSONArray arr = o.optJSONArray("matches");
        if (arr != null) {
            for (int i = 0; i < arr.length() && i < 32; i++) {
                String m = arr.optString(i, "").trim();
                if (!m.isEmpty()) matches.add(m);
            }
        }
        if (matches.isEmpty()) matches.add("*://*/*");
        String css = o.optString("css", "");
        String js = o.optString("js", "");
        if (css.length() + js.length() > MAX_PACK_CHARS) {
            throw new IllegalArgumentException("Extension script/css too large.");
        }
        return new BrowserExtension(
                id,
                name,
                o.optString("version", "1.0.0"),
                o.optString("description", ""),
                o.optBoolean("enabled", true),
                builtIn,
                matches,
                css,
                js,
                o.optString("runAt", "document_end"));
    }

    /**
     * Minimal userscript import: reads @name @match @version @description and body after header.
     * Does not implement GM_* APIs.
     */
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
        String description = "Imported userscript (no GM APIs)";
        List<String> matches = new ArrayList<>();
        String id = "userscript." + Integer.toHexString(raw.hashCode());
        if (header.find()) {
            String block = header.group(1);
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
                    default: break;
                }
            }
        }
        if (matches.isEmpty()) matches.add("*://*/*");
        String body = header.find() ? raw.substring(header.end()).trim() : raw.trim();
        // reset matcher — header already consumed; body extraction redo:
        Matcher header2 = USERSCRIPT_HEADER.matcher(raw);
        if (header2.find()) {
            body = raw.substring(header2.end()).trim();
        }
        return new BrowserExtension(id, name, version, description, true, false, matches, "", body, "document_end");
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
        o.put("css", ext.css);
        o.put("js", ext.js);
        o.put("runAt", ext.runAt);
        return o.toString(2);
    }
}

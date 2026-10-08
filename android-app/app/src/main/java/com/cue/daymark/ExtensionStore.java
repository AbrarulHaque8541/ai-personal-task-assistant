package com.cue.daymark;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local-only extension registry (built-ins + user packs in app private storage). */
final class ExtensionStore {
    private static final String PREFS = "daymark.extensions.v1";
    private static final String KEY_DISABLED_BUILTINS = "disabled_builtin_ids";
    private static final String USER_DIR = "browser_extensions";

    private final SharedPreferences preferences;
    private final File userDir;

    ExtensionStore(Context context) {
        Context app = context.getApplicationContext();
        this.preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.userDir = new File(app.getFilesDir(), USER_DIR);
        if (!userDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            userDir.mkdirs();
        }
    }

    List<BrowserExtension> listAll() {
        Map<String, BrowserExtension> byId = new LinkedHashMap<>();
        for (BrowserExtension builtIn : BuiltInExtensions.all()) {
            boolean enabled = !isBuiltinDisabled(builtIn.id);
            byId.put(builtIn.id, builtIn.withEnabled(enabled));
        }
        File[] files = userDir.listFiles((dir, name) -> name != null && name.endsWith(".daymark-ext.json"));
        if (files != null) {
            for (File file : files) {
                try {
                    String raw = readFile(file);
                    BrowserExtension ext = ExtensionPackageParser.parseDaymarkJson(raw, false);
                    byId.put(ext.id, ext);
                } catch (Exception ignored) {
                    // skip corrupt pack
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    List<BrowserExtension> enabledMatching(String pageUrl) {
        List<BrowserExtension> out = new ArrayList<>();
        for (BrowserExtension ext : listAll()) {
            if (ext.enabled && ext.matchesUrl(pageUrl)) out.add(ext);
        }
        return out;
    }

    void setBuiltinEnabled(String id, boolean enabled) {
        if (id == null || !id.startsWith("daymark.builtin.")) return;
        String csv = preferences.getString(KEY_DISABLED_BUILTINS, "");
        List<String> disabled = new ArrayList<>();
        if (csv != null && !csv.isEmpty()) {
            for (String part : csv.split(",")) {
                if (!part.isEmpty() && !part.equals(id)) disabled.add(part);
            }
        }
        if (!enabled) disabled.add(id);
        preferences.edit().putString(KEY_DISABLED_BUILTINS, join(disabled)).apply();
    }

    void installUserPack(BrowserExtension ext) throws Exception {
        if (ext == null || ext.builtIn) throw new IllegalArgumentException("Invalid pack.");
        if (ext.id.startsWith("daymark.builtin.")) {
            throw new IllegalArgumentException("Cannot overwrite built-in id.");
        }
        String json = ExtensionPackageParser.toDaymarkJson(ext);
        File out = new File(userDir, sanitizeFileName(ext.id) + ".daymark-ext.json");
        writeFile(out, json);
    }

    void uninstallUserPack(String id) {
        if (id == null || id.startsWith("daymark.builtin.")) return;
        File out = new File(userDir, sanitizeFileName(id) + ".daymark-ext.json");
        //noinspection ResultOfMethodCallIgnored
        out.delete();
    }

    private boolean isBuiltinDisabled(String id) {
        String csv = preferences.getString(KEY_DISABLED_BUILTINS, "");
        if (csv == null || csv.isEmpty()) return false;
        for (String part : csv.split(",")) {
            if (id.equals(part)) return true;
        }
        return false;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String sanitizeFileName(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String readFile(File file) throws Exception {
        byte[] buf = new byte[(int) Math.min(file.length(), 200_000)];
        try (FileInputStream in = new FileInputStream(file)) {
            int n = in.read(buf);
            if (n <= 0) return "";
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        }
    }

    private static void writeFile(File file, String content) throws Exception {
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            if (!tmp.renameTo(file)) throw new Exception("Could not save extension pack.");
        }
    }
}

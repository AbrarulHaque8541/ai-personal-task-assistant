package com.cue.daymark;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local-only extension registry (built-ins + user packs in app private storage). */
final class ExtensionStore {
    private static final String PREFS = "daymark.extensions.v1";
    private static final String KEY_DISABLED_BUILTINS = "disabled_builtin_ids";
    private static final String KEY_EXTENSIONS_ENABLED = "extensions_enabled";
    private static final String KEY_DISABLED_SITES_PREFIX = "disabled_sites.";
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

    /** Global kill switch: when off, no pack is injected into any page. */
    boolean isGloballyEnabled() {
        return preferences.getBoolean(KEY_EXTENSIONS_ENABLED, true);
    }

    void setGloballyEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_EXTENSIONS_ENABLED, enabled).apply();
    }

    /** Hosts where a built-in pack is paused (per-site disable, persisted per pack id). */
    List<String> disabledSitesForBuiltin(String id) {
        String csv = preferences.getString(KEY_DISABLED_SITES_PREFIX + id, "");
        List<String> sites = new ArrayList<>();
        if (csv != null && !csv.isEmpty()) {
            for (String part : csv.split(",")) {
                if (!part.isEmpty() && !sites.contains(part)) sites.add(part);
            }
        }
        return sites;
    }

    void setSiteDisabled(String id, String host, boolean disabled) {
        if (id == null || id.trim().isEmpty() || host == null || host.trim().isEmpty()) return;
        String normalizedHost = BrowserExtension.normalizeSiteHost(host);
        if (normalizedHost == null) return;
        if (id.startsWith("daymark.builtin.")) {
            List<String> sites = new ArrayList<>(disabledSitesForBuiltin(id));
            if (disabled) {
                if (!sites.contains(normalizedHost)) sites.add(normalizedHost);
            } else {
                sites.remove(normalizedHost);
            }
            preferences.edit().putString(KEY_DISABLED_SITES_PREFIX + id, join(sites)).apply();
            return;
        }
        File file = findUserPack(id);
        if (file == null) return;
        try {
            BrowserExtension ext = ExtensionPackageParser.parseDaymarkJson(readFile(file), false);
            List<String> sites = new ArrayList<>(ext.disabledSites);
            if (disabled) {
                if (!sites.contains(normalizedHost)) sites.add(normalizedHost);
            } else {
                sites.remove(normalizedHost);
            }
            writeFile(file, ExtensionPackageParser.toDaymarkJson(ext.withDisabledSites(sites)));
        } catch (Exception ignored) {
            // Corrupt packs remain hidden from the runtime until re-imported.
        }
    }

    /** Serialized pack text for user-pack export, or null when the pack is missing. */
    String userPackJson(String id) {
        if (id == null || id.startsWith("daymark.builtin.")) return null;
        File file = findUserPack(id);
        if (file == null) return null;
        try {
            return readFile(file);
        } catch (Exception exception) {
            return null;
        }
    }

    List<BrowserExtension> listAll() {
        Map<String, BrowserExtension> byId = new LinkedHashMap<>();
        for (BrowserExtension builtIn : BuiltInExtensions.all()) {
            boolean enabled = !isBuiltinDisabled(builtIn.id);
            byId.put(builtIn.id, builtIn.withEnabled(enabled)
                    .withDisabledSites(disabledSitesForBuiltin(builtIn.id)));
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
        if (out.exists()) {
            throw new IllegalArgumentException(
                    "An extension with this ID is already installed. Uninstall it before importing a replacement.");
        }
        writeFile(out, json);
    }

    void setUserPackEnabled(String id, boolean enabled) {
        if (id == null || id.trim().isEmpty() || id.startsWith("daymark.builtin.")) return;
        File file = findUserPack(id);
        if (file == null) return;
        try {
            BrowserExtension ext = ExtensionPackageParser.parseDaymarkJson(readFile(file), false);
            BrowserExtension updated = ext.withEnabled(enabled);
            writeFile(file, ExtensionPackageParser.toDaymarkJson(updated));
        } catch (Exception ignored) {
            // Corrupt packs remain hidden from the runtime until re-imported.
        }
    }

    private File findUserPack(String id) {
        if (id == null) return null;
        File file = new File(userDir, sanitizeFileName(id) + ".daymark-ext.json");
        return file.exists() ? file : null;
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
        int max = (int) Math.min(file.length(), 200_000);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(max);
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) != -1) {
                if (total + read > max) {
                    read = max - total;
                    if (read <= 0) break;
                }
                out.write(buffer, 0, read);
                total += read;
                if (total >= max) break;
            }
        }
        byte[] bytes = out.toByteArray();
        if (bytes.length == 0) return "";
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeFile(File file, String content) throws Exception {
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            // Same-directory replacement: if the move fails, keep the previous file intact.
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }
}

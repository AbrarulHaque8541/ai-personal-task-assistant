package com.cue.daymark;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Host-side tests for the extension package parser: the app's riskiest
 * untrusted-input path (Daymark JSON packs, userscript headers, WebExtension
 * manifests, ZIP/XPI/CRX3 archives). Runs on the host org.json stubs.
 */
public final class ExtensionPackageParserSmoke {
    private static int assertions;

    private ExtensionPackageParserSmoke() { }

    public static void main(String[] args) throws Exception {
        daymarkJsonRoundTripPreservesEveryField();
        daymarkJsonValidationRejectsBadPacks();
        userscriptHeaderParsesAndGrantPolicyIsWarned();
        bareUserscriptGetsSafeDefaults();
        webExtensionManifestTranslatesContentScriptsOnly();
        webExtensionManifestWithoutContentScriptsIsRejected();
        archiveImportSkipsTraversalEntriesAndUnsupportedFiles();
        archiveWithoutManifestIsRejected();
        archiveFileCountIsCapped();
        crx3HeaderIsStrippedBeforeUnzip();
        oversizedArchivesAreRejected();
        System.out.println("PASS extension package parser smoke tests: " + assertions + " assertions");
    }

    private static void daymarkJsonRoundTripPreservesEveryField() throws Exception {
        BrowserExtension original = new BrowserExtension(
                "user.round.trip", "Round trip", "2.4", "Rounds through JSON", true, false,
                Arrays.asList("https://example.com/*"), Arrays.asList("https://example.com/private"),
                ".hidden{display:none}", "console.log('hi');", "document_start", "warn text",
                Arrays.asList("example.com"));
        String json = ExtensionPackageParser.toDaymarkJson(original);
        BrowserExtension parsed = ExtensionPackageParser.parseDaymarkJson(json, false);
        check(original.id.equals(parsed.id), "pack id survives a JSON round trip");
        check(original.name.equals(parsed.name), "pack name survives a JSON round trip");
        check(original.version.equals(parsed.version), "pack version survives a JSON round trip");
        check(original.description.equals(parsed.description), "pack description survives a JSON round trip");
        check(parsed.enabled, "enabled pack survives a JSON round trip");
        check(!parsed.builtIn, "imported packs are never marked built-in");
        check(parsed.matches.contains("https://example.com/*"), "match rules survive a JSON round trip");
        check(parsed.excludes.contains("https://example.com/private"), "exclude rules survive a JSON round trip");
        check(original.css.equals(parsed.css), "css survives a JSON round trip");
        check(original.js.equals(parsed.js), "js survives a JSON round trip");
        check("document_start".equals(parsed.runAt), "runAt survives a JSON round trip");
        check(original.warnings.equals(parsed.warnings), "warnings survive a JSON round trip");
        check(parsed.disabledSites.contains("example.com"), "disabled sites survive a JSON round trip");
    }

    private static void daymarkJsonValidationRejectsBadPacks() {
        expectRejected("{}", "packs without id and name are rejected");
        expectRejected("{\"id\":\"x\"}", "packs without a name are rejected");
        expectRejected("{\"name\":\"X\"}", "packs without an id are rejected");
        expectRejected("{\"id\":\"bad id!\",\"name\":\"X\"}", "ids with unsafe characters are rejected");
        expectRejected("[1,2,3]", "non-object JSON payloads are rejected");
        expectRejected(null, "null pack text is rejected");
        StringBuilder huge = new StringBuilder();
        huge.append("{\"id\":\"big\",\"name\":\"Big\",\"css\":\"");
        for (int i = 0; i < 210_000; i++) huge.append('a');
        huge.append("\"}");
        expectRejected(huge.toString(), "packs over the 200 KB text cap are rejected");
    }

    private static void userscriptHeaderParsesAndGrantPolicyIsWarned() throws Exception {
        String script = "// ==UserScript==\n"
                + "// @name        Demo script\n"
                + "// @version     2.1\n"
                + "// @description A demo\n"
                + "// @match       https://example.com/*\n"
                + "// @exclude     https://example.com/private*\n"
                + "// @run-at      document-start\n"
                + "// @grant       GM_addStyle\n"
                + "// @require     https://cdn.example/lib.js\n"
                + "// ==/UserScript==\n"
                + "console.log('body');";
        BrowserExtension ext = ExtensionPackageParser.parseUserScript(script);
        check("Demo script".equals(ext.name), "userscript @name is imported");
        check("2.1".equals(ext.version), "userscript @version is imported");
        check("A demo".equals(ext.description), "userscript @description is imported");
        check(ext.matches.contains("https://example.com/*"), "userscript @match is imported");
        check(ext.excludes.contains("https://example.com/private*"), "userscript @exclude is imported");
        check("document_start".equals(ext.runAt), "userscript @run-at document-start is imported");
        check("console.log('body');".equals(ext.js), "userscript body excludes the header block");
        check(ext.warnings.contains("@require/@resource not fetched"), "remote @require is refused with a warning");
        check(!ext.warnings.contains("unsupported @grant"), "GM_addStyle is allowed without an unsupported-grant warning");

        String unsafe = "// ==UserScript==\n// @name Unsafe\n// @grant GM_setValue\n// ==/UserScript==\nbody();";
        BrowserExtension warned = ExtensionPackageParser.parseUserScript(unsafe);
        check(warned.warnings.contains("unsupported @grant GM_setValue"),
                "privileged GM_* grants become warnings, not APIs");
        check(warned.description.contains("Daymark:"),
                "the description tells the user only DOM-level APIs are provided");
    }

    private static void bareUserscriptGetsSafeDefaults() throws Exception {
        BrowserExtension bare = ExtensionPackageParser.parseUserScript("console.log(1);");
        check("Imported script".equals(bare.name), "headerless userscripts get a default name");
        check(bare.matches.contains("*://*/*"), "headerless userscripts default to all HTTPS pages");
        check("console.log(1);".equals(bare.js), "headerless userscripts keep their whole body");
    }

    private static void webExtensionManifestTranslatesContentScriptsOnly() throws Exception {
        String manifest = "{\"name\":\"Cosmetic only\",\"version\":\"1.2\",\"description\":\"d\","
                + "\"background\":{\"service_worker\":\"bg.js\"},"
                + "\"permissions\":[\"tabs\"],"
                + "\"action\":{\"default_popup\":\"popup.html\"},"
                + "\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],"
                + "\"run_at\":\"document_start\",\"css\":[\"style.css\"],\"js\":[\"script.js\"]}]}";
        BrowserExtension ext = ExtensionPackageParser.parseWebExtensionManifest(manifest);
        check("Cosmetic only".equals(ext.name), "manifest name is imported");
        check("1.2".equals(ext.version), "manifest version is imported");
        check(ext.matches.contains("https://example.com/*"), "content script match rules are imported");
        check("document_start".equals(ext.runAt), "content script run_at document_start is imported");
        check(ext.id.startsWith("webext."), "manifest imports get a webext id prefix");
        check(ext.warnings.contains("Background/service worker was not imported"),
                "background workers are refused with a warning");
        check(ext.warnings.contains("Toolbar actions/popups were not imported"),
                "toolbar popups are refused with a warning");
        check(ext.warnings.contains("Requested permissions were not granted"),
                "manifest permissions are refused with a warning");
        check(ext.warnings.contains("CSS file style.css was not found in the archive"),
                "manifest-only imports warn that referenced files were not supplied");
        check(ext.warnings.contains("JS file script.js was not found in the archive"),
                "manifest-only imports warn about missing JS files too");
        check(ext.css.isEmpty() && ext.js.isEmpty(),
                "manifest-only imports never invent page code");
    }

    private static void webExtensionManifestWithoutContentScriptsIsRejected() {
        expectRejected("{\"name\":\"Background only\",\"background\":{\"service_worker\":\"bg.js\"}}",
                "extensions without content_scripts are rejected");
        expectRejected("{\"name\":\"Empty scripts\",\"content_scripts\":[]}",
                "extensions with an empty content_scripts list are rejected");
    }

    private static void archiveImportSkipsTraversalEntriesAndUnsupportedFiles() throws Exception {
        String manifest = "{\"name\":\"Zipped\",\"version\":\"3.0\","
                + "\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],"
                + "\"js\":[\"script.js\",\"../evil.js\"],\"css\":[\"style.css\"]}]}";
        BrowserExtension ext = ExtensionPackageParser.parseWebExtensionArchive(
                zipArchive(manifest, true));
        check("Zipped".equals(ext.name), "archive manifests are imported by name");
        check(ext.js.contains("console.log('packaged');"), "packaged JS is imported from the archive");
        check(!ext.js.contains("traversal"), "path-traversal entries are never imported");
        check(ext.css.contains(".ad{display:none}"), "packaged CSS is imported from the archive");
        check(ext.warnings.contains("JS file ../evil.js was not found in the archive"),
                "a traversal-referenced file is reported as absent");
        check(ext.warnings.contains("compatibility mode"),
                "archive imports always carry the compatibility-mode warning");
    }

    private static void archiveWithoutManifestIsRejected() throws Exception {
        byte[] archive = zipArchive(null, false);
        try {
            ExtensionPackageParser.parseWebExtensionArchive(archive);
            throw new AssertionError("archives without manifest.json are rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("manifest.json"),
                    "the manifest.json rejection explains the reason");
        }
    }

    private static void archiveFileCountIsCapped() throws Exception {
        String manifest = "{\"name\":\"Too many\",\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],\"js\":[\"a.js\"]}]}";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            putEntry(zip, "manifest.json", manifest);
            for (int i = 0; i < 140; i++) {
                putEntry(zip, "f" + i + ".js", "console.log(" + i + ");");
            }
        }
        try {
            ExtensionPackageParser.parseWebExtensionArchive(bytes.toByteArray());
            throw new AssertionError("archives with over 128 files are rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("too many files"),
                    "the file-count cap rejection explains the reason");
        }
    }

    private static void crx3HeaderIsStrippedBeforeUnzip() throws Exception {
        String manifest = "{\"name\":\"From CRX3\",\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],\"js\":[\"script.js\"]}]}";
        byte[] zipBytes = zipArchive(manifest, false);
        ByteArrayOutputStream crx = new ByteArrayOutputStream();
        crx.write(new byte[] { 'C', 'r', '2', '4' });
        crx.write(new byte[] { 3, 0, 0, 0 });
        crx.write(new byte[] { 0, 0, 0, 0 });
        crx.write(zipBytes);
        BrowserExtension ext = ExtensionPackageParser.parseWebExtensionArchive(crx.toByteArray());
        check("From CRX3".equals(ext.name), "the CRX3 container header is stripped before unzip");
        check(ext.js.contains("console.log('packaged');"), "CRX3-packaged JS is imported");
    }

    private static void oversizedArchivesAreRejected() {
        try {
            ExtensionPackageParser.parseWebExtensionArchive(new byte[5 * 1024 * 1024 + 1]);
            throw new AssertionError("archives over 5 MB are rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("5 MB"), "the archive size cap is explained");
        }
        try {
            ExtensionPackageParser.parseWebExtensionArchive(new byte[0]);
            throw new AssertionError("empty archives are rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("empty"), "the empty-archive rejection is explained");
        }
    }

    private static byte[] zipArchive(String manifestJson, boolean includeTraversalEntry) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (manifestJson != null) putEntry(zip, "manifest.json", manifestJson);
            putEntry(zip, "script.js", "console.log('packaged');");
            putEntry(zip, "style.css", ".ad{display:none}");
            putEntry(zip, "notes.txt", "not imported");
            if (includeTraversalEntry) putEntry(zip, "../evil.js", "alert('traversal');");
        }
        return bytes.toByteArray();
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void expectRejected(String raw, String message) {
        assertions++;
        try {
            ExtensionPackageParser.parseDaymarkJson(raw, false);
            throw new AssertionError(message + ": expected rejection");
        } catch (IllegalArgumentException expected) {
            // rejected as expected
        } catch (Exception other) {
            throw new AssertionError(message + ": unexpected rejection type " + other, other);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}

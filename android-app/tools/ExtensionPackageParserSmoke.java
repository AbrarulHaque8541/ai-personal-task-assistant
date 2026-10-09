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
 * Regression coverage for issues #190, #192, #193, #194.
 * Imported ids are content SHA-256 digests, never 32-bit string hashes.
 */
public final class ExtensionPackageParserSmoke {
    private static int assertions;

    private ExtensionPackageParserSmoke() { }

    public static void main(String[] args) throws Exception {
        daymarkJsonRoundTripPreservesEveryField();
        daymarkJsonValidationRejectsBadPacks();
        userscriptHeaderParsesAndGrantPolicyIsWarned();
        userscriptWithoutMatchIsRejected();
        webExtensionManifestTranslatesContentScriptsOnly();
        webExtensionManifestWithoutContentScriptsIsRejected();
        webExtensionMatchScopesFailClosed();
        importedIdsAreStableAndCollisionResistant();
        documentStartImportsNormalizeToHonestTiming();
        archiveImportSkipsTraversalEntriesAndUnsupportedFiles();
        archiveWithoutManifestIsRejected();
        archiveFileCountIsCapped();
        archiveDecompressedBudgetBoundsIgnoredEntries();
        crx3HeaderIsStrippedBeforeUnzip();
        oversizedArchivesAreRejected();
        System.out.println("PASS extension package parser smoke tests: " + assertions + " assertions");
    }

    private static void daymarkJsonRoundTripPreservesEveryField() throws Exception {
        BrowserExtension original = new BrowserExtension(
                "user.round.trip", "Round trip", "2.4", "Rounds through JSON", true, false,
                Arrays.asList("https://example.com/*"), Arrays.asList("https://example.com/private"),
                ".hidden{display:none}", "console.log('hi');", "document_end", "warn text",
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
        check("document_end".equals(parsed.runAt), "the effective runAt survives a JSON round trip");
        check(original.warnings.equals(parsed.warnings), "warnings survive a JSON round trip");
        check(parsed.disabledSites.contains("example.com"), "disabled sites survive a JSON round trip");
    }

    private static void daymarkJsonValidationRejectsBadPacks() {
        expectRejected("{}", "packs without id and name are rejected");
        expectRejected("{\"id\":\"x\"}", "packs without a name are rejected");
        expectRejected("{\"name\":\"X\"}", "packs without an id are rejected");
        expectRejected("{\"id\":\"bad id!\",\"name\":\"X\"}", "ids with unsafe characters are rejected");
        expectRejected("{\"id\":\"no-scope\",\"name\":\"No scope\"}", "Daymark packs without match scopes are rejected");
        expectRejected("{\"id\":\"bad-scope\",\"name\":\"Bad scope\",\"matches\":[\"http://example.com/*\"]}", "Daymark packs with unsupported scopes are rejected");
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
        check("document_end".equals(ext.runAt),
                "userscript document-start normalizes to the effective document_end timing");
        check(ext.warnings.contains("document_start run timing is not supported"),
                "the timing limitation is disclosed as an import warning");
        check("console.log('body');".equals(ext.js), "userscript body excludes the header block");
        check(ext.warnings.contains("@require/@resource not fetched"), "remote @require is refused with a warning");
        check(!ext.warnings.contains("unsupported @grant"), "GM_addStyle is allowed without an unsupported-grant warning");

        String unsafe = "// ==UserScript==\n// @name Unsafe\n// @match https://example.com/*\n// @grant GM_setValue\n// ==/UserScript==\nbody();";
        BrowserExtension warned = ExtensionPackageParser.parseUserScript(unsafe);
        check(warned.warnings.contains("unsupported @grant GM_setValue"),
                "privileged GM_* grants become warnings, not APIs");
        check(warned.description.contains("Daymark:"),
                "the description tells the user only DOM-level APIs are provided");
    }

    private static void userscriptWithoutMatchIsRejected() {
        expectUserscriptRejected("console.log(1);", "headerless userscripts without an explicit @match are rejected");
        expectUserscriptRejected("// ==UserScript==\n// @name No scope\n// ==/UserScript==\nrun();",
                "userscripts without @match are rejected instead of running on every site");
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
        check(!ext.matches.contains("*://*/*"), "valid scoped imports are never broadened to all sites");
        check("document_end".equals(ext.runAt),
                "manifest document_start normalizes to the effective document_end timing");
        check(ext.warnings.contains("document_start run timing is not supported"),
                "manifest imports disclose the timing limitation");
        check(ext.id.matches("webext\\.[0-9a-f]{64}"),
                "manifest ids are SHA-256 digests, not 32-bit string hashes");
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
        expectWebExtensionRejected("{\"name\":\"Background only\",\"background\":{\"service_worker\":\"bg.js\"}}",
                "extensions without content_scripts are rejected");
        expectWebExtensionRejected("{\"name\":\"Empty scripts\",\"content_scripts\":[]}",
                "extensions with an empty content_scripts list are rejected");
    }

    private static void webExtensionMatchScopesFailClosed() throws Exception {
        expectWebExtensionRejected("{\"name\":\"No matches\",\"content_scripts\":[{\"js\":[\"a.js\"]}]}",
                "content scripts without a matches array fail closed");
        expectWebExtensionRejected("{\"name\":\"Empty matches\",\"content_scripts\":[{\"matches\":[],\"js\":[\"a.js\"]}]}",
                "content scripts with an empty matches array fail closed");
        expectWebExtensionRejected(
                "{\"name\":\"Cleartext\",\"content_scripts\":[{\"matches\":[\"http://example.com/*\"],\"js\":[\"a.js\"]}]}",
                "unsupported schemes fail closed");
        expectWebExtensionRejected(
                "{\"name\":\"Malformed host\",\"content_scripts\":[{\"matches\":[\"https://**/*\"],\"js\":[\"a.js\"]}]}",
                "malformed host patterns fail closed");
        expectWebExtensionRejected(
                "{\"name\":\"No path\",\"content_scripts\":[{\"matches\":[\"https://example.com\"],\"js\":[\"a.js\"]}]}",
                "patterns without a declared path fail closed");
        expectWebExtensionRejected(
                "{\"name\":\"Garbage\",\"content_scripts\":[{\"matches\":[\"example.com\"],\"js\":[\"a.js\"]}]}",
                "bare hostnames are not match patterns");
        expectWebExtensionRejected(
                "{\"name\":\"Bad exclude\",\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],"
                        + "\"exclude_matches\":[\"https://example.com\"],\"js\":[\"a.js\"]}]}",
                "invalid exclude_matches also fail closed");
        try {
            BrowserExtension restricted = ExtensionPackageParser.parseWebExtensionManifest(
                    "{\"name\":\"Scoped\",\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],"
                            + "\"exclude_matches\":[\"https://example.com/private*\"],\"js\":[\"a.js\"]}]}");
            check(restricted.matches.contains("https://example.com/*"), "valid scopes are imported");
            check(!restricted.matches.contains("*://*/*"), "imports never silently broaden to all sites");
            check(restricted.excludes.contains("https://example.com/private*"), "explicit exclusions still win");
            BrowserExtension rootOnly = ExtensionPackageParser.parseWebExtensionManifest(
                    "{\"name\":\"Root path\",\"content_scripts\":[{\"matches\":[\"https://example.com/\"],\"js\":[\"a.js\"]}]}");
            check(rootOnly.matches.contains("https://example.com/"), "a root-only path is a valid explicit scope");
        } catch (IllegalArgumentException unexpected) {
            throw new AssertionError("valid restricted scopes must import: " + unexpected, unexpected);
        }
    }

    private static void importedIdsAreStableAndCollisionResistant() throws Exception {
        // "Aa" and "BB" collide under Java's 32-bit String.hashCode(); digests must not.
        check("Aa".hashCode() == "BB".hashCode(),
                "the fixture strings really do collide under String.hashCode()");
        String scriptA = "// ==UserScript==\n// @name Aa\n// @match https://example.com/*\n// ==/UserScript==\nrun();";
        String scriptB = scriptA.replace("@name Aa", "@name BB");
        check(scriptA.hashCode() == scriptB.hashCode(), "userscript fixtures share a Java String hash");
        BrowserExtension first = ExtensionPackageParser.parseUserScript(scriptA);
        BrowserExtension second = ExtensionPackageParser.parseUserScript(scriptB);
        check(!first.id.equals(second.id),
                "userscript ids survive hashCode collisions without colliding");
        check(first.id.matches("userscript\\.[0-9a-f]{64}")
                        && second.id.matches("userscript\\.[0-9a-f]{64}"),
                "userscript ids are SHA-256 digests");
        check(first.id.equals(ExtensionPackageParser.parseUserScript(scriptA).id),
                "re-importing the same userscript keeps a stable id");

        String manifest = "{\"name\":\"Aa\",\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],\"js\":[\"a.js\"]}]}";
        String other = manifest.replace("\"Aa\"", "\"BB\"");
        String firstId = ExtensionPackageParser.parseWebExtensionManifest(manifest).id;
        String secondId = ExtensionPackageParser.parseWebExtensionManifest(other).id;
        check(!firstId.equals(secondId), "manifest ids differ for different manifests");
        check(firstId.equals(ExtensionPackageParser.parseWebExtensionManifest(manifest).id),
                "re-importing the same manifest keeps a stable id");
        check(firstId.matches("webext\\.[0-9a-f]{64}"), "manifest ids are SHA-256 digests");
        
        // Identical manifest metadata can point to different code. Those imports must not
        // collide merely because the manifest JSON is identical.
        String codeManifest = "{\"name\":\"Same manifest\",\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],\"js\":[\"script.js\"]}]}";
        BrowserExtension codeA = ExtensionPackageParser.parseWebExtensionArchive(
                zipArchive(codeManifest, false, "runA();"));
        BrowserExtension codeB = ExtensionPackageParser.parseWebExtensionArchive(
                zipArchive(codeManifest, false, "runB();"));
        check(!codeA.id.equals(codeB.id),
                "different effective JS content under the same manifest gets a different id");
        check(codeA.id.equals(ExtensionPackageParser.parseWebExtensionArchive(
                        zipArchive(codeManifest, false, "runA();")).id),
                "same manifest and code retain a stable imported id");
    }

    private static void documentStartImportsNormalizeToHonestTiming() throws Exception {
        BrowserExtension webext = ExtensionPackageParser.parseWebExtensionManifest(
                "{\"name\":\"Early\",\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],"
                        + "\"run_at\":\"document_start\",\"js\":[\"a.js\"]}]}");
        check("document_end".equals(webext.runAt),
                "webext document_start normalizes to the effective timing");
        check(webext.warnings.contains("document_start run timing is not supported"),
                "webext imports disclose the timing limitation");

        BrowserExtension userscript = ExtensionPackageParser.parseUserScript(
                "// ==UserScript==\n// @name Early\n// @match https://example.com/*\n// @run-at document-start\n// ==/UserScript==\nbody();");
        check("document_end".equals(userscript.runAt),
                "userscript document_start normalizes to the effective timing");
        check(userscript.warnings.contains("document_start run timing is not supported"),
                "userscript imports disclose the timing limitation");

        String packJson = ExtensionPackageParser.toDaymarkJson(new BrowserExtension(
                "user.early", "Early pack", "1.0.0", "", true, false,
                Arrays.asList("https://example.com/*"), Arrays.asList(),
                "", "body();", "document_start", "", Arrays.asList()));
        BrowserExtension pack = ExtensionPackageParser.parseDaymarkJson(packJson, false);
        check("document_end".equals(pack.runAt),
                "daymark JSON document_start normalizes to the effective timing");
        check(pack.warnings.contains("document_start run timing is not supported"),
                "the stored pack discloses the timing limitation");
        BrowserExtension reParsed = ExtensionPackageParser.parseDaymarkJson(
                ExtensionPackageParser.toDaymarkJson(pack), false);
        check(reParsed.warnings.equals(pack.warnings),
                "the timing disclosure is idempotent and never duplicated");
        check("document_end".equals(reParsed.runAt), "re-parsing keeps the effective timing");
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

    private static void archiveDecompressedBudgetBoundsIgnoredEntries() throws Exception {
        String manifest = "{\"name\":\"Budget\",\"content_scripts\":[{\"matches\":[\"https://example.com/*\"],\"js\":[\"script.js\"]}]}";

        // A single ignored entry above the per-entry cap is rejected deterministically.
        ByteArrayOutputStream oversized = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(oversized)) {
            putEntry(zip, "manifest.json", manifest);
            putEntry(zip, "script.js", "console.log('ok');");
            putEntry(zip, "huge.bin", new byte[2 * 1024 * 1024 + 1024]);
        }
        try {
            ExtensionPackageParser.parseWebExtensionArchive(oversized.toByteArray());
            throw new AssertionError("entries expanding past the per-entry decompressed cap are rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("decompressed"),
                    "the per-entry decompressed rejection explains the reason");
        }

        // Splitting the payload across several ignored entries cannot bypass the aggregate cap.
        ByteArrayOutputStream split = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(split)) {
            putEntry(zip, "manifest.json", manifest);
            putEntry(zip, "script.js", "console.log('ok');");
            for (int i = 0; i < 9; i++) {
                putEntry(zip, "ignored" + i + ".bin", new byte[512 * 1024]);
            }
        }
        try {
            ExtensionPackageParser.parseWebExtensionArchive(split.toByteArray());
            throw new AssertionError("the aggregate decompressed cap cannot be bypassed by splitting ignored files");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("decompressed"),
                    "the aggregate decompressed rejection explains the reason");
        }

        // Directory entries are also drained and counted even if the importer never stores them.
        ByteArrayOutputStream directoryBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(directoryBytes)) {
            putEntry(zip, "manifest.json", manifest);
            putEntry(zip, "script.js", "console.log('ok');");
            putEntry(zip, "payload-dir/", new byte[2 * 1024 * 1024 + 1024]);
        }
        try {
            ExtensionPackageParser.parseWebExtensionArchive(directoryBytes.toByteArray());
            throw new AssertionError("oversized directory-entry expansion is rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("decompressed"),
                    "directory-entry decompression rejection explains the reason");
        }

        // A modest bounded archive still imports.
        BrowserExtension ext = ExtensionPackageParser.parseWebExtensionArchive(zipArchive(manifest, true));
        check("Budget".equals(ext.name), "bounded archives still import after the decompressed caps");
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

    private static void oversizedArchivesAreRejected() throws Exception {
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
        return zipArchive(manifestJson, includeTraversalEntry, "console.log('packaged');");
    }

    private static byte[] zipArchive(String manifestJson, boolean includeTraversalEntry,
                                     String scriptContent) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (manifestJson != null) putEntry(zip, "manifest.json", manifestJson);
            putEntry(zip, "script.js", scriptContent);
            putEntry(zip, "style.css", ".ad{display:none}");
            putEntry(zip, "notes.txt", "not imported");
            if (includeTraversalEntry) putEntry(zip, "../evil.js", "alert('traversal');");
        }
        return bytes.toByteArray();
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws Exception {
        putEntry(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
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

    private static void expectUserscriptRejected(String raw, String message) {
        assertions++;
        try {
            ExtensionPackageParser.parseUserScript(raw);
            throw new AssertionError(message + ": expected rejection");
        } catch (IllegalArgumentException expected) {
            // rejected as expected
        } catch (Exception other) {
            throw new AssertionError(message + ": unexpected rejection type " + other, other);
        }
    }

    private static void expectWebExtensionRejected(String manifest, String message) {
        assertions++;
        try {
            ExtensionPackageParser.parseWebExtensionManifest(manifest);
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

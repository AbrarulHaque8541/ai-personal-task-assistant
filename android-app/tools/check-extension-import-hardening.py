#!/usr/bin/env python3
"""Guard the extension import hardening (issues #190, #192, #193, #194).

Fails closed if any of the following regresses:
- archive imports must budget decompressed bytes across every entry, not just stored text;
- imported ids must be SHA-256 digests, not 32-bit String.hashCode values;
- installing a different pack under an existing id must fail instead of overwriting;
- WebExtension imports must fail closed on missing/invalid match scopes instead of
  defaulting to all sites;
- imports that request document_start must normalize to the effective timing with an
  honest, idempotent disclosure.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
parser = (root / "app/src/main/java/com/cue/daymark/ExtensionPackageParser.java").read_text(encoding="utf-8")
store = (root / "app/src/main/java/com/cue/daymark/ExtensionStore.java").read_text(encoding="utf-8")
smoke = (root / "tools/ExtensionPackageParserSmoke.java").read_text(encoding="utf-8")

archive = parser.split("static BrowserExtension parseWebExtensionArchive", 1)[1].split(
    "private static BrowserExtension buildWebExtension", 1)[0]
build = parser.split("private static BrowserExtension buildWebExtension", 1)[1].split(
    "private static List<String> readValidatedPatterns", 1)[0]
install = store.split("void installUserPack", 1)[1].split("private File findUserPack", 1)[0]

# issue #190: decompressed budgets across every entry, including skipped file types
assert "MAX_ARCHIVE_DECOMPRESSED_BYTES" in archive and "MAX_ENTRY_DECOMPRESSED_BYTES" in archive, \
    "archive imports must declare decompressed budgets"
assert "totalDecompressed += read" in archive, "the decompressed budget must count every entry"
assert archive.count("totalDecompressed") >= 3, "aggregate accounting must be checked while draining entries"

# issue #193: collision-resistant ids and a conflict-safe install path
assert "manifest.toString().hashCode()" not in parser, "32-bit string hashes are not collision-resistant ids"
assert "raw.hashCode()" not in parser, "userscript ids must not use the 32-bit string hash"
assert 'MessageDigest.getInstance("SHA-256")' in parser, "imported ids must derive from SHA-256"
assert "already installed with id" in install, "installing a different pack under an existing id must fail"
assert install.index("already installed with id") < install.index("writeFile(out, json)"), \
    "the install conflict check must run before the write"
writer = store.split("private static void writeFile", 1)[1]
assert "Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)" in writer, \
    "failed extension saves must preserve the previous pack"
assert "file.delete()" not in writer, "failed extension saves must not delete the previous pack"

# issue #194: fail-closed match scopes for imported content scripts
assert 'matches.add("*://*/*")' not in parser, \
    "no imported format may silently default missing scopes to all sites"
assert "validateScopePatterns(matches, \"Daymark extension matches\", true)" in parser, \
    "Daymark JSON imports must require an explicit scope"
assert "validateScopePatterns(matches, \"Userscript matches\", true)" in parser, \
    "userscript imports must require an explicit scope"
assert "no valid match rules" in build, "content scripts without valid match rules must fail closed"
assert "isSupportedMatchPattern" in parser, "match patterns must be validated, not trusted"

# issue #192: honest effective timing
assert "document_start run timing is not supported" in parser, \
    "imports that request document_start must disclose the effective timing"

for marker in (
    "webExtensionMatchScopesFailClosed",
    "importedIdsAreStableAndCollisionResistant",
    "documentStartImportsNormalizeToHonestTiming",
    "archiveDecompressedBudgetBoundsIgnoredEntries",
    "the timing disclosure is idempotent and never duplicated",
    "the fixture strings really do collide under String.hashCode()",
    "the aggregate decompressed cap cannot be bypassed by splitting ignored files",
):
    assert marker in smoke, f"missing import-hardening regression: {marker}"

print("PASS: extension import hardening — bounded archive budgets, SHA-256 ids, fail-closed match scopes, honest effective timing")

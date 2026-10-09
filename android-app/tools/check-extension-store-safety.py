#!/usr/bin/env python3
"""Guard imported extension identity collisions and fail-safe pack replacement."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
store = (root / "app/src/main/java/com/cue/daymark/ExtensionStore.java").read_text(encoding="utf-8")
install = store.split("void installUserPack", 1)[1].split("void setUserPackEnabled", 1)[0]
assert "if (out.exists())" in install, "an existing extension ID must never be silently overwritten"
assert "Uninstall it before importing a replacement" in install, "replacement rejection must explain recovery"
writer = store.split("private static void writeFile", 1)[1].split("\n    }", 1)[0]
assert "Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)" in writer, "save must use a same-directory replacement"
assert "file.delete()" not in writer, "failed save must not delete the previous extension pack"
parser = (root / "app/src/main/java/com/cue/daymark/ExtensionPackageParser.java").read_text(encoding="utf-8")
assert "MessageDigest.getInstance(\"SHA-256\")" in parser, "import IDs must use a cryptographic digest"
assert "Integer.toHexString(manifest.toString().hashCode())" not in parser, "manifest IDs must not use 32-bit String.hashCode"
assert "Integer.toHexString(raw.hashCode())" not in parser, "userscript IDs must not use 32-bit String.hashCode"
print("PASS extension identity/store guards: SHA-256 IDs, no silent overwrite, preserve prior file on failed save")

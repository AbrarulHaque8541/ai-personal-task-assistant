#!/usr/bin/env python3
"""Guard: private backup staging is swept even when startup never completes normally.

Startup restore staging can hold decrypted archive plaintext. Sweeping it only at the end of the
load pipeline means a load or reconcile failure skips the sweep and leaves that plaintext on disk
until the next successful launch, so the sweep must run from the manager's construction.
"""
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
manager = (root / "app/src/main/java/com/cue/daymark/PortableBackupManager.java").read_text(encoding="utf-8")

constructor = manager.split("PortableBackupManager(Context context, RestoreCheckpoint checkpoint) {", 1)[1]
constructor = constructor.split("\n    }", 1)[0]
assert "cleanupTransientFiles();" in constructor, \
    "the staging sweep must run from manager construction so a failed load cannot skip it"
assert "catch (IOException ignored)" in constructor, \
    "a failed construction-time sweep must stay non-fatal and be retried on a later launch"

sweep = manager.split("void cleanupTransientFiles(File preserve)", 1)[1]
sweep = sweep.split("void reconcile(", 1)[0]
assert "getCanonicalFile()" in sweep, "the preserve path must be compared canonically"
assert "deleteTree(child)" in sweep, "a non-preserved staging entry must be removed"
assert "preserved.equals(candidate)" in sweep, "a matching in-process export must be preserved"
assert "preserved = null;" in sweep, "a vanished preserve path must not block the sweep"

assert "File[] children = stagingRoot.listFiles();" in manager
assert '".dmbackup"' in manager, "the export staging suffix the sweep must understand"

print("PASS portable staging policy: swept at manager construction, non-fatal on failure, "
      "and able to preserve an in-process export")

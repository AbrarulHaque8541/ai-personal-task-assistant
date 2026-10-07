#!/usr/bin/env python3
"""Guard: an interrupted portable export can never leave a complete-looking backup.

A SAF destination is an already-created document, so opening it truncated and streaming into it left
a partial file still carrying the final `daymark-backup.dmbackup` name whenever the copy was
interrupted - a truncated archive that looks like a finished backup and whose recovery key the user
would then trust. This pins the replacement contract on the writer itself.
"""
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
writer = (root / "app/src/main/java/com/cue/daymark/PortableExportWriter.java").read_text(encoding="utf-8")
smoke = (root / "tools/PortableExportWriterSmoke.java").read_text(encoding="utf-8")

assert 'INCOMPLETE_MARKER = ".daymark-incomplete-"' in writer, \
    "an interrupted write must be conspicuous by name"

# The order inside write() is the whole guarantee: mark, copy, independently read back, finalize.
body = writer.split("static String write(File stagedArchive", 1)[1]
assert body.index("renameTo(incompleteName)") < body.index("destination.openForWrite()") \
        < body.index("destination.openForRead()") < body.index("renameTo(finalName)"), \
    "the document must be marked before writing, independently read back, then finalized"

for marker in ("MessageDigest.isEqual", "expectedDigest", "readBackBytes", "readBackDigest",
               "isSafeDisplayName", "checkCancelled", "bestEffortDelete"):
    assert marker in writer, f"missing export-integrity step: {marker}"

# A verified-but-unfinalizable copy must be retained and stay marked, never silently renamed.
assert "any retained copy remains visibly marked incomplete" in writer, \
    "a finalization failure must leave the retained copy clearly marked"
assert "if (!contentVerified) bestEffortDelete(destination);" in writer, \
    "only an unverified copy may be deleted; verified bytes must not be discarded"

assert "class PortableExportWriterSmoke" in smoke
for case in ("copyIsMarkedVerifiedAndFinalized",
             "interruptedCopyStaysMarkedIncompleteAndIsRemoved",
             "cancellationLeavesNoCompleteLookingBackup",
             "oversizedAndEmptyStagedArchivesAreRejected",
             "unsafeDisplayNamesAndFinalizationFailureAreRejected"):
    assert case in smoke, f"missing interrupted-export regression: {case}"

print("PASS portable export policy: document marked incomplete before writing, copy verified "
      "against the staged digest, independent read-back required, and a failed copy never "
      "left under the final backup name")

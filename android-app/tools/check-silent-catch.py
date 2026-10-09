#!/usr/bin/env python3
"""Source-level guard against silent catch blocks in MainActivity.

The round-2 audit found two catch blocks that swallowed their exception with no
record at all. This guard fails if either of those two sites stops recording a
diagnostic, and fails if a NEW empty catch block appears in MainActivity.

The MainActivity wiring is delivered as an applyable patch
(tools/patches/silent-catch-observability-mainactivity.patch) because the file
exceeds the commit payload cap, so on the un-patched branch this prints a
PENDING note instead of failing.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text()
POLICY = (ROOT / "app/src/main/java/com/cue/daymark/StartupDiagnostics.java").read_text()
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


check("ORPHAN_CLEANUP_FAILED" in POLICY and "EXPORT_STAGE_CLEANUP_FAILED" in POLICY,
      "both previously-silent sites must have a stable diagnostic code")
check("MAX_EVENTS" in POLICY, "the recorder must be bounded")

# An empty catch body is the pattern the audit flagged. Allow only the two known
# sites, and only once they record a diagnostic.
empty_catch = re.compile(r"catch\s*\([^)]*\)\s*\{\s*(?:/\*.*?\*/\s*)?\}")
empty_sites = empty_catch.findall(ACTIVITY)

if "StartupDiagnostics.record(" in ACTIVITY:
    check(ACTIVITY.count("StartupDiagnostics.record(") >= 2,
          "both the orphan-cleanup and export-cleanup sites must record a diagnostic")
    check(not empty_sites,
          f"no empty catch block may remain in MainActivity: {empty_sites}")
else:
    raise AssertionError(
        "silent-catch: required MainActivity wiring is missing; "
        "this check now fails instead of reporting PENDING (issue #196)")

print(f"PASS silent-catch source checks: {checks} assertions")

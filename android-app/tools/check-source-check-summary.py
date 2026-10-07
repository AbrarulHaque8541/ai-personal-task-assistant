#!/usr/bin/env python3
"""Guard the summary block of check-v1-source.sh (issue #45).

The audit found the final `print("PASS ...")` block repeated several lines
verbatim and still claimed "no permissions/network" for a manifest that declares
INTERNET for the HTTPS-only browser — a stale summary contradicting the
assertions above it. This guard fails if a duplicate summary line returns or if
the block again claims the app declares no network permission.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = (ROOT / "tools/check-v1-source.sh").read_text()
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


summaries = re.findall(r'^print\("PASS ([^"]*)"\)$', SCRIPT, re.M)
check(len(summaries) >= 8, "the summary block must still report each policy area")
check(len(summaries) == len(set(summaries)),
      "no summary line may be repeated verbatim (issue #45)")

for stale in ("no permissions/network", "no permissions, network"):
    check(not any(stale in line for line in summaries),
          f"the summary must not claim the app declares no network permission: {stale}")

check(any("INTERNET for the HTTPS-only browser" in line for line in summaries),
      "the summary must state the real INTERNET permission for the browser")

print(f"PASS source-check summary: {checks} assertions (no duplicate or stale summary lines)")

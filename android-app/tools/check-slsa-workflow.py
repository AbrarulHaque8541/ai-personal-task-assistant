#!/usr/bin/env python3
"""Source-level guard against the SLSA workflow publishing fake provenance.

The round-2 audit found .github/workflows/generator-generic-ossf-slsa3-publish.yml
was still the untouched upstream template: it fabricated `artifact1` / `artifact2`
with `echo` and generated SLSA provenance for those fake files. This guard fails
if the template markers return or if the workflow stops hashing the real APK.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github/workflows/generator-generic-ossf-slsa3-publish.yml").read_text()
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


for marker in ('echo "artifact1" > artifact1', 'echo "artifact2" > artifact2',
               "These are some amazing artifacts", "files=$(ls artifact*)"):
    check(marker not in WORKFLOW,
          f"the SLSA workflow must not fabricate placeholder artifacts: {marker}")

check("assembleGithubSideloadDebug" in WORKFLOW,
      "the SLSA workflow must build the real githubSideload APK")
check("app-githubSideload-debug.apk" in WORKFLOW,
      "the provenance subject must be the real APK path")
check("sha256sum" in WORKFLOW and "base64 -w0" in WORKFLOW,
      "the provenance subject must be the base64 sha256 of the real APK")
check("base64-subjects" in WORKFLOW,
      "the generator must receive the real subject digests")

print(f"PASS SLSA workflow source checks: {checks} assertions")

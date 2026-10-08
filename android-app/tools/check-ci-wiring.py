#!/usr/bin/env python3
"""Guard that the round-2 regression suites stay wired into CI.

The round-2 fixes each shipped a host regression suite (a `run-*-tests.sh`
wrapper plus a `check-*.py` policy guard). A suite that exists but is never
invoked by a workflow is dead weight: it passes locally and protects nothing.

This guard fails if any known round-2 suite is missing from BOTH
`.github/workflows/android.yml` and `android-app/tools/check-v1-source.sh`, and
it fails if the release workflow's signing environment variables drift away from
the names `app/build.gradle.kts` actually reads.

Run from the repository root:  python3 android-app/tools/check-ci-wiring.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID_YML = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
RELEASE_YML = (ROOT / ".github/workflows/android-release-assets.yml").read_text(encoding="utf-8")
SOURCE_CHECK = (ROOT / "android-app/tools/check-v1-source.sh").read_text(encoding="utf-8")
GRADLE = (ROOT / "android-app/app/build.gradle.kts").read_text(encoding="utf-8")

checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


# Every round-2 host suite must be reachable from CI. `check-v1-source.sh` is the
# canonical aggregator; `android.yml` also names each suite explicitly so a future
# edit to the aggregator cannot silently drop one.
ROUND2_SUITES = [
    "run-diagnostics-tests.sh",       # F8/F9 silent-catch observability
    "run-web-mode-tests.sh",          # F10 task/Web mode restore
    "run-text-scale-tests.sh",        # F7 device font scale
    "run-composer-draft-tests.sh",    # #30 composer draft
    "run-portable-staging-tests.sh",  # #28 plaintext staging cleanup
    "run-activity-request-code-tests.sh",  # #29 request-code uniqueness
    "run-portable-export-tests.sh",   # #29 interrupted export
]
for suite in ROUND2_SUITES:
    check(suite in SOURCE_CHECK, f"{suite} is not wired into check-v1-source.sh")
    check(suite in ANDROID_YML, f"{suite} is not wired into .github/workflows/android.yml")

# The web prototype tests (including the round-2 dark-mode suite) must run in CI.
check("npm test" in ANDROID_YML, "the web prototype tests are not run by android.yml")

# The release workflow must feed the signing variables the Gradle build reads.
# A mismatch here means the release build fails closed even when the secrets exist.
for var in ("RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD"):
    check(var in GRADLE, f"build.gradle.kts does not read {var}")
    check(f"{var}=" in RELEASE_YML, f"android-release-assets.yml does not set {var} for the release build")
check('KEYSTORE_PATH="' not in RELEASE_YML,
      "android-release-assets.yml still sets KEYSTORE_PATH, which build.gradle.kts no longer reads")

# The release notes must not repeat a section heading.
check(RELEASE_YML.count("'### Device testing'") <= 1,
      "android-release-assets.yml emits a duplicated '### Device testing' release-notes section")

print(f"PASS CI wiring: {checks} assertions (round-2 suites wired, signing vars aligned, notes deduped)")

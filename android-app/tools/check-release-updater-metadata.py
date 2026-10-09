#!/usr/bin/env python3
"""Check that production releases emit the metadata required by Daymark's updater."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).parent
workflow = (root.parent / ".github/workflows/android-production-release.yml").read_text(encoding="utf-8")
client = (root / "app/src/main/java/com/cue/daymark/updater/GitHubReleaseClient.java").read_text(encoding="utf-8")
for marker in (
    'echo "<!-- daymark-updater-v1"',
    '"applicationId":"com.cue.daymark"',
    '"versionCode":%s',
    '"minSdkVersion":%s',
    '"signerCertificateSha256":"%s"',
    'MIN_SDK_VERSION: ${{ steps.verify.outputs.min_sdk_version }}',
    'SIGNER_SHA256: ${{ steps.verify.outputs.signer_sha256 }}',
    'echo "min_sdk_version=$min_sdk_version" >> "$GITHUB_OUTPUT"',
    'echo "signer_sha256=${expected_signer,,}" >> "$GITHUB_OUTPUT"',
):
    assert marker in workflow, f"production release workflow missing updater metadata wiring: {marker}"
for marker in (
    'daymark-updater-v1',
    'Release is missing Daymark publisher metadata.',
    'requiredString(metadata, "applicationId")',
    'requiredLong(metadata, "versionCode")',
    'requiredInt(metadata, "minSdkVersion")',
    'requiredString(metadata, "signerCertificateSha256")',
):
    assert marker in client, f"updater parser contract changed unexpectedly: {marker}"
print("PASS: production release notes contain the updater metadata contract")

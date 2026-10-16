#!/usr/bin/env python3
"""Check that production releases emit the metadata required by Daymark's updater."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
workflow = (root.parent / ".github/workflows/android-production-release.yml").read_text(encoding="utf-8")
client = (root / "app/src/main/java/com/abrar/daymark/updater/GitHubReleaseClient.java").read_text(encoding="utf-8")
for marker in (
    'echo "<!-- daymark-updater-v1"',
    '"applicationId":"com.abrar.daymark"',
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
# Never advise users to bypass a warning that explicitly labels the APK harmful.
for marker in (
    'apk_bytes="$(stat -c%s "$apk")"',
    'unzip -tqq "$apk"',
    'grep -Fxq "AndroidManifest.xml" <<< "$apk_entries"',
    'grep -Fxq "resources.arsc" <<< "$apk_entries"',
    'grep -Fxq "classes.dex" <<< "$apk_entries"',
    'APK does not expose the expected launcher Activity',
    'Cold-launch signed production APK on Android 10 (API 29) emulator',
    'Cold-launch signed production APK on Android 16 (API 36) emulator',
    'bash android-app/tools/cold-launch-apk.sh',
    'The release attaches a single APK.',
    'warning that explicitly says the app is harmful must not be bypassed',
):
    assert marker in workflow, f"release artifact safety/cold-launch gate missing: {marker}"
assert 'Choose **Download anyway**' not in workflow, "unsafe Play Protect bypass instruction must not return"
assert 'for pair in "$APK_NAME:$APK_SHA:$apk"; do' in workflow, "release page must upload only the installable APK"
assert 'gh release create "$TAG" "$apk"' in workflow, "draft release must attach only the APK, not the AAB"
assert '--draft --verify-tag' in workflow, "production workflow must create a draft release, not publish automatically"
assert '.draft == true' in workflow, "workflow must verify draft status before finishing"
assert '--latest' not in workflow, "workflow must not auto-publish or mark the draft as latest"
assert 'Draft release is ready and is NOT public.' in workflow, "owner-device acceptance must precede public publication"
assert (root / "tools/cold-launch-apk.sh").is_file(), "cold-launch helper script is missing"
print("PASS: updater metadata, APK integrity, safe warning guidance, single-APK publication, and API-29/API-36 cold-launch gates")

#!/usr/bin/env bash
# Guardrail: app identity, signer, target API, and release workflows must stay aligned.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/.." && pwd)"
GRADLE="$ROOT/app/build.gradle.kts"
UPDATER="$ROOT/app/src/main/java/com/abrar/daymark/updater/UpdaterPublisherConfig.java"
DEVICE_TEST="$REPO_ROOT/.github/workflows/android-device-test-release.yml"
PRODUCTION="$REPO_ROOT/.github/workflows/android-production-release.yml"
RELEASE_ASSETS="$REPO_ROOT/.github/workflows/android-release-assets.yml"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

require_literal() {
  local file="$1" literal="$2" description="$3"
  [[ -s "$file" ]] || fail "missing file: $file"
  grep -Fq -- "$literal" "$file" || fail "$description"
}

require_literal "$GRADLE" 'namespace = "com.abrar.daymark"' 'namespace must stay com.abrar.daymark'
require_literal "$GRADLE" 'applicationId = "com.abrar.daymark"' 'applicationId must stay com.abrar.daymark after the approved identity reset'
require_literal "$GRADLE" 'compileSdk = 36' 'compileSdk must stay on API 36'
require_literal "$GRADLE" 'targetSdk = 36' 'targetSdk must stay on API 36'

code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE" | head -1)"
name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE" | head -1)"
[[ -n "$code" && -n "$name" ]] || fail 'versionCode/versionName missing'
[[ "$code" -ge 7 ]] || fail "versionCode $code < 7 (must remain monotonic after published v1.0.5/versionCode 6)"

require_literal "$UPDATER" 'ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580' 'UpdaterPublisherConfig must pin production signer SHA-256'

# Provision the exact compile platform on all build workflows and assert the package target.
require_literal "$DEVICE_TEST" 'platforms;android-36 build-tools;35.0.0' 'device-test workflow must provision API 36'
require_literal "$DEVICE_TEST" '^v[0-9]+\.[0-9]+(\.[0-9]+)?-device-test\.[0-9]+$' 'device-test workflow must accept versioned device-test tags'
require_literal "$DEVICE_TEST" '[[ "$target_sdk" == "36" ]]' 'device-test APK must assert target SDK 36'
require_literal "$PRODUCTION" 'platforms;android-36 build-tools;35.0.0' 'production workflow must provision API 36'
require_literal "$PRODUCTION" '[[ "$tag" =~ ^v[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]' 'production workflow must accept the exact v1.0 release tag format'
require_literal "$PRODUCTION" '[[ "$target_sdk_version" == "36" ]]' 'production APK must assert target SDK 36'
require_literal "$PRODUCTION" '[[ "$GITHUB_REF_NAME" == "v${version_name}" ]]' 'production tag must match the Gradle versionName'
require_literal "$RELEASE_ASSETS" 'platforms;android-36 build-tools;35.0.0' 'release-assets workflow must provision API 36'
require_literal "$RELEASE_ASSETS" '[[ "$target_sdk" == "36" ]]' 'release asset APK must assert target SDK 36'

echo "PASS release identity/API 36: applicationId=com.abrar.daymark versionName=$name versionCode=$code signer pin and all release workflow gates verified"

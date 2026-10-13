#!/usr/bin/env bash
# Guardrail: production identity must stay stable for same-app updates.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="$ROOT/app/build.gradle.kts"
UPDATER="$ROOT/app/src/main/java/com/cue/daymark/updater/UpdaterPublisherConfig.java"

grep -q 'applicationId = "com.abrar.daymark"' "$GRADLE" || {
  echo "FAIL: applicationId must be com.abrar.daymark after the approved identity reset" >&2
  exit 1
}

code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE" | head -1)"
name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE" | head -1)"
[[ -n "$code" && -n "$name" ]] || { echo "FAIL: versionCode/versionName missing" >&2; exit 1; }

# Baseline after v1.0.2 release
if [[ "$code" -lt 7 ]]; then
  echo "FAIL: versionCode $code < 7 (must remain monotonic after previous published versionCode 6)" >&2
  exit 1
fi

grep -q 'ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580' "$UPDATER" || {
  echo "FAIL: UpdaterPublisherConfig must pin production signer SHA-256" >&2
  exit 1
}

echo "PASS release identity: applicationId=com.abrar.daymark versionName=$name versionCode=$code signer pin present"

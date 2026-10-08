#!/usr/bin/env bash
# Issue #43: fail if a Wrangler-style package from repo root would include non-web assets.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .assetsignore ]]; then
  echo "::error::.assetsignore missing" >&2
  exit 1
fi

fail=0
while IFS= read -r -d '' path; do
  rel="${path#./}"
  case "$rel" in
    *.apk|*.aab|android-app/*|artifacts/*|.github/*|docs/*)
      echo "::error::Deploy package must not include: $rel" >&2
      fail=1
      ;;
  esac
done < <(find . -type f \
  ! -path './.git/*' \
  ! -path './node_modules/*' \
  ! -path './.wrangler/*' \
  ! -path './android-app/*' \
  ! -path './artifacts/*' \
  ! -path './.github/*' \
  ! -path './tests/*' \
  ! -path './docs/*' \
  ! -name '*.apk' \
  ! -name '*.aab' \
  ! -name '*.md' \
  -print0 2>/dev/null || true)

# Explicitly require key web files to still be present conceptually.
for required in index.html styles.css app.js; do
  if [[ ! -f "$required" ]]; then
    echo "::error::Required web file missing: $required" >&2
    fail=1
  fi
done

# .assetsignore must exclude APKs and android-app
grep -q '\*\.apk' .assetsignore || { echo "::error::.assetsignore must exclude *.apk" >&2; fail=1; }
grep -q 'android-app/' .assetsignore || { echo "::error::.assetsignore must exclude android-app/" >&2; fail=1; }
grep -q 'artifacts/' .assetsignore || { echo "::error::.assetsignore must exclude artifacts/" >&2; fail=1; }
grep -q '\.github/' .assetsignore || { echo "::error::.assetsignore must exclude .github/" >&2; fail=1; }

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "PASS web deploy package policy: .assetsignore excludes APKs/android-app/artifacts/.github; web entry files present"

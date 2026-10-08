#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
test -f .assetsignore
grep -q '\*\.apk' .assetsignore
grep -q 'android-app/' .assetsignore
grep -q 'artifacts/' .assetsignore
grep -q '\.github/' .assetsignore
for required in index.html styles.css app.js; do
  test -f "$required"
done
echo "PASS web deploy package policy"

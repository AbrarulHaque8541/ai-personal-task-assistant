#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/daymark-insets-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
javac -encoding UTF-8 --release 17 -d "$BUILD_DIR" \
  "$ROOT/app/src/main/java/com/cue/daymark/WindowInsetsPolicy.java" \
  "$ROOT/tools/WindowInsetsPolicySmoke.java"
java -ea -cp "$BUILD_DIR" com.cue.daymark.WindowInsetsPolicySmoke
python3 "$ROOT/tools/check-window-insets.py"

#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/daymark-textscale-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
javac -encoding UTF-8 --release 17 -d "$BUILD_DIR" \
  "$ROOT/app/src/main/java/com/cue/daymark/TextScalePolicy.java" \
  "$ROOT/tools/TextScalePolicySmoke.java"
java -ea -cp "$BUILD_DIR" com.cue.daymark.TextScalePolicySmoke
python3 "$ROOT/tools/check-text-scale.py"

#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/daymark-scroll-colour-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
javac -encoding UTF-8 --release 17 -d "$BUILD_DIR" \
  "$ROOT/app/src/main/java/com/abrar/daymark/ScrollColorBlend.java" \
  "$ROOT/tools/ScrollColorBlendSmoke.java"
java -ea -cp "$BUILD_DIR" com.abrar.daymark.ScrollColorBlendSmoke
python3 "$ROOT/tools/check-scroll-color-blend.py" "$ROOT"

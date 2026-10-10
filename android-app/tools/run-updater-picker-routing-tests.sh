#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "daymark-updater-picker-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
javac -encoding UTF-8 --release 17 -d "$BUILD_DIR" \
  "$ROOT/tools/UpdaterPickerRoutingSmoke.java"
java -cp "$BUILD_DIR" com.cue.daymark.UpdaterPickerRoutingSmoke

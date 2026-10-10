#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "daymark-attachment-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
javac -encoding UTF-8 --release 17 -d "$BUILD_DIR" \
  "$ROOT/app/src/main/java/com/cue/daymark/AttachmentRef.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/AttachmentLogic.java" \
  "$ROOT/tools/AttachmentLogicSmoke.java"
java -cp "$BUILD_DIR" com.cue.daymark.AttachmentLogicSmoke

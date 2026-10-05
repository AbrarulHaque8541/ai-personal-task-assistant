#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "daymark-core-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
python3 "$ROOT/tools/check-backup-rules.py"
javac --release 17 -d "$BUILD_DIR" \
  "$ROOT/app/src/main/java/com/cue/daymark/Task.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/TaskLogic.java" \
  "$ROOT/tools/TaskLogicSmoke.java"
java -ea -cp "$BUILD_DIR" com.cue.daymark.TaskLogicSmoke

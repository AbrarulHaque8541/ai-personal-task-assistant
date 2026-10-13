#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/daymark-portable-backup-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
python3 "$ROOT/tools/check-portable-archive-bounds.py"
javac -encoding UTF-8 --release 17 -d "$BUILD_DIR" \
  "$ROOT/app/src/main/java/com/abrar/daymark/AttachmentRef.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/AttachmentLogic.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/Task.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/Subtask.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskLogic.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskDuePresets.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/PortableBackupCodec.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/PortableImportGrantRecovery.java" \
  "$ROOT/tools/PortableBackupSmoke.java"
java -ea -cp "$BUILD_DIR" com.abrar.daymark.PortableBackupSmoke

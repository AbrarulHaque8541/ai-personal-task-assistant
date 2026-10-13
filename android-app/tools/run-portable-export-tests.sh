#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD="$ROOT/build/portable-export-tests"
rm -rf "$BUILD"
mkdir -p "$BUILD"
javac -encoding UTF-8 -d "$BUILD" \
    "$ROOT/app/src/main/java/com/abrar/daymark/PortableExportWriter.java" \
    "$ROOT/app/src/main/java/com/abrar/daymark/PortableBackupCodec.java" \
    "$ROOT/app/src/main/java/com/abrar/daymark/TaskLogic.java" \
    "$ROOT/app/src/main/java/com/abrar/daymark/AttachmentLogic.java" \
    "$ROOT/app/src/main/java/com/abrar/daymark/Task.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/Subtask.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskDuePresets.java" \
    "$ROOT/app/src/main/java/com/abrar/daymark/AttachmentRef.java" \
    "$ROOT/tools/PortableExportWriterSmoke.java"
java -cp "$BUILD" com.abrar.daymark.PortableExportWriterSmoke
python3 "$ROOT/tools/check-portable-export-policy.py" "$ROOT"

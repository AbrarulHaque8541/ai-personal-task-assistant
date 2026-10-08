#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD="$ROOT/build/portable-export-tests"
rm -rf "$BUILD"
mkdir -p "$BUILD"
javac -d "$BUILD" \
    "$ROOT/app/src/main/java/com/cue/daymark/PortableExportWriter.java" \
    "$ROOT/app/src/main/java/com/cue/daymark/PortableBackupCodec.java" \
    "$ROOT/app/src/main/java/com/cue/daymark/TaskLogic.java" \
    "$ROOT/app/src/main/java/com/cue/daymark/AttachmentLogic.java" \
    "$ROOT/app/src/main/java/com/cue/daymark/Task.java" \
    "$ROOT/app/src/main/java/com/cue/daymark/AttachmentRef.java" \
    "$ROOT/tools/PortableExportWriterSmoke.java"
java -cp "$BUILD" com.cue.daymark.PortableExportWriterSmoke

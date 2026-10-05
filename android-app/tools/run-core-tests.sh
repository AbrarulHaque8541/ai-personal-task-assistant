#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "daymark-core-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
javac --release 17 -d "$BUILD_DIR" \
  "$ROOT/tools/host-stubs/android/content/Context.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/Task.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/TaskLogic.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/UpdaterCore.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/UpdaterRecoveryStore.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/SafApkSaver.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/StrictJsonParser.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/GitHubReleaseClient.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/GitHubApkDownloader.java" \
  "$ROOT/tools/TaskLogicSmoke.java" \
  "$ROOT/tools/UpdaterSmoke.java" \
  "$ROOT/tools/GitHubTransportSmoke.java"
java -ea -cp "$BUILD_DIR" com.cue.daymark.TaskLogicSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.UpdaterSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.updater.GitHubTransportSmoke

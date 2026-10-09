#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "daymark-core-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
python3 "$ROOT/tools/check-backup-rules.py"
python3 "$ROOT/tools/check-task-template-source.py" "$ROOT"
javac --release 17 -d "$BUILD_DIR" \
  "$ROOT/tools/host-stubs/android/content/Context.java" \
  "$ROOT/tools/host-stubs/org/json/JSONObject.java" \
  "$ROOT/tools/host-stubs/org/json/JSONArray.java" \
  "$ROOT/tools/host-stubs/org/json/JsonWriter.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/AttachmentRef.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/AttachmentLogic.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/ActivityCallbackGate.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/Task.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/TaskLogic.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/TaskDuePresets.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/TaskTemplate.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/TaskTemplateLogic.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/TaskSnapshotSchema.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/TaskSnapshotCodec.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/Subtask.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserExtension.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserAddress.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserHistory.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserNetworkPolicy.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserSettingsPolicy.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserViewportPolicy.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/ExtensionPackageParser.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/CosmeticFilterToCss.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/UpdaterCore.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/UpdaterRecoveryStore.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/PendingSaveTransaction.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/SafApkSaver.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/StrictJsonParser.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/GitHubReleaseClient.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/updater/GitHubApkDownloader.java" \
  "$ROOT/tools/TaskLogicSmoke.java" \
  "$ROOT/tools/TaskSnapshotCodecSmoke.java" \
  "$ROOT/tools/BrowserExtensionSmoke.java" \
  "$ROOT/tools/ActivityCallbackGateSmoke.java" \
  "$ROOT/tools/UpdaterSmoke.java" \
  "$ROOT/tools/GitHubTransportSmoke.java" \
  "$ROOT/tools/BrowserAddressSmoke.java" \
  "$ROOT/tools/BrowserNetworkPolicySmoke.java" \
  "$ROOT/tools/BrowserSettingsPolicySmoke.java" \
  "$ROOT/tools/BrowserViewportPolicySmoke.java" \
  "$ROOT/tools/ExtensionPackageParserSmoke.java" \
  "$ROOT/tools/CosmeticFilterToCssSmoke.java"
java -ea -cp "$BUILD_DIR" com.cue.daymark.TaskLogicSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.TaskSnapshotCodecSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.BrowserExtensionSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.ActivityCallbackGateSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.UpdaterSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.updater.GitHubTransportSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.BrowserAddressSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.BrowserNetworkPolicySmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.BrowserSettingsPolicySmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.BrowserViewportPolicySmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.ExtensionPackageParserSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.CosmeticFilterToCssSmoke

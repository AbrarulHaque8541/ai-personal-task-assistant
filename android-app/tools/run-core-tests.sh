#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "daymark-core-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
python3 "$ROOT/tools/check-backup-rules.py"
python3 "$ROOT/tools/check-task-template-source.py" "$ROOT"
javac -encoding UTF-8 --release 17 -d "$BUILD_DIR" \
  "$ROOT/tools/host-stubs/android/content/Context.java" \
  "$ROOT/tools/host-stubs/org/json/JSONObject.java" \
  "$ROOT/tools/host-stubs/org/json/JSONArray.java" \
  "$ROOT/tools/host-stubs/org/json/JsonWriter.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/AttachmentRef.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/AttachmentLogic.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/ActivityCallbackGate.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/ScrollThemeColor.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/Task.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/NaturalLanguageTaskParse.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskLogic.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskListRenderGate.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskDuePresets.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskTemplate.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskTemplateLogic.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskSnapshotSchema.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/TaskSnapshotCodec.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/Subtask.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserExtension.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserAddress.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserHistory.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserLogPolicy.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserNetworkPolicy.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserMediaPolicy.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserTabPolicy.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserSettingsPolicy.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserViewportPolicy.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserSelfHealingPolicy.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/BrowserDiagnostics.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/ExtensionPackageParser.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/CosmeticFilterToCss.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/updater/UpdaterCore.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/updater/UpdaterRecoveryStore.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/updater/PendingSaveTransaction.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/updater/SafApkSaver.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/StrictJsonParser.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/updater/GitHubReleaseClient.java" \
  "$ROOT/app/src/main/java/com/abrar/daymark/updater/GitHubApkDownloader.java" \
  "$ROOT/tools/TaskLogicSmoke.java" \
  "$ROOT/tools/ScrollThemeColorSmoke.java" \
  "$ROOT/tools/NaturalLanguageTaskParseSmoke.java" \
  "$ROOT/tools/TaskListRenderGateSmoke.java" \
  "$ROOT/tools/TaskSnapshotCodecSmoke.java" \
  "$ROOT/tools/BrowserExtensionSmoke.java" \
  "$ROOT/tools/ActivityCallbackGateSmoke.java" \
  "$ROOT/tools/UpdaterSmoke.java" \
  "$ROOT/tools/GitHubTransportSmoke.java" \
  "$ROOT/tools/BrowserAddressSmoke.java" \
  "$ROOT/tools/BrowserLogPolicySmoke.java" \
  "$ROOT/tools/BrowserNetworkPolicySmoke.java" \
  "$ROOT/tools/BrowserMediaPolicySmoke.java" \
  "$ROOT/tools/BrowserTabPolicySmoke.java" \
  "$ROOT/tools/BrowserSettingsPolicySmoke.java" \
  "$ROOT/tools/BrowserViewportPolicySmoke.java" \
  "$ROOT/tools/BrowserSelfHealingPolicySmoke.java" \
  "$ROOT/tools/BrowserDiagnosticsSmoke.java" \
  "$ROOT/tools/ExtensionPackageParserSmoke.java" \
  "$ROOT/tools/CosmeticFilterToCssSmoke.java"
java -ea -cp "$BUILD_DIR" com.abrar.daymark.TaskLogicSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.ScrollThemeColorSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.NaturalLanguageTaskParseSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.TaskListRenderGateSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.TaskSnapshotCodecSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserExtensionSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.ActivityCallbackGateSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.UpdaterSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.updater.GitHubTransportSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserAddressSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserLogPolicySmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserNetworkPolicySmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserMediaPolicySmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserTabPolicySmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserSettingsPolicySmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserViewportPolicySmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserSelfHealingPolicySmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.BrowserDiagnosticsSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.ExtensionPackageParserSmoke
java -ea -cp "$BUILD_DIR" com.abrar.daymark.CosmeticFilterToCssSmoke

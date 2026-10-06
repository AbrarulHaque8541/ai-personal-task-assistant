#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "daymark-core-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
javac --release 17 -d "$BUILD_DIR" \
  "$ROOT/app/src/main/java/com/cue/daymark/Task.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/TaskLogic.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/Reminder.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/ReminderTombstone.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/ReminderDeliveryLock.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/ReminderLogic.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserAddress.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserHistory.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserNetworkPolicy.java" \
  "$ROOT/app/src/main/java/com/cue/daymark/BrowserSettingsPolicy.java" \
  "$ROOT/tools/TaskLogicSmoke.java" \
  "$ROOT/tools/BrowserAddressSmoke.java" \
  "$ROOT/tools/BrowserNetworkPolicySmoke.java" \
  "$ROOT/tools/BrowserSettingsPolicySmoke.java" \
  "$ROOT/tools/ReminderLogicSmoke.java"
java -ea -cp "$BUILD_DIR" com.cue.daymark.TaskLogicSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.BrowserAddressSmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.BrowserNetworkPolicySmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.BrowserSettingsPolicySmoke
java -ea -cp "$BUILD_DIR" com.cue.daymark.ReminderLogicSmoke

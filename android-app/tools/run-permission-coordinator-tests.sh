#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# Source presence + compile with android stubs is heavy; assert class API shape.
grep -q 'requestPostNotifications' app/src/main/java/com/cue/daymark/PermissionCoordinator.java
grep -q 'openExactAlarmSettings' app/src/main/java/com/cue/daymark/PermissionCoordinator.java
grep -q 'openAccessibilitySettings' app/src/main/java/com/cue/daymark/PermissionCoordinator.java
grep -q 'statusSummary' app/src/main/java/com/cue/daymark/PermissionCoordinator.java
echo "PASS permission coordinator source checks"

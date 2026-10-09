#!/usr/bin/env sh
# Regression test for issue #196: source guards must fail when required wiring is absent.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ACTIVITY="$ROOT/app/src/main/java/com/cue/daymark/MainActivity.java"
BACKUP=$(mktemp)
cp "$ACTIVITY" "$BACKUP"
restore() { cp "$BACKUP" "$ACTIVITY"; rm -f "$BACKUP"; }
trap restore EXIT INT TERM

expect_fail_after_removal() {
  checker="$1"
  marker="$2"
  MARKER="$marker" ACTIVITY="$ACTIVITY" python3 - <<'PY'
import os
from pathlib import Path
p = Path(os.environ["ACTIVITY"])
s = p.read_text(encoding="utf-8")
marker = os.environ["MARKER"]
if marker not in s:
    raise SystemExit(f"required marker not found in MainActivity.java: {marker}")
p.write_text(s.replace(marker, "REMOVED_BY_META_TEST"), encoding="utf-8")
PY
  if python3 "$ROOT/tools/$checker" >/dev/null 2>&1; then
    echo "FAIL: $checker exited 0 after removing required marker: $marker" >&2
    return 1
  fi
  cp "$BACKUP" "$ACTIVITY"
  echo "PASS meta: $checker fails when marker is missing"
}

expect_fail_after_removal check-text-scale.py "TextScalePolicy.combined("
expect_fail_after_removal check-window-insets.py "WindowInsets.Type.ime()"
expect_fail_after_removal check-web-mode-state.py "STATE_WEB_MODE"
expect_fail_after_removal check-silent-catch.py "StartupDiagnostics.record("

for checker in check-text-scale.py check-window-insets.py check-web-mode-state.py check-silent-catch.py; do
  python3 "$ROOT/tools/$checker" >/dev/null
  echo "PASS meta: $checker passes on current source"
done

echo "PASS source-check guard regression tests"

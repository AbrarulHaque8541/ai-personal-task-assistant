#!/usr/bin/env sh
# Regression test for issue #196: source guards must fail when required wiring is absent.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ACTIVITY="$ROOT/app/src/main/java/com/abrar/daymark/MainActivity.java"
BACKUP=$(mktemp)
cp "$ACTIVITY" "$BACKUP"
restore() { cp "$BACKUP" "$ACTIVITY"; rm -f "$BACKUP"; }
trap restore EXIT INT TERM

expect_fail_after_removal() {
  checker="$1"
  marker="$2"
  target="${3:-$ACTIVITY}"
  target_backup=$(mktemp)
  cp "$target" "$target_backup"
  MARKER="$marker" TARGET="$target" python3 - <<'PY'
import os
from pathlib import Path
p = Path(os.environ["TARGET"])
s = p.read_text(encoding="utf-8")
marker = os.environ["MARKER"]
if marker not in s:
    raise SystemExit(f"required marker not found in {p.name}: {marker}")
p.write_text(s.replace(marker, "REMOVED_BY_META_TEST"), encoding="utf-8")
PY
  if python3 "$ROOT/tools/$checker" >/dev/null 2>&1; then
    cp "$target_backup" "$target"
    rm -f "$target_backup"
    echo "FAIL: $checker exited 0 after removing required marker: $marker" >&2
    return 1
  fi
  cp "$target_backup" "$target"
  rm -f "$target_backup"
  echo "PASS meta: $checker fails when marker is missing"
}

expect_fail_after_removal check-text-scale.py "TextScalePolicy.combined("
WINDOW="$ROOT/app/src/main/java/com/abrar/daymark/Api30Window.java"
if [ -f "$WINDOW" ]; then
  expect_fail_after_removal check-window-insets.py "WindowInsets.Type.ime()" "$WINDOW"
  expect_fail_after_removal check-window-insets.py "Api29Window.relaxBarContrast(window);" "$ACTIVITY"
else
  expect_fail_after_removal check-window-insets.py "WindowInsets.Type.ime()"
fi
expect_fail_after_removal check-web-mode-state.py "STATE_WEB_MODE"
expect_fail_after_removal check-silent-catch.py "StartupDiagnostics.record("

for checker in check-text-scale.py check-window-insets.py check-web-mode-state.py check-silent-catch.py; do
  python3 "$ROOT/tools/$checker" >/dev/null
  echo "PASS meta: $checker passes on current source"
done

echo "PASS source-check guard regression tests"

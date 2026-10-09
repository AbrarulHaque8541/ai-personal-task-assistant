#!/usr/bin/env sh
# Meta-test for issue #196: each hardened source checker must FAIL (non-zero)
# when its required MainActivity wiring marker is removed, and PASS on the
# current source. Mutations are applied to a temporary copy and always restored.
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
  sed "s/$marker/REMOVED_BY_META_TEST/g" "$ACTIVITY" > "$ACTIVITY.meta" && mv "$ACTIVITY.meta" "$ACTIVITY"
  if python3 "$ROOT/tools/$checker" >/dev/null 2>&1; then
    echo "FAIL: $checker still exits 0 after removing required marker: $marker" >&2
    return 1
  fi
  cp "$BACKUP" "$ACTIVITY"
  echo "PASS meta: $checker fails non-zero when $marker is missing"
}

expect_fail_after_removal check-text-scale.py "TextScalePolicy.combined("
expect_fail_after_removal check-window-insets.py "WindowInsets.Type.ime()"
expect_fail_after_removal check-web-mode-state.py "STATE_WEB_MODE"
expect_fail_after_removal check-silent-catch.py "StartupDiagnostics.record("

for checker in check-text-scale.py check-window-insets.py check-web-mode-state.py check-silent-catch.py; do
  python3 "$ROOT/tools/$checker" >/dev/null
  echo "PASS meta: $checker passes on the current source"
done

echo "PASS check-guard meta-tests: hardened source checks cannot silently report PENDING"

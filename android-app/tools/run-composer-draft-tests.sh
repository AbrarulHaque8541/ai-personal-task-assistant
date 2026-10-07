#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/daymark-composer-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
javac --release 17 -d "$BUILD_DIR" \
  "$ROOT/app/src/main/java/com/cue/daymark/ComposerDraftPolicy.java" \
  "$ROOT/tools/ComposerDraftPolicySmoke.java"
java -ea -cp "$BUILD_DIR" com.cue.daymark.ComposerDraftPolicySmoke
python3 "$ROOT/tools/check-composer-draft.py"

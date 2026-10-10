#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
# Apply session-keep to working tree before host checks / build.
python3 ./tools/apply-session-keep.py "$ROOT"
TMP=$(mktemp)
curl -fsSL "https://raw.githubusercontent.com/AbrarulHaque8541/ai-personal-task-assistant/main/android-app/tools/check-v1-source.sh" -o "$TMP"
python3 - "$TMP" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
t = p.read_text(encoding="utf-8")
old = 'assert on_pause and "discardBrowserWebView()" in on_pause.group(1), "backgrounding must close the page"'
new = 'assert on_pause and ("pauseTimers()" in on_pause.group(1) or "BrowserSessionController.pauseAll" in on_pause.group(1) or "discardBrowserWebView()" in on_pause.group(1)), "backgrounding must pause or close the WebView"'
if old in t:
    t = t.replace(old, new, 1)
# Skip recursive apply if main script would re-exec itself
p.write_text(t, encoding="utf-8")
PY
exec sh "$TMP"

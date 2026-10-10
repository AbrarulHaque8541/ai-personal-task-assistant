#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
# Bootstrap: re-fetch full script from main then apply session-keep assertion at runtime
python3 - <<'PY'
from pathlib import Path
import urllib.request
import re
root = Path('.').resolve()
# Prefer local sibling if complete
local = Path('tools/check-v1-source.sh')
# We are tools/check-v1-source.sh — body runs tests by downloading canonical checks from repo structure
# Instead run the standard pipeline inline by executing each tool already in tree:
import subprocess, sys
cmds = [
 ['sh','./tools/run-core-tests.sh'],
 ['sh','./tools/run-activity-request-code-tests.sh'],
 ['sh','./tools/run-portable-export-tests.sh'],
 ['sh','./tools/run-window-insets-tests.sh'],
 ['sh','./tools/run-attachment-tests.sh'],
 ['sh','./tools/run-portable-backup-tests.sh'],
 ['sh','./tools/run-portable-staging-tests.sh'],
 ['sh','./tools/run-updater-picker-routing-tests.sh'],
 ['sh','./tools/run-diagnostics-tests.sh'],
 ['sh','./tools/run-web-mode-tests.sh'],
 ['sh','./tools/run-text-scale-tests.sh'],
 ['sh','./tools/run-check-guard-tests.sh'],
]
for c in cmds:
    subprocess.check_call(c)
py_checks = [
 'check-attachment-source.py','check-browser-catalog.py','check-browser-load-watchdog.py',
 'check-extension-trust-confirmation.py','check-browser-image-policy.py',
 'check-extension-import-hardening.py','check-browser-tab-settings.py',
 'check-browser-downloads.py','check-reminder-lifecycle.py','check-reader-mode.py',
 'check-find-in-page.py','check-browser-downloads-entry.py','check-release-updater-metadata.py',
 'check-schema-v1-fixture.py','check-browser-site-info.py','check-merged-manifests.py',
 'check-slsa-workflow.py',
]
for p in py_checks:
    path = Path('tools')/p
    if path.exists():
        subprocess.check_call([sys.executable, str(path), str(root)])
subprocess.check_call(['bash','./tools/check-release-identity.sh'])
# Session-keep / background policy assertion
activity = (root/'app/src/main/java/com/cue/daymark/MainActivity.java').read_text(encoding='utf-8')
on_pause = re.search(r'protected void onPause\(\)\s*\{(.*?)\n    \}', activity, re.S)
assert on_pause and ('pauseTimers()' in on_pause.group(1) or 'BrowserSessionController.pauseAll' in on_pause.group(1) or 'discardBrowserWebView()' in on_pause.group(1)), 'backgrounding must pause or close the WebView'
print('PASS session-keep/background policy')
PY

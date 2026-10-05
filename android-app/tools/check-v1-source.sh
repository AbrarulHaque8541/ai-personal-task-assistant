#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
sh ./tools/run-core-tests.sh
sh ./tools/run-attachment-tests.sh
python3 ./tools/check-attachment-source.py "$ROOT"
python3 - "$ROOT" <<'PY'
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
app = manifest.find("application")
assert app is not None, "missing application element"
assert not list(manifest.findall("uses-permission")), "V1 must not declare permissions or network access"
assert app.get("{http://schemas.android.com/apk/res/android}allowBackup") == "false", "backup must stay disabled"
assert app.get("{http://schemas.android.com/apk/res/android}usesCleartextTraffic") == "false", "cleartext must stay disabled"
assert not list(app.findall("service")), "V1 must not add services"
assert not list(app.findall("receiver")), "V1 must not add receivers"
providers = app.findall("provider")
assert len(providers) == 1, "only the grant-only attachment content provider is allowed"
provider = providers[0]
android = "{http://schemas.android.com/apk/res/android}"
assert provider.get(android + "name") == ".AttachmentContentProvider"
assert provider.get(android + "exported") == "false" and provider.get(android + "grantUriPermissions") == "true"

main = root / "app/src/main"
for path in main.rglob("*"):
    if path.is_file() and path.suffix.lower() in {".gguf", ".onnx", ".tflite", ".wav", ".mp3", ".flac", ".aab", ".apk"}:
        raise AssertionError(f"unexpected optional/binary asset in source: {path.relative_to(root)}")

build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
deps = build.split("dependencies {", 1)[1].split("}", 1)[0]
assert "implementation(" not in deps and "api(" not in deps and "runtimeOnly(" not in deps, "unexpected app runtime dependency"
activity = (main / "java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
for expected in ("What do you want", "Power path", "DEMO SUGGESTION", "highContrast", "textScale"):
    assert expected in activity, f"missing V1 source feature marker: {expected}"
for expected in ("Permission status:", "showPermissionStatus()", "PackageManager.GET_PERMISSIONS", "no Android permissions are declared"):
    assert expected in activity, f"missing permission-status behavior: {expected}"
for forbidden in ("requestPermissions(", "ActivityResultContracts.RequestPermission", "registerForActivityResult"):
    assert forbidden not in activity, f"V1 must not request permissions automatically: {forbidden}"

text_size_calls = re.findall(r"\.setTextSize\(([^)]*)\)", activity)
assert text_size_calls, "expected scalable text controls"
assert all("textScale" in call for call in text_size_calls), "every app text-size call must apply the user's text-size setting"
assert "textSizeMode == 0 ? 0.9f : textSizeMode == 2 ? 1.25f : 1.0f" in activity, "compact/standard/extra-large text choices changed"

for label in ("Search tasks by title", "Clear task search", "Choose low, medium, or high priority", "Edit task:", "Delete task:", "Mark “"):
    assert label in activity, f"missing screen-reader label source: {label}"
assert "setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)" in activity
assert "screen text is English only" in activity, "language limitations must remain explicit"
task_logic = (main / "java/com/cue/daymark/TaskLogic.java").read_text(encoding="utf-8")
assert "Locale.getDefault()" in task_logic, "date formatting should follow the device locale"
store = (main / "java/com/cue/daymark/EncryptedTaskStore.java").read_text(encoding="utf-8")
assert "TaskLogic.isValidTaskList(tasks)" in store, "encrypted writer must reject invalid or duplicate task snapshots"
assert "TaskLogic.isValidTaskList(result)" in store, "encrypted reader must use the same task-list validation contract"

print("PASS V1 source policy: no permissions/network, background components or runtime dependencies; only the non-exported grant-only attachment provider; no optional media/model binaries")
print("PASS accessibility/localization source checks: scalable text, labeled controls, explicit English-only scope, device-locale dates")
print("PASS permission policy: manifest-backed status only; no runtime permission prompt code")
print("PASS encrypted task-store policy: writer and reader share invalid/duplicate-task rejection")
PY
python3 "$ROOT/tools/check-accessibility-contrast.py"

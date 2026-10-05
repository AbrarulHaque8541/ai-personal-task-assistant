#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
sh ./tools/run-core-tests.sh
sh ./tools/run-attachment-tests.sh
sh ./tools/run-portable-backup-tests.sh
python3 ./tools/check-attachment-source.py "$ROOT"
python3 ./tools/check-schema-v1-fixture.py "$ROOT"
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
export_flow = activity.split("private void beginPortableExport", 1)[1].split("private void writePortableExport", 1)[0]
assert "new Intent(Intent.ACTION_CREATE_DOCUMENT)" in export_flow, "portable export must create a new SAF document"
assert "Intent.ACTION_OPEN_DOCUMENT" not in export_flow, "portable export must not select an existing document for overwrite"
codec = (main / "java/com/cue/daymark/PortableBackupCodec.java").read_text(encoding="utf-8")
assert 'Cipher.getInstance("AES/GCM/NoPadding")' in codec and "MAX_ARCHIVE_BYTES" in codec and "MAX_MANIFEST_BYTES" in codec
saved_state = activity.split("protected void onSaveInstanceState", 1)[1].split("protected void onActivityResult", 1)[0]
assert "STATE_PENDING_PORTABLE_IMPORT_URI" in saved_state, "pending portable URI must survive activity recreation"
assert "STATE_PENDING_PORTABLE_IMPORT_TOKEN" in saved_state, "pending import must save its non-secret operation token"
assert "activityStateForSelection" in saved_state, "only a matching live journal selection may enter saved state"
assert "pendingRecoveryKey" not in saved_state, "transient recovery key must never enter saved state"
assert "keyInput.setSaveEnabled(false)" in activity, "recovery-key entry must not be saved by view hierarchy state"
assert "takePersistableUriPermission" in activity
assert "restorePendingPortableImportSelection" in activity and "hasPersistedPortableReadGrant" in activity
assert "PortableImportGrantRecovery.awaitNoActivityRestoreWorker" in activity
assert "reconcileStartupImportUri" in activity, "startup must decide preserve versus cleanup after storage reconciliation"
selection = activity.split("private boolean retainPortableImportUri", 1)[1].split("private PortableImportGrantRecovery.Selection pendingPortableImportSelection", 1)[0]
assert selection.index("recordActivePortableImportUri(uri)") < selection.index("pendingPortableImportUri = uri"), "journal exact URI before exposing selected state or prompting for key"
picker_result = activity.split("protected void onActivityResult", 1)[1].split("private int themeResource", 1)[0]
assert picker_result.index("retainPortableImportUri(data.getData()") < picker_result.index("showPortableImportKeyDialog()"), "journal selected URI before recovery-key prompt"
destroy = activity.split("protected void onDestroy", 1)[1].split("protected void onSaveInstanceState", 1)[0]
assert "if (!portableRestoreWorkerActive)" in destroy
active_restore = activity.split("private void beginPortableRestore", 1)[1].split("private String portableRestoreFailure", 1)[0]
assert "finishActivePortableImportUri(" in active_restore and "selectedOperation" in active_restore
assert "PortableBackupCodec.clear(recoveryKey);" in active_restore and "portableRestoreWorkerActive = false;" in active_restore
assert "finishPortableImportSelection(selected" in activity, "cancel cleanup must reconcile before exact URI release"
manager = (main / "java/com/cue/daymark/PortableBackupManager.java").read_text(encoding="utf-8")
assert "writePending(" in manager and "appendAtomically(additions)" in manager and "reconcile(" in manager
assert "reconcileStartupImportUri" in manager and "reconcileStartup(" in manager
assert "recordTakenGrantOrRelease" in manager and "releaseExactPortableReadGrant" in manager
assert "JOURNAL_MAGIC" in manager and "operationToken" in manager, "URI journal must bind the token and URI"
assert "releasePersistableUriPermission(uri," in manager and "Intent.FLAG_GRANT_READ_URI_PERMISSION" in manager
assert "exactUri.equals(permission.getUri()) && permission.isReadPermission()" in manager
preflight = codec.split("private static void preflightManifestAttachmentCount", 1)[1].split("private static void skipManifestString", 1)[0]
assert "totalAttachments > AttachmentLogic.MAX_TOTAL_COUNT - attachmentCount" in preflight
assert codec.index("preflightManifestAttachmentCount(plaintext)") < codec.index("List<PortableTask> tasks = new ArrayList<>(taskCount)")
writer = codec.split("static String writeArchive", 1)[1].split("static VerifiedArchive readArchive", 1)[0]
assert writer.index("countAttachmentDescriptors(tasks)") < writer.index("TaskLogic.isValidTaskList(tasks)")
grant_recovery = (main / "java/com/cue/daymark/PortableImportGrantRecovery.java").read_text(encoding="utf-8")
assert "transaction.reconcile();" in grant_recovery and "releaseAndClear(journal, releaser, journaled);" in grant_recovery
assert "ACTIVE_SELECTIONS" in grant_recovery and "isValidLiveSelection" in grant_recovery
assert "restorePendingActivitySelection" in grant_recovery and "operationToken" in grant_recovery
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
print("PASS portable backup policy: bounded AES-GCM format, SAF create-only export, snapshot-last restore journal")
PY
python3 "$ROOT/tools/check-accessibility-contrast.py"

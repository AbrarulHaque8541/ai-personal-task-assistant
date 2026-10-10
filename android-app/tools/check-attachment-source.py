#!/usr/bin/env python3
import pathlib
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
main = (root / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
store = (root / "app/src/main/java/com/cue/daymark/AndroidAttachmentStore.java").read_text(encoding="utf-8")
blob = (root / "app/src/main/java/com/cue/daymark/AttachmentBlobStore.java").read_text(encoding="utf-8")
logic = (root / "app/src/main/java/com/cue/daymark/AttachmentLogic.java").read_text(encoding="utf-8")
provider = (root / "app/src/main/java/com/cue/daymark/AttachmentContentProvider.java").read_text(encoding="utf-8")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"

assert "Intent.ACTION_OPEN_DOCUMENT" in main and "Intent.CATEGORY_OPENABLE" in main
assert 'picker.setType("*/*")' in main, "preserve arbitrary provider MIME types"
assert "EXTRA_LOCAL_ONLY" not in main, "keep Android document-provider choice neutral"
portable_grant_helpers = main.split("private boolean retainPortableImportUri", 1)[1].split(
    "private void showPortableImportKeyDialog", 1)[0]
attachment_source = main.replace(portable_grant_helpers, "")
assert "FLAG_GRANT_PERSISTABLE_URI_PERMISSION" not in attachment_source
assert "takePersistableUriPermission" not in attachment_source
saved_state = main.split("protected void onSaveInstanceState", 1)[1].split(
    "protected void onActivityResult", 1)[0]
assert "pendingPickedAttachmentUri" not in saved_state, "attachment provider URIs must remain transient"
assert "READ_EXTERNAL_STORAGE" not in main and "READ_MEDIA_" not in main
permission_nodes = [
    node for node in manifest
    if node.tag == "uses-permission" or node.tag.startswith("uses-permission-sdk-")
]
declared_permissions = set(i.get(android + "name") for i in permission_nodes)
allowed_permissions = {
    "android.permission.INTERNET",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.VIBRATE",
}
assert declared_permissions.issubset(allowed_permissions), f"unexpected permissions declared: {declared_permissions - allowed_permissions}"
assert "android.permission.INTERNET" in declared_permissions, "INTERNET permission must be declared"
for perm in declared_permissions:
    assert "STORAGE" not in perm and "MEDIA" not in perm, f"broad storage permission forbidden: {perm}"
providers = manifest.findall(".//provider")
assert len(providers) == 1, "the only provider is the attachment read bridge"
assert providers[0].get(android + "exported") == "false"
assert providers[0].get(android + "grantUriPermissions") == "true"
assert "${applicationId}.attachments" in providers[0].get(android + "authorities", "")
assert "context.getNoBackupFilesDir()" in store, "attachment ciphertext must stay outside Android backup"
assert "OpenableColumns.DISPLAY_NAME" in store and "OpenableColumns.SIZE" in store
assert "openInputStream(uri)" in store
assert "long sizeHint = reportedSize >= 0" in store
assert "AttachmentLogic.MAX_ATTACHMENT_BYTES" in store
assert "AttachmentLogic.MAX_ATTACHMENTS_PER_TASK" in store
assert "sanitizeDisplayName" in store
assert "guessMimeType" in store
assert "copyBounded" in store
assert "deleteQuietly" in store
assert "AttachmentLogic.isValidAttachmentList" in store
assert "filesDir" not in store or "getNoBackupFilesDir" in store
assert "CipherOutputStream" in blob or "AES" in blob or "encrypt" in blob.lower()
assert "openFile" in provider or "openPipeHelper" in provider or "ParcelFileDescriptor" in provider
assert "AttachmentLogic" in logic
print("PASS attachment source checks")

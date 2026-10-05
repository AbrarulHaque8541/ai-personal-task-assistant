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
assert "FLAG_GRANT_PERSISTABLE_URI_PERMISSION" not in main
assert "takePersistableUriPermission" not in main
assert "READ_EXTERNAL_STORAGE" not in main and "READ_MEDIA_" not in main
assert not list(manifest.findall("uses-permission")), "attachment selection must not add broad storage permissions"
providers = manifest.findall(".//provider")
assert len(providers) == 1, "the only provider is the attachment read bridge"
assert providers[0].get(android + "exported") == "false"
assert providers[0].get(android + "grantUriPermissions") == "true"
assert "${applicationId}.attachments" in providers[0].get(android + "authorities", "")
assert "context.getNoBackupFilesDir()" in store, "attachment ciphertext must stay outside Android backup"
assert "OpenableColumns.DISPLAY_NAME" in store and "OpenableColumns.SIZE" in store
assert "openInputStream(uri)" in store
assert "long sizeHint = reportedSize >= 0" in store
assert "reportedSize > AttachmentLogic.MAX_FILE_BYTES" not in store, "provider size hints must not override actual stream limits"
assert "uri.toString()" not in store and "uri.getPath()" not in store
assert "Cipher.getInstance(\"AES/GCM/NoPadding\")" in blob
assert "cipher.updateAAD(associatedData(taskId, id));" in blob, "bind each payload to its canonical blob and task IDs"
assert "private static byte[] associatedData(String taskId, String id)" in blob
assert "blobs.importStream(taskId, appOwnedId" in store
assert "blobs.openInput(taskId, appOwnedId)" in store
assert "MAX_FILE_BYTES" in blob and "MAX_TOTAL_STORAGE_BYTES" in blob
assert "remainingTotalBytes" in blob and "CancellationCheck" in blob
assert "StorageSpaceException" in blob and "cleanupOrphans" in blob
assert "renameTo(destination)" in blob and ".pending" in blob
assert '"Open with another app"' in main and "Intent.ACTION_VIEW" in main
assert "Intent.createChooser(view" in main and "FLAG_GRANT_READ_URI_PERMISSION" in main
assert "isSafeToOpenExternally" in logic and "UNSAFE_EXTENSIONS" in logic
assert "openPipeHelper" in provider and "openDecrypted(attachment.taskId" in provider
assert 'if (!"r".equals(mode))' in provider
assert "findBinding(uri)" in provider and "new AttachmentBinding(task.id, reference)" in provider, \
    "provider must resolve the owning task for IDs referenced by encrypted task metadata"
test = (root / "tools/AttachmentBlobStoreSmoke.java").read_text(encoding="utf-8")
assert "payloadSubstitutionAcrossAttachmentOrTaskFailsAuthentication" in test
assert "The app you choose may retain it" in main or "may retain it" in main
attachment_import = main.split("private void importAttachment(", 1)[1].split(
    "private void requestAttachmentCancel()", 1)[0]
failure_cleanup = attachment_import.split("} catch (Exception exception) {", 1)[1].split("} finally", 1)[0]
assert "if (saveAttempted)" in failure_cleanup and "cleanupDeferred = true" in failure_cleanup, \
    "retain the encrypted payload when task-snapshot commit status may be ambiguous"
assert failure_cleanup.index("if (saveAttempted)") < failure_cleanup.index("else {") \
    < failure_cleanup.index("attachmentStore.delete(newId)"), \
    "only delete an imported payload immediately before any task-snapshot save attempt"
assert "saveOutcomeAmbiguous = saveAttempted && error != null" in attachment_import
assert "Attachment save could not be verified" in attachment_import and "Reopen Daymark" in attachment_import, \
    "save-attempt failures must not be reported as definite rollback; prompt a safe reload/reconciliation"
print("PASS attachment source policy: arbitrary SAF imports, no broad permission, encrypted quotas/cancel/low-space, no persistent provider URI, grant-only read bridge, confirm-gated allowlisted open")

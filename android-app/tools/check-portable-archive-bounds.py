#!/usr/bin/env python3
"""Reproduce portable archive metadata and local snapshot upper-bound calculations."""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAX_TASKS = 10_000
MAX_TOTAL_COUNT = 100
MAX_PER_TASK = 5
MAX_FILE_BYTES = 20 * 1024 * 1024
MAX_TOTAL_BYTES = 100 * 1024 * 1024
MAX_NAME_CHARS = 120
MAX_MANIFEST_BYTES = 8 * 1024 * 1024
MAX_ARCHIVE_BYTES = 110 * 1024 * 1024
MAX_STORE_BYTES = 10 * 1024 * 1024
STORE_CRYPTO_OVERHEAD_BYTES = 4 + 12 + 16

codec_source = (ROOT / "app/src/main/java/com/cue/daymark/PortableBackupCodec.java").read_text()
logic_source = (ROOT / "app/src/main/java/com/cue/daymark/AttachmentLogic.java").read_text()
store_source = (ROOT / "app/src/main/java/com/cue/daymark/EncryptedBlobStore.java").read_text()
task_logic_source = (ROOT / "app/src/main/java/com/cue/daymark/TaskLogic.java").read_text()
assert "MAX_ARCHIVE_BYTES = 110L * 1024L * 1024L" in codec_source
assert "MAX_MANIFEST_BYTES = 8 * 1024 * 1024" in codec_source
assert "MAX_TASKS = 10_000" in codec_source
assert "MAX_FILE_BYTES = 20L * 1024L * 1024L" in logic_source
assert "MAX_TOTAL_BYTES = 100L * 1024L * 1024L" in logic_source
assert "MAX_TOTAL_COUNT = 100" in logic_source and "MAX_PER_TASK = 5" in logic_source
assert "MAX_NAME_CHARS = 120" in logic_source
assert "task.title.length() > 160" in task_logic_source
assert "MAX_STORE_BYTES = 10 * 1024 * 1024" in store_source

# These are legal maximum-length values: U+0800 is a safe three-byte BMP
# scalar, Instant.parse accepts this 41-byte boundary value, and the MIME
# token is 64 + slash + 64 permitted ASCII characters.
title = "\u0800" * 160
display_name = "\u0800" * MAX_NAME_CHARS
mime_type = "a" * 64 + "/" + "b" * 64
timestamp = "+999999999-12-31T23:59:59.999999999+18:00"
due_date = "9999-12-31"
assert len(title) == 160 and len(title.encode("utf-8")) == 480
assert len(display_name) == MAX_NAME_CHARS and len(display_name.encode("utf-8")) == 360
assert len(mime_type.encode("ascii")) == 129 and len(timestamp.encode("ascii")) == 41

# Match TaskSnapshotCodec.encode field names/order and compact JSON shape (schema v4).
# The chosen strings require no JSON escaping; the codec's writer retains U+0800 raw.
def uuid_like(prefix: str, serial: int) -> str:
    return f"{prefix}-0000-4000-8000-{serial:012x}"

references = [
    {
        "id": uuid_like("10000000", index),
        "displayName": display_name,
        "mimeType": mime_type,
        "sizeBytes": MAX_TOTAL_BYTES // MAX_TOTAL_COUNT,
    }
    for index in range(MAX_TOTAL_COUNT)
]
tasks = []
for index in range(MAX_TASKS):
    tasks.append(
        {
            "id": uuid_like("00000000", index),
            "title": title,
            "dueDate": due_date,
            "priority": "medium",
            "completed": False,
            "createdAt": timestamp,
            "updatedAt": timestamp,
            "notes": "",
            "dueTime": None,
            "reminderLeadMinutes": None,
            "reminderShownFire": None,
            "subtasks": [],
            "attachments": references[index * MAX_PER_TASK:(index + 1) * MAX_PER_TASK]
            if index < MAX_TOTAL_COUNT // MAX_PER_TASK
            else [],
        }
    )
snapshot_bytes = len(
    json.dumps({"version": 4, "tasks": tasks}, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
)
assert snapshot_bytes == 8_248_603, f"unexpected maximum-profile task JSON size: {snapshot_bytes}"
assert snapshot_bytes <= MAX_STORE_BYTES - STORE_CRYPTO_OVERHEAD_BYTES

per_task_manifest = (
    16  # task token
    + 4 + len(title.encode("utf-8"))
    + 1 + 4 + len(due_date.encode("utf-8"))
    + 1 + 1  # priority and completed
    + 4 + len(timestamp.encode("ascii"))
    + 4 + len(timestamp.encode("ascii"))
    + 1  # attachment count
    + 4  # v2 extension: empty notes string (length prefix only)
    + 1  # due-time flag
    + 1  # reminder lead code
    + 1  # reminder-shown flag
    + 1  # subtask count
)
per_attachment_manifest = (
    16 + 8
    + 4 + len(display_name.encode("utf-8"))
    + 4 + len(mime_type.encode("ascii"))
)
manifest_bytes = 8 + MAX_TASKS * per_task_manifest + MAX_TOTAL_COUNT * per_attachment_manifest
assert per_task_manifest == 616
assert per_attachment_manifest == 521
assert manifest_bytes == 6_212_108 and manifest_bytes < MAX_MANIFEST_BYTES
frame_bytes = 28 + (MAX_TOTAL_COUNT + 1) * (57 + 16)
archive_bytes = MAX_TOTAL_BYTES + manifest_bytes + frame_bytes
archive_gap = MAX_ARCHIVE_BYTES - archive_bytes
assert frame_bytes == 7_401
assert archive_bytes == 111_077_109 and 0 < archive_gap

print(
    "PASS portable archive bounds: "
    f"snapshot={snapshot_bytes} bytes; manifest={manifest_bytes} bytes; "
    f"max-valid-archive={archive_bytes} bytes; cap-gap={archive_gap} bytes"
)

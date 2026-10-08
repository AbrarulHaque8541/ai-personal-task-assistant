#!/usr/bin/env python3
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
fixture_path = root / "app/src/androidTest/assets/schema-v1-task-snapshot.json"
fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
assert set(fixture) == {"version", "tasks"}, "schema-v1 fixture root must be complete and exact"
assert type(fixture["version"]) is int and fixture["version"] == 1, "fixture must identify legacy schema v1"
assert isinstance(fixture["tasks"], list) and len(fixture["tasks"]) == 2, "fixture must cover multiple legacy tasks"
expected_fields = {"id", "title", "dueDate", "priority", "completed", "createdAt", "updatedAt"}
ids = set()
for task in fixture["tasks"]:
    assert set(task) == expected_fields, "each schema-v1 task must include exactly the seven legacy fields"
    assert isinstance(task["id"], str) and task["id"] not in ids, "fixture task IDs must be unique strings"
    ids.add(task["id"])
    assert isinstance(task["title"], str), "legacy title must be a JSON string"
    assert task["dueDate"] is None or isinstance(task["dueDate"], str), "legacy dueDate must be string or null"
    assert task["priority"] in {"low", "medium", "high"}, "legacy priority must be valid"
    assert type(task["completed"]) is bool, "legacy completion must be a JSON boolean"
    assert isinstance(task["createdAt"], str) and isinstance(task["updatedAt"], str), "legacy timestamps must be strings"
assert any(task["dueDate"] is None for task in fixture["tasks"]), "fixture should cover nullable due dates"
assert any(task["completed"] for task in fixture["tasks"]), "fixture should cover completed tasks"
assert any(not task["completed"] for task in fixture["tasks"]), "fixture should cover open tasks"

store = (root / "app/src/main/java/com/cue/daymark/EncryptedTaskStore.java").read_text(encoding="utf-8")
codec = (root / "app/src/main/java/com/cue/daymark/TaskSnapshotCodec.java").read_text(encoding="utf-8")
assert "TaskSnapshotCodec.decode(" in store, "the encrypted store delegates snapshot decoding to the shared codec"
assert "TaskSnapshotSchema.isSupportedVersion(version)" in codec, "decoder must reject unsupported schema versions"
assert "TaskSnapshotSchema.isVersionTwo(version)" in codec and "TaskSnapshotSchema.isVersionThree(version)" in codec, \
    "schemas v2 and v3 decode attachment metadata"
assert "TaskSnapshotSchema.isVersionThree(version)" in codec and "TaskSnapshotSchema.isVersionFour(version)" in codec, \
    "schemas v3 and v4 decode template and extended task metadata"
assert "TaskSnapshotSchema.isVersionFour(version)" in codec, "schema v4 decodes notes, due time, reminders, and subtasks"
assert "TaskTemplateLogic.isValidList(templates)" in codec, "template snapshots use shared validation"
assert 'requiredString(object, "title")' in codec, "legacy scalar decoding must remain strict"
assert "if (!TaskLogic.isValidTaskList(result))" in codec, "imported snapshots must pass shared task-list validation"

gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
assert 'testInstrumentationRunner = "com.cue.daymark.DaymarkPlatformInstrumentation"' in gradle
assert not any(token in gradle for token in ("testImplementation(", "androidTestImplementation(", "androidTestApi(", "androidTestRuntimeOnly(")), "platform tests must not add dependencies"

tests = (root / "app/src/androidTest/java/com/cue/daymark/DaymarkPlatformInstrumentation.java").read_text(encoding="utf-8")
for marker in (
    "extends Instrumentation",
    "importLegacySnapshotRoundTripAndRollback()",
    "attachmentProviderPipeAndAndroidCrypto()",
    "AttachmentContentProvider",
    "provider.openFile(uri, \"r\")",
    "provider.query(uri, null, null, null, null)",
    "AndroidAttachmentStore.AndroidKeyAccess",
    "expectNoPlaintext",
    "EncryptedBlobStore.Kind.CORRUPT_DATA",
    "EncryptedBlobStore.Kind.AUTHENTICATION_FAILED",
):
    assert marker in tests, f"platform instrumentation is missing required coverage marker: {marker}"
assert "org.junit" not in tests and "androidx.test" not in tests, "platform instrumentation must not depend on JUnit/AndroidX"

print("PASS schema-v1 golden fixture: complete legacy fields, nullable dates, open/completed tasks, strict decoder checks")
print("PASS dependency-free Android instrumentation source: migration/rollback, provider metadata/pipe and Android Keystore coverage")

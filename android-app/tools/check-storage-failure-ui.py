#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / "app/src/main/java/com/cue/daymark/MainActivity.java").read_text()
STORE = (ROOT / "app/src/main/java/com/cue/daymark/EncryptedTaskStore.java").read_text()
BLOB_STORE = (ROOT / "app/src/main/java/com/cue/daymark/EncryptedBlobStore.java").read_text()
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


load_start = ACTIVITY.index("private void loadEncryptedTasks()")
save_start = ACTIVITY.index("private void saveTasksAsync()", load_start)
load = ACTIVITY[load_start:save_start]
load_error = load.split("} else {", 1)[1]
check("storageLoadFailed = true" in load_error, "load failure enters an explicit unavailable state")
check("storageFailureStatus(error)" in load_error, "load failure class is shown in status")
check("tasks.clear()" not in load_error, "load failure does not clear the in-memory task list")
check("lastSavedTasks.clear()" not in load_error, "load failure does not clear the saved snapshot")
check("Saved tasks are unavailable" in load_error, "load failure gives user-facing feedback")
check("storageLoading = true" in load and "storageLoading = false" in load,
      "initial loading remains distinct until the storage read completes")

save_end = ACTIVITY.index("private String storageFailureStatus(", save_start)
save = ACTIVITY[save_start:save_end]
failed_save = save.split("} else if (revision == saveRevision) {", 1)[1]
check("Not saved · unsaved changes are shown" in failed_save,
      "failed save explicitly labels the visible changes unsaved")
check("These changes are not saved" in failed_save,
      "failed edit warns changes may be lost after leaving")
check("storageReady = false" in failed_save, "failed save pauses subsequent edits")
check("tasks.clear()" not in failed_save and "tasks.addAll(lastSavedTasks)" not in failed_save,
      "failed save does not silently revert and discard the visible edit")
check("lastSavedTasks.clear()" not in failed_save,
      "failed save retains the last-known committed in-memory snapshot")

render_start = ACTIVITY.index("private void renderTaskList(LocalDate today)")
render_end = ACTIVITY.index("private void renderTaskCount()", render_start)
render_tasks = ACTIVITY[render_start:render_end]
check(render_tasks.index("if (storageLoading || storageLoadFailed)") < render_tasks.index("String selectedFilter"),
      "loading or load failure takes precedence over ordinary empty/filter states")
check('emptyTitle.setText("Saved tasks unavailable")' in render_tasks,
      "corrupt/unavailable data is not presented as an ordinary empty list")
check('if (storageSaveFailed)' in render_tasks and 'emptyTitle.setText("Change not saved")' in render_tasks,
      "a failed deletion of the last task is labeled as an unsaved change")
check('openCount.setText(storageLoading || storageLoadFailed ? "—"' in ACTIVITY
      and 'dueTodayCount.setText(storageLoading || storageLoadFailed ? "—"' in ACTIVITY,
      "loading and load errors are not reported as zero task metrics")
check('taskCount.setText("Unavailable")' in ACTIVITY,
      "task count reports unavailable data instead of zero")
check("Suggestions are unavailable until saved tasks can be opened." in ACTIVITY,
      "suggestion empty-state copy does not imply an empty task list")

check("keys.loadExistingKey()" in BLOB_STORE, "load uses only the existing key")
check("keys.createKeyForNewStore()" in BLOB_STORE, "key creation is limited to a new empty store")
check('baseFile.getPath() + ".bak"' in STORE and 'baseFile.getPath() + ".new"' in STORE,
      "Android adapter distinguishes a recoverable backup/staged write from no store")
check("Files.move" not in STORE and "AtomicFile" in STORE,
      "persistence uses Android AtomicFile instead of a non-atomic replacement fallback")
check("fileOutput.getFD().sync()" in STORE,
      "the Android adapter surfaces file-sync errors before AtomicFile commit")
check("Arrays.equals(blob, committedBlob)" in BLOB_STORE,
      "the encrypted snapshot is read back and compared before save success")
check("synchronized (FILE_ACCESS_LOCK)" in STORE,
      "file load/save operations are serialized across Activity instances")
check("verifyLoadedSnapshotUnchanged()" in BLOB_STORE,
      "a stale Activity cannot overwrite a newer encrypted snapshot")
check('Object version = document.opt("version")' in STORE
      and "TaskSnapshotSchema.isSupportedVersion(version)" in STORE
      and "optInt(" not in STORE,
      "snapshot versions are validated without numeric coercion")
check("TaskSnapshotSchema.requireString(object.opt(\"title\"))" in STORE
      and "optString(" not in STORE,
      "required task strings are validated without string coercion")

print(f"PASS storage failure UI/source checks: {checks} assertions")

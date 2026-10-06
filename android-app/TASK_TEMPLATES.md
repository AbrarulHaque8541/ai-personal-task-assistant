# Android task templates

Task templates are an optional shortcut in the native Android task composer. They work locally and offline; they do not add a network client, Android permission, background work, automation, or runtime dependency.

## Create and manage a template

1. In **Task** mode, open **Task templates** and tap **New template**, or open **Add with a date or priority** and tap **Save as template**.
2. Enter or adjust the task title, optional due date, and priority, then choose **Save template** (or **Save as template** in the existing task editor).
3. Saving a template does **not** create a task. The template title is its list label; its saved values are the title, optional date, and priority. At most 100 templates can be stored. Remove a template from the template list after a confirmation; this does not modify any existing task.

## Reuse and task creation

1. Open **Task templates** and choose **Use** for the desired template.
2. Daymark opens the existing task-details form with the saved title, date, and priority populated. Review and edit any value, including clearing or changing a saved due date.
3. The task is created only after tapping **Create task**. Cancel or system Back creates nothing. The created task gets a new ID and timestamps, starts incomplete, and does not inherit template identity or attachments.

Template selection alone never writes a task. The saved due date is copied as-is, so users should review it before confirmation; it is not a relative-date rule.

## Storage, migration, and recovery boundary

Templates are serialized in schema-v3 JSON inside the existing AES-GCM encrypted `files/tasks.enc` snapshot, protected by the existing non-exportable Android Keystore key and `AtomicFile` commit/read-back flow. Existing schema-v1 and schema-v2 task snapshots remain readable; templates are absent in those older snapshots until saved. Task and template updates share one encrypted snapshot write. A read/authentication/key failure remains fail-closed; it is not treated as an empty template list or task list.

Templates are **device-local** and are not included in the existing portable `.dmbackup` task/attachment archive. Exporting or restoring a portable task archive does not export, replace, or clear the device's templates. Clearing app data or uninstalling removes the local snapshot and its Keystore key, so templates are lost along with other local task data if no separate recovery exists.

## Focused verification

From the repository root, run:

```sh
sh android-app/tools/run-core-tests.sh
python3 android-app/tools/check-schema-v1-fixture.py android-app
```

The JDK smoke suite covers template bounds and validation, fresh task identity and defaults, editable template-to-task values, and supported snapshot versions. Android platform instrumentation additionally covers encrypted template save/reopen and v1/v2-to-v3 migration; it requires a device or emulator to execute.

from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
application = manifest.find("application")
assert application is not None, "missing application element"
assert application.get(ANDROID_NS + "allowBackup") == "false", "automatic backup must remain disabled"
assert application.get(ANDROID_NS + "dataExtractionRules") == "@xml/data_extraction_rules", (
    "Android 12+ backup and device-transfer rules must be referenced"
)
assert application.get(ANDROID_NS + "fullBackupContent") == "@xml/full_backup_content", (
    "legacy backup rules must be referenced"
)

store_source = (ROOT / "app/src/main/java/com/cue/daymark/EncryptedTaskStore.java").read_text(encoding="utf-8")
assert 'new File(context.getFilesDir(), "tasks.enc")' in store_source, (
    "update backup exclusions if the task store path changes"
)
assert 'new File(baseFile.getPath() + ".bak")' in store_source, (
    "update backup exclusions if the AtomicFile backup path changes"
)
assert 'new File(baseFile.getPath() + ".new")' in store_source, (
    "update backup exclusions if the AtomicFile staging path changes"
)
expected = {
    ("file", "tasks.enc"),
    ("file", "tasks.enc.bak"),
    ("file", "tasks.enc.new"),
    ("file", "tasks.enc.tmp"),  # Legacy writer temp file; exclude any leftover from prior builds.
}

modern = ET.parse(ROOT / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
assert modern.tag == "data-extraction-rules"
policies = []
for section_name in ("cloud-backup", "device-transfer"):
    section = modern.find(section_name)
    assert section is not None, f"missing {section_name} rules"
    exclusions = {
        (element.get("domain"), element.get("path"))
        for element in section.findall("exclude")
    }
    assert expected <= exclusions, (
        f"{section_name} must exclude encrypted task data and all AtomicFile/legacy temp artifacts"
    )
    policies.append((section_name, exclusions))

legacy = ET.parse(ROOT / "app/src/main/res/xml/full_backup_content.xml").getroot()
assert legacy.tag == "full-backup-content"
legacy_exclusions = {
    (element.get("domain"), element.get("path"))
    for element in legacy.findall("exclude")
}
assert expected <= legacy_exclusions, (
    "legacy backup rules must exclude encrypted task data and all AtomicFile/legacy temp artifacts"
)
policies.append(("legacy-backup", legacy_exclusions))

# AtomicFile may leave a recoverable prior snapshot in .bak and an interrupted staged write in .new.
# Simulate those artifacts being offered for backup/transfer, and ensure no policy copies them off-device.
interrupted_atomic_write_artifacts = {"tasks.enc.bak", "tasks.enc.new"}
for policy_name, exclusions in policies:
    copied = {
        artifact
        for artifact in interrupted_atomic_write_artifacts
        if ("file", artifact) not in exclusions
    }
    assert not copied, (
        f"interrupted AtomicFile artifacts would be copied by {policy_name}: {sorted(copied)}"
    )

print("PASS Android backup policy: task ciphertext, AtomicFile .bak/.new, and legacy temp artifacts excluded from cloud, device transfer, and legacy backup")
print("PASS interrupted AtomicFile regression: recoverable .bak and staged .new artifacts are not copied off-device")

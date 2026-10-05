from pathlib import Path
import sys
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
assert 'new File(storeFile.getParentFile(), "tasks.enc.tmp")' in store_source, (
    "update backup exclusions if the temporary file path changes"
)
expected = {("file", "tasks.enc"), ("file", "tasks.enc.tmp")}

modern = ET.parse(ROOT / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
assert modern.tag == "data-extraction-rules"
for section_name in ("cloud-backup", "device-transfer"):
    section = modern.find(section_name)
    assert section is not None, f"missing {section_name} rules"
    exclusions = {
        (element.get("domain"), element.get("path"))
        for element in section.findall("exclude")
    }
    assert expected <= exclusions, f"{section_name} must exclude encrypted task and temp files"

legacy = ET.parse(ROOT / "app/src/main/res/xml/full_backup_content.xml").getroot()
assert legacy.tag == "full-backup-content"
legacy_exclusions = {
    (element.get("domain"), element.get("path"))
    for element in legacy.findall("exclude")
}
assert expected <= legacy_exclusions, "legacy backup rules must exclude encrypted task and temp files"

print("PASS Android backup policy: task ciphertext and save temp file excluded from cloud, D2D, and legacy backup")

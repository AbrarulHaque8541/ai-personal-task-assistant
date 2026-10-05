#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
sh ./tools/run-core-tests.sh
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
assert not list(app.findall("provider")), "V1 must not add providers"

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

updater_dir = main / "java/com/cue/daymark/updater"
updater_sources = "\n".join(path.read_text(encoding="utf-8") for path in updater_dir.glob("*.java"))
release_client = (updater_dir / "GitHubReleaseClient.java").read_text(encoding="utf-8")
updater_core = (updater_dir / "UpdaterCore.java").read_text(encoding="utf-8")
publisher_config = (updater_dir / "UpdaterPublisherConfig.java").read_text(encoding="utf-8")
downloader = (updater_dir / "GitHubApkDownloader.java").read_text(encoding="utf-8")
assert 'https://api.github.com/repos/AbrarulHaque8541/ai-personal-task-assistant/releases/latest' in release_client, "updater endpoint must remain fixed"
assert release_client.count("https://") == 1, "release metadata client must not add other service endpoints"
assert '"Check now"' in activity and "checkForUpdates(false)" in activity and "UpdaterCore.shouldCheck" in activity, "updater checks must remain foreground/manual and rate limited"
assert not manifest.findall("uses-permission"), "no manifest permission is enabled while installer permission scope is pending"
assert not any(permission.get("{http://schemas.android.com/apk/res/android}name") == "android.permission.REQUEST_INSTALL_PACKAGES" for permission in manifest.findall("uses-permission")), "installer permission must not be added without owner approval"
assert "INSTALLATION_ENABLED = false" in publisher_config and 'PUBLISHER_SIGNER_SHA256 = ""' in publisher_config, "publisher installer gate must remain fail-closed"
assert "buildConfig = true" in build and "!BuildConfig.DEBUG" in publisher_config, "updater must be release-only even after signer configuration"
assert "UpdaterCore.isNetworkCheckAllowed(hasInternetPermission(), publisherConfigured)" in activity, "publisher configuration must gate even release-metadata network checks"
assert activity.index("UpdaterCore.isNetworkCheckAllowed") < activity.index("new GitHubReleaseClient()"), "network policy must run before release-client construction"
assert "setInstanceFollowRedirects(false)" in downloader and "isAllowedAssetRedirectUrl" in downloader, "APK redirects must be manually validated"
assert "TaskLogic" not in updater_sources and "EncryptedTaskStore" not in updater_sources, "updater must not depend on or upload task/history data"
assert updater_core.index("if (!consent.accept(release))") < updater_core.index("temporaryApk = downloader.download(release)"), "download must occur only after explicit consent"
assert updater_core.index("verifier.inspect(temporaryApk)") < updater_core.index("handoff.handoff(temporaryApk)"), "handoff must follow APK verification"
assert "WorkManager" not in updater_sources and "JobScheduler" not in updater_sources, "updater must not add background polling"

print("PASS V1 source policy: no manifest permissions/background components/runtime dependencies or optional media/model binaries; updater endpoint and consent gates are fixed")
print("PASS accessibility/localization source checks: scalable text, labeled controls, explicit English-only scope, device-locale dates")
print("PASS permission/updater policy: no new permissions, release-only publisher-gated metadata network, validated release-asset redirects, no unapproved installer handoff, task data isolated")
PY
python3 "$ROOT/tools/check-accessibility-contrast.py"

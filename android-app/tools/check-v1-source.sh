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
play_manifest = ET.parse(root / "app/src/play/AndroidManifest.xml").getroot()
sideload_manifest = ET.parse(root / "app/src/githubSideload/AndroidManifest.xml").getroot()
android_name = "{http://schemas.android.com/apk/res/android}name"
permission_names = lambda manifest_root: {item.get(android_name) for item in manifest_root.findall("uses-permission")}
assert permission_names(manifest) == set(), "shared manifest must remain permission-free"
assert permission_names(play_manifest) == set(), "Play flavor must not declare permissions"
assert permission_names(sideload_manifest) == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"
}, "GitHub sideload may declare only network and connectivity-state permissions"
assert app.get("{http://schemas.android.com/apk/res/android}allowBackup") == "false", "backup must stay disabled"
assert app.get("{http://schemas.android.com/apk/res/android}usesCleartextTraffic") == "false", "cleartext must stay disabled"
assert not list(app.findall("service")), "V1 must not add services"
assert not list(app.findall("receiver")), "V1 must not add receivers"
assert not list(app.findall("provider")), "V1 must not add providers"
play_app = play_manifest.find("application")
sideload_app = sideload_manifest.find("application")
assert play_app is not None and not list(play_app), "Play flavor must not add updater activities or components"
assert sideload_app is not None and not list(sideload_app), \
    "GitHub sideload has no PackageInstaller/status activity; verified APKs are opened manually"

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
release_test = (root / "tools/GitHubTransportSmoke.java").read_text(encoding="utf-8")
assert 'https://api.github.com/repos/AbrarulHaque8541/ai-personal-task-assistant/releases/latest' in release_client, "updater endpoint must remain fixed"
assert release_client.count("https://") == 1, "release metadata client must not add other service endpoints"
assert '"Check now"' in activity and "checkForUpdates(false)" in activity and "UpdaterCore.shouldCheck" in activity, "updater checks must remain foreground/manual and rate limited"
assert 'create("githubSideload")' in build and 'create("play")' in build, "explicit sideload and Play product flavors are required"
assert '"UPDATER_ENABLED", "true"' in build and '"UPDATER_ENABLED", "false"' in build, "only sideload may include the updater"
assert "UPDATER_ENABLED = false" in publisher_config and 'PUBLISHER_SIGNER_SHA256 = ""' in publisher_config, "publisher updater gate and signer pin must remain fail-closed"
assert "BuildConfig.UPDATER_ENABLED" in publisher_config and "!BuildConfig.DEBUG" in publisher_config, "updater must be release-only and sideload-only"
assert "UpdaterCore.isNetworkCheckAllowed(hasInternetPermission(), publisherConfigured)" in activity, "publisher configuration must gate even release-metadata network checks"
assert activity.index("UpdaterCore.isNetworkCheckAllowed") < activity.index("new GitHubReleaseClient()"), "network policy must run before release-client construction"
assert "setInstanceFollowRedirects(false)" in downloader and "isAllowedAssetRedirectUrl" in downloader, "APK redirects must be manually validated"
assert "StrictJsonParser.parse(json)" in release_client and "MAX_RESPONSE_BYTES" in release_client, "release JSON must use the bounded strict parser"
assert "connectionFactory.open" in release_client and "connectionFactory.open" in downloader, "HTTP transports must remain fixture-testable"
for marker in ("parseRelease(fixture", "oversized metadata body", "untrusted APK redirect", "truncated body", "duplicate JSON keys"):
    assert marker in release_test, f"missing updater transport/parser fixture: {marker}"
assert "android.permission.REQUEST_INSTALL_PACKAGES" not in (root / "app/src/githubSideload/AndroidManifest.xml").read_text(encoding="utf-8"), "REQUEST_INSTALL_PACKAGES must remain absent"
assert not any("PackageInstaller" in path.read_text(encoding="utf-8") for path in main.rglob("*.java")), "PackageInstaller handoff must not ship in main source"
assert not list(updater_dir.glob("*PackageInstaller*.java")), "PackageInstaller adapter/status source must be absent"
assert "canRequestPackageInstalls()" not in activity and "ACTION_MANAGE_UNKNOWN_APP_SOURCES" not in activity, "no install-source Settings flow is enabled"
assert '"Download and verify"' in activity and '"Cancel"' in activity and "downloadAndVerify" in activity, "download requires explicit consent and stops after verification"
assert "VerificationStatus.VERIFIED" in activity and "APK verified — not installed" in activity, "successful verification must not imply installation"
assert '"Wi-Fi only (recommended)"' in activity and '"Allow mobile data"' in activity, "download requires a clear network choice with Wi-Fi as default"
assert "NETWORK_POLICY" in updater_core and "isWifiConnected" in downloader, "Wi-Fi-only choice must be enforced before and during transfer"
assert "ACTION_CREATE_DOCUMENT" in activity and "open the saved APK yourself from Files" in activity, "verified APK must be saved for user-directed manual opening"
assert "TaskLogic" not in updater_sources and "EncryptedTaskStore" not in updater_sources, "updater must not depend on or upload task/history data"
assert updater_core.index("if (!consent.accept(release))") < updater_core.index("temporaryApk = downloader.download(release)"), "download must occur only after explicit consent"
assert "uri.getPort() == -1 || uri.getPort() == 443" in updater_core, "release asset URLs must reject non-default HTTPS ports"
assert "github.com:444" in (root / "tools/UpdaterSmoke.java").read_text(encoding="utf-8"), "unusual asset port regression is required"
assert "WorkManager" not in updater_sources and "JobScheduler" not in updater_sources, "updater must not add background polling"

print("PASS V1 source policy: main and Play manifests are permission-free; GitHub sideload has only INTERNET and ACCESS_NETWORK_STATE; no install permission/handoff, background polling, runtime dependencies, or optional binaries")
print("PASS accessibility/localization source checks: scalable text, labeled controls, explicit English-only scope, device-locale dates")
print("PASS permission/updater policy: release-only sideload gates, exact publisher verification, explicit install-choice boundary, Play permission isolation, task data isolated")
PY
python3 "$ROOT/tools/check-accessibility-contrast.py"

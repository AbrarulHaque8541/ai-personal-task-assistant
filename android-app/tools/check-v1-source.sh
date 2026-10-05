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
android = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
app = manifest.find("application")
assert app is not None, "missing application element"
permissions = [item.get(android + "name") for item in manifest.findall("uses-permission")]
assert permissions == ["android.permission.INTERNET"], "only INTERNET may be declared for the browser"
assert app.get(android + "allowBackup") == "false", "backup must stay disabled"
assert app.get(android + "usesCleartextTraffic") == "false", "cleartext must remain disabled in HTTPS-only V1"
assert not list(app.findall("service")), "browser must not add a service"
assert not list(app.findall("receiver")), "browser must not add a receiver"
assert not list(app.findall("provider")), "browser must not add a provider"

main = root / "app/src/main"
for path in main.rglob("*"):
    if path.is_file() and path.suffix.lower() in {".gguf", ".onnx", ".tflite", ".wav", ".mp3", ".flac", ".aab", ".apk"}:
        raise AssertionError(f"unexpected optional/binary asset in source: {path.relative_to(root)}")

build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
deps = build.split("dependencies {", 1)[1].split("}", 1)[0]
assert "implementation(" not in deps and "api(" not in deps and "runtimeOnly(" not in deps, "unexpected app runtime dependency"
activity = (main / "java/com/cue/daymark/MainActivity.java").read_text(encoding="utf-8")
webview = (main / "java/com/cue/daymark/DaymarkWebView.java").read_text(encoding="utf-8")
address = (main / "java/com/cue/daymark/BrowserAddress.java").read_text(encoding="utf-8")
for expected in ("What do you want", "Power path", "DEMO SUGGESTION", "highContrast", "textScale"):
    assert expected in activity, f"missing task source feature marker: {expected}"
for expected in ("Permission status:", "showPermissionStatus()", "PackageManager.GET_PERMISSIONS", "no Android permissions are declared"):
    assert expected in activity, f"missing permission-status behavior: {expected}"
for forbidden in ("requestPermissions(", "ActivityResultContracts.RequestPermission", "registerForActivityResult"):
    assert forbidden not in activity, f"browser must not add runtime permission prompt code: {forbidden}"

add_task = re.search(r"private void addQuickTask\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert add_task and re.search(r"if \(webMode \|\| !storageReady\) return;", add_task.group(1)), "Web mode must never create a task"
navigate = re.search(r"private void navigateFromInput\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert navigate and "if (!webMode) return;" in navigate.group(1), "browser navigation must require Web mode"
set_mode = re.search(r"private void setWebMode\(boolean enabled\)\s*\{(.*?)\n    \}", activity, re.S)
assert set_mode and "taskDraft = quickCaptureInput.getText()" in set_mode.group(1), "task draft should remain local in task mode"
assert "quickCaptureInput.setText(enabled ? \"\" : taskDraft);" in activity, "task draft must not be prefilled into Web mode"
assert "new TextWatcher()" in activity and "if (!webMode) taskDraft" in activity, "Web text must not overwrite the local task draft"
assert "webModeButton.setOnClickListener(view -> setWebMode(true))" in activity
assert "webGoButton.setOnClickListener(view -> navigateFromInput())" in activity
assert "Nothing loads until you tap Go or choose a site." in activity

for expected in (
    "settings.setSafeBrowsingEnabled(true)",
    "WebSettings.MIXED_CONTENT_NEVER_ALLOW",
    "settings.setAllowFileAccess(false)",
    "settings.setAllowContentAccess(false)",
    "settings.setAllowFileAccessFromFileURLs(false)",
    "settings.setAllowUniversalAccessFromFileURLs(false)",
    "settings.setJavaScriptCanOpenWindowsAutomatically(false)",
    "settings.setSupportMultipleWindows(true)",
    "handler.cancel()",
    "request.deny()",
    "callback.invoke(origin, false, false)",
    "listener.onDownloadRequested()",
    "BrowserAddress.isAllowedWebUrl(url)",
    "request.isForMainFrame()",
    "request.isRedirect()",
    "onHttpNavigationBlocked(request.getUrl().toString(), request.isRedirect())",
):
    assert expected in webview, f"missing WebView security boundary: {expected}"
for forbidden in ("addJavascriptInterface(", "shouldInterceptRequest(", "loadUrl(request.getUrl"):
    assert forbidden not in webview, f"unsafe/unrequested WebView bridge or interception found: {forbidden}"

for expected in (
    "DUCKDUCKGO(\"DuckDuckGo\"",
    "GOOGLE(\"Google\"",
    "BING(\"Bing\"",
    "BRAVE(\"Brave Search\"",
    "URLEncoder.encode(query.trim(), \"UTF-8\")",
    "HTTP is blocked. Use HTTPS; a per-site HTTP exception requires a separate explicit request.",
    "Only HTTPS pages are supported. A per-site HTTP exception requires a separate explicit request.",
):
    assert expected in address, f"missing browser input rule: {expected}"
assert "https://" + "example" not in address  # Search/address behavior is covered by executable smoke tests.
assert "ChatGPT" in activity and "Claude" in activity and "Gemini" in activity and "Perplexity" in activity
assert "https://chatgpt.com/" in activity and "https://claude.ai/" in activity
assert "BROWSER_HISTORY_KEY" in activity and "BrowserHistory.add(current, url)" in activity
clear = re.search(r"private void clearBrowserData\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert clear, "explicit browser data clear action is required"
for expected in ("clearHistory()", "clearCache(true)", "WebStorage.getInstance().deleteAllData()", "removeAllCookies("):
    assert expected in clear.group(1), f"clear action must include {expected}"
assert "BrowserHistory.clear()" in clear.group(1), "clear action must erase local history"
assert "History & data" in activity and "Clear history & site data" in activity
assert "HTTP redirect/downgrade was blocked" in activity and "No insecure page was opened" in activity

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

print("PASS task/browser separation: web input does not create tasks or receive task-draft prefill")
print("PASS browser policy: encoded explicit search, HTTPS-only with HTTP/redirect downgrade blocking, no JS bridge/request interceptor")
print("PASS WebView source security: Safe Browsing, mixed-content/file-access restrictions, SSL cancel, site permission denial, pop-up/download handling")
print("PASS local browser data: app-private capped history and explicit history/cookie/cache/WebStorage clear")
print("PASS manifest/dependencies: INTERNET only, no background components, no added runtime dependency or optional media/model binaries")
print("PASS accessibility/localization source checks: scalable text, labeled controls, live status, explicit English-only scope, device-locale dates")
PY
python3 "$ROOT/tools/check-accessibility-contrast.py"

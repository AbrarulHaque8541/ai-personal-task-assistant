#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
sh ./tools/run-core-tests.sh
sh ./tools/run-window-insets-tests.sh
sh ./tools/run-diagnostics-tests.sh
sh ./tools/run-web-mode-tests.sh
sh ./tools/run-attachment-tests.sh
sh ./tools/run-portable-backup-tests.sh
python3 ./tools/check-attachment-source.py "$ROOT"
python3 ./tools/check-schema-v1-fixture.py "$ROOT"
python3 ./tools/check-merged-manifests.py "$ROOT"
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
play_manifest = ET.parse(root / "app/src/play/AndroidManifest.xml").getroot()
sideload_manifest = ET.parse(root / "app/src/githubSideload/AndroidManifest.xml").getroot()
android_name = "{http://schemas.android.com/apk/res/android}name"
permission_names = lambda manifest_root: {item.get(android_name) for item in manifest_root.findall("uses-permission")}
assert permission_names(manifest) == {"android.permission.INTERNET"}, "shared manifest declares only INTERNET for the HTTPS-only browser"
assert permission_names(play_manifest) == set(), "Play flavor must not declare permissions"
assert permission_names(sideload_manifest) == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"
}, "GitHub sideload may declare only network and connectivity-state permissions"
assert app.get("{http://schemas.android.com/apk/res/android}allowBackup") == "false", "backup must stay disabled"
assert app.get("{http://schemas.android.com/apk/res/android}usesCleartextTraffic") == "false", "cleartext must stay disabled"
assert not list(app.findall("service")), "V1 must not add services"
assert not list(app.findall("receiver")), "V1 must not add receivers"
providers = app.findall("provider")
assert len(providers) == 1, "only the grant-only attachment content provider is allowed"
provider = providers[0]
android = "{http://schemas.android.com/apk/res/android}"
assert provider.get(android + "name") == ".AttachmentContentProvider"
assert provider.get(android + "exported") == "false" and provider.get(android + "grantUriPermissions") == "true"
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
sideload_network = (root / "app/src/githubSideload/java/com/cue/daymark/UpdaterNetworkAccess.java").read_text(encoding="utf-8")
play_network = (root / "app/src/play/java/com/cue/daymark/UpdaterNetworkAccess.java").read_text(encoding="utf-8")
assert "UpdaterNetworkAccess.isWifiConnected(getApplicationContext())" in activity, \
    "the updater must use the flavor-specific network-access boundary"
assert "getActiveNetwork()" in sideload_network and "getNetworkCapabilities(" in sideload_network
assert "ConnectivityManager" not in play_network and "getActiveNetwork()" not in play_network
assert re.search(r"static boolean isWifiConnected\(Context context\)\s*\{\s*return false;\s*\}", play_network), \
    "Play updater network access must fail closed without adding a permission"
export_flow = activity.split("private void beginPortableExport", 1)[1].split("private void writePortableExport", 1)[0]
assert "new Intent(Intent.ACTION_CREATE_DOCUMENT)" in export_flow, "portable export must create a new SAF document"
assert "Intent.ACTION_OPEN_DOCUMENT" not in export_flow, "portable export must not select an existing document for overwrite"
codec = (main / "java/com/cue/daymark/PortableBackupCodec.java").read_text(encoding="utf-8")
assert 'Cipher.getInstance("AES/GCM/NoPadding")' in codec and "MAX_ARCHIVE_BYTES" in codec and "MAX_MANIFEST_BYTES" in codec
saved_state = activity.split("protected void onSaveInstanceState", 1)[1].split("protected void onActivityResult", 1)[0]
assert "STATE_PENDING_PORTABLE_IMPORT_URI" in saved_state, "pending portable URI must survive activity recreation"
assert "STATE_PENDING_PORTABLE_IMPORT_TOKEN" in saved_state, "pending import must save its non-secret operation token"
assert "activityStateForSelection" in saved_state, "only a matching live journal selection may enter saved state"
assert "pendingRecoveryKey" not in saved_state, "transient recovery key must never enter saved state"
assert "keyInput.setSaveEnabled(false)" in activity, "recovery-key entry must not be saved by view hierarchy state"
assert "takePersistableUriPermission" in activity
assert "restorePendingPortableImportSelection" in activity and "hasPersistedPortableReadGrant" in activity
assert "PortableImportGrantRecovery.awaitNoActivityRestoreWorker" in activity
assert "reconcileStartupImportUri" in activity, "startup must decide preserve versus cleanup after storage reconciliation"
selection = activity.split("private boolean retainPortableImportUri", 1)[1].split("private PortableImportGrantRecovery.Selection pendingPortableImportSelection", 1)[0]
assert selection.index("recordActivePortableImportUri(uri)") < selection.index("pendingPortableImportUri = uri"), "journal exact URI before exposing selected state or prompting for key"
picker_result = activity.split("protected void onActivityResult", 1)[1].split("private int themeResource", 1)[0]
assert picker_result.index("retainPortableImportUri(data.getData()") < picker_result.index("showPortableImportKeyDialog()"), "journal selected URI before recovery-key prompt"
destroy = activity.split("protected void onDestroy", 1)[1].split("protected void onSaveInstanceState", 1)[0]
assert "if (!portableRestoreWorkerActive)" in destroy
active_restore = activity.split("private void beginPortableRestore", 1)[1].split("private String portableRestoreFailure", 1)[0]
assert "finishActivePortableImportUri(" in active_restore and "selectedOperation" in active_restore
assert "PortableBackupCodec.clear(recoveryKey);" in active_restore and "portableRestoreWorkerActive = false;" in active_restore
assert "finishPortableImportSelection(selected" in activity, "cancel cleanup must reconcile before exact URI release"
manager = (main / "java/com/cue/daymark/PortableBackupManager.java").read_text(encoding="utf-8")
assert "writePending(" in manager and "appendAtomically(additions)" in manager and "reconcile(" in manager
assert "reconcileStartupImportUri" in manager and "reconcileStartup(" in manager
assert "recordTakenGrantOrRelease" in manager and "releaseExactPortableReadGrant" in manager
assert "JOURNAL_MAGIC" in manager and "operationToken" in manager, "URI journal must bind the token and URI"
assert "releasePersistableUriPermission(uri," in manager and "Intent.FLAG_GRANT_READ_URI_PERMISSION" in manager
assert "exactUri.equals(permission.getUri()) && permission.isReadPermission()" in manager
blob_store = (main / "java/com/cue/daymark/AttachmentBlobStore.java").read_text(encoding="utf-8")
attachment_store = (main / "java/com/cue/daymark/AndroidAttachmentStore.java").read_text(encoding="utf-8")
assert "long verifyReadable(String taskId, String id) throws IOException" in blob_store, \
    "payload integrity must be checkable by full AES-GCM authentication, not existence alone"
assert "boolean isReadable(String taskId, String id)" in blob_store, \
    "the fail-closed readable check is required"
assert "long verifyReadable(String taskId, String appOwnedId)" in attachment_store, \
    "the attachment store must expose authenticated verification"
assert "verifyReadable(ownerTaskId, attachmentId)" in manager, \
    "restore reconciliation must authenticate every restored payload before recording the backup as imported"
assert "attachments.exists(attachmentId)" not in manager, \
    "restore reconciliation must not rely on an existence-only check for a trusted payload"
assert "void verifyReferencedPayloads(List<Task> loadedTasks, AndroidAttachmentStore attachments)" in manager, \
    "the manager must authenticate every referenced payload before a snapshot is exposed or exported"
assert "verifyReferencedPayloads(loadedTasks, attachments);" in manager, \
    "startup must authenticate referenced payloads before exposing storage as ready"
preflight = codec.split("private static void preflightManifestAttachmentCount", 1)[1].split("private static void skipManifestString", 1)[0]
assert "totalAttachments > AttachmentLogic.MAX_TOTAL_COUNT - attachmentCount" in preflight
assert codec.index("preflightManifestAttachmentCount(plaintext)") < codec.index("List<PortableTask> tasks = new ArrayList<>(taskCount)")
writer = codec.split("static String writeArchive", 1)[1].split("static VerifiedArchive readArchive", 1)[0]
assert writer.index("countAttachmentDescriptors(tasks)") < writer.index("TaskLogic.isValidTaskList(tasks)")
grant_recovery = (main / "java/com/cue/daymark/PortableImportGrantRecovery.java").read_text(encoding="utf-8")
assert "transaction.reconcile();" in grant_recovery and "releaseAndClear(journal, releaser, journaled);" in grant_recovery
assert "ACTIVE_SELECTIONS" in grant_recovery and "isValidLiveSelection" in grant_recovery
assert "restorePendingActivitySelection" in grant_recovery and "operationToken" in grant_recovery
webview = (main / "java/com/cue/daymark/DaymarkWebView.java").read_text(encoding="utf-8")
address = (main / "java/com/cue/daymark/BrowserAddress.java").read_text(encoding="utf-8")
history_source = (main / "java/com/cue/daymark/BrowserHistory.java").read_text(encoding="utf-8")
browser_smoke = (root / "tools/BrowserAddressSmoke.java").read_text(encoding="utf-8")
settings_policy = (main / "java/com/cue/daymark/BrowserSettingsPolicy.java").read_text(encoding="utf-8")
settings_smoke = (root / "tools/BrowserSettingsPolicySmoke.java").read_text(encoding="utf-8")
for expected in ("What do you want", "Power path", "DEMO SUGGESTION", "highContrast", "textScale"):
    assert expected in activity, f"missing task source feature marker: {expected}"
for expected in ("Permission status:", "showPermissionStatus()", "PackageManager.GET_PERMISSIONS", "no Android permissions are declared"):
    assert expected in activity, f"missing permission-status behavior: {expected}"
for forbidden in ("requestPermissions(", "ActivityResultContracts.RequestPermission", "registerForActivityResult"):
    assert forbidden not in activity, f"browser must not add runtime permission prompt code: {forbidden}"

add_task = re.search(r"private void addQuickTask\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert add_task and re.search(r"if \(webMode \|\| (!storageReady|!canEdit\(\))\) return;", add_task.group(1)), "Web mode must never create a task"
navigate = re.search(r"private void navigateFromInput\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert navigate and "if (!webMode) return;" in navigate.group(1), "browser navigation must require Web mode"
set_mode = re.search(r"private void setWebMode\(boolean enabled\)\s*\{(.*?)\n    \}", activity, re.S)
assert set_mode and "taskDraft = quickCaptureInput.getText()" in set_mode.group(1), "task draft should remain local in task mode"
assert "quickCaptureInput.setText(enabled ? \"\" : taskDraft);" in activity, "task draft must not be prefilled into Web mode"
assert "new TextWatcher()" in activity and "if (!webMode) taskDraft" in activity, "Web text must not overwrite the local task draft"
assert "webModeButton.setOnClickListener(view -> setWebMode(true))" in activity
assert "webGoButton.setOnClickListener(view -> navigateFromInput())" in activity
assert "browserNetworkPolicy = new BrowserNetworkPolicy(readBrowserOnlinePreference());" in activity
assert "BrowserNetworkPolicy.DEFAULT_ONLINE_ENABLED" in activity, "missing/corrupt online preference must default Offline"
assert "getBoolean(BROWSER_ONLINE_ENABLED_KEY," in activity, "browser Online preference must be read locally"
assert "putBoolean(BROWSER_ONLINE_ENABLED_KEY, true)" in activity and "putBoolean(BROWSER_ONLINE_ENABLED_KEY, false)" in activity, "explicit Online choice must persist both states"
assert "Online browsing (off by default)" in activity and "browserOnlineToggle.setOnCheckedChangeListener" in activity
assert "confirmBrowserOnlineAccess()" in activity and "setPositiveButton(\"Enable Online\"" in activity, "Online must require confirmation after its disclosure"
assert "Offline by default" in activity and "each search or site still requires a separate tap" in activity
assert "selected destination receives your query or URL and normal connection data" in activity, "provider/site egress must remain explicit"
assert "may log it" in activity and "may contact and be logged by third-party endpoints" in activity, "provider and page endpoint logging must not be ruled out"
assert "Google/Play Services" in activity and "The Safe Browsing provider itself is not selectable in Daymark" in activity, "Safe Browsing provider and platform traffic must be disclosed separately from its local on/off setting"
assert "URL-hash-based checks" in activity and "WebView M126+ may send a partial URL hash through a proxy" in activity
assert "This does not mean every full URL is sent" in activity and "This is not a claim that every full URL is sent" in activity
assert "Daymark sends no task text and adds no app analytics" in activity
assert "WebView diagnostic metrics are opted out" in activity
assert "The Online switch blocks Daymark page/resource loads only" in activity
assert "does not control Android System WebView Safe Browsing" in activity
assert "no search/site request was sent" in activity, "Offline status must not overpromise absence of platform Safe Browsing traffic"
assert "browserSettingsPolicy = new BrowserSettingsPolicy(readSafeBrowsingPreference());" in activity
assert "BrowserSettingsPolicy.DEFAULT_SAFE_BROWSING_ENABLED" in activity and "getBoolean(SAFE_BROWSING_ENABLED_KEY," in activity
assert "putBoolean(SAFE_BROWSING_ENABLED_KEY, enabled)" in activity, "Safe Browsing choice must be saved locally"
assert "browserSettingsButton.setOnClickListener(view -> showBrowserSettingsDialog())" in activity
assert "setTitle(\"Browser Settings\")" in activity and "Safe Browsing (recommended)" in activity
assert "Disable Safe Browsing?" in activity and "Turning this off reduces protection" in activity
assert 'setPositiveButton("Disable Safe Browsing"' in activity and "setBrowserSafeBrowsingEnabled(false)" in activity
assert "browserWebView.getSettings().setSafeBrowsingEnabled(enabled)" in activity
assert "browserSettingsPolicy.isSafeBrowsingEnabled(), new DaymarkWebView.Listener()" in activity
assert "settings.setSafeBrowsingEnabled(safeBrowsingEnabled)" in webview
assert "DEFAULT_SAFE_BROWSING_ENABLED = true" in settings_policy
assert "Safe Browsing must default on for new installs" in settings_smoke
assert "explicit opt-out should be restorable" in settings_smoke and "user must be able to re-enable" in settings_smoke
assert "Site history keeps only validated HTTPS origins" in activity
assert "Paths, queries, fragments, URL credentials, and page titles are not saved" in activity
assert "Selecting a saved site opens its origin, not its last route" in activity
assert "sanitizeStoredBrowserHistory();" in activity and "BrowserHistory.sanitizeSerialized(existing)" in activity, "legacy history must be sanitized on launch"
assert "BrowserHistory.sanitizeUrl(url)" in activity and "BrowserHistory.add(current, safeHistoryUrl)" in activity, "each history write must pass through the sanitizer"
history_dialog = re.search(r"private void showBrowserHistoryDialog\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert history_dialog and 'setTitle("Site history (HTTPS origins only)")' in history_dialog.group(1)
assert history_dialog and "navigateBrowserTo(history.get(selected))" in history_dialog.group(1), "reopening a saved site must navigate to its origin"
for expected in ("source.getHost()", "source.getPort()", "port != HTTPS_DEFAULT_PORT", "HTTPS_DEFAULT_PORT = 443",
                 "sanitized.getRawUserInfo()", "sanitized.getRawQuery()", "sanitized.getRawFragment()", "sanitized.getRawPath()"):
    assert expected in history_source, f"history sanitizer must handle {expected}"
assert "source.getRawPath()" not in history_source, "site history must not read or persist a page path"
assert "no paths, queries, fragments, userinfo, or titles" in history_source
for secret_case in ("/reset/secret-reset-token", "/oauth/secret-authorization-code", "secret-code",
                    "secret-fragment", "user:password@example.com", "Private password-reset page title with a secret code"):
    assert secret_case in browser_smoke, f"missing origin-history privacy regression: {secret_case}"
assert "BrowserAddress.isAllowedWebUrl(sites.get(0))" in browser_smoke, "a stored site origin must remain reopenable"
assert "active browsing URL" in browser_smoke, "history redaction must not mutate the current route/query"
assert "uri.getRawUserInfo() != null" in address, "browser navigation must continue to reject userinfo"
for method in ("navigateFromInput", "navigateBrowserTo", "loadBrowserAddress"):
    body = re.search(r"private void " + method + r"\([^)]*\)\s*\{(.*?)\n    \}", activity, re.S)
    assert body and "browserNetworkPolicy.allowsRemoteLoads()" in body.group(1), f"{method} must fail closed while Offline"
online_setting = re.search(r"private void setBrowserOnlineEnabled\(boolean enabled\)\s*\{(.*?)\n    \}", activity, re.S)
assert online_setting and "browserNetworkPolicy.setOnlineEnabled(enabled)" in online_setting.group(1)
assert online_setting and "showBrowserHome()" in online_setting.group(1)
assert online_setting and "loadUrl(" not in online_setting.group(1) and "loadBrowserAddress(" not in online_setting.group(1), "enabling Online must not itself load a page"
discard = re.search(r"private void discardBrowserWebView\(boolean stopLoading\)\s*\{(.*?)\n    \}", activity, re.S)
assert discard and "setBlockNetworkLoads(true)" in discard.group(1) and "stopLoading()" in discard.group(1), "offline/background teardown must block and stop the page"
on_pause = re.search(r"protected void onPause\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert on_pause and "discardBrowserWebView()" in on_pause.group(1), "backgrounding must close the page"
ensure_webview = re.search(r"private boolean ensureBrowserWebView\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert ensure_webview and "if (!browserNetworkPolicy.allowsRemoteLoads()) return false;" in ensure_webview.group(1), "WebView construction must fail closed while Offline"
load_address = re.search(r"private void loadBrowserAddress\(String address\)\s*\{(.*?)\n    \}", activity, re.S)
assert load_address and "setBlockNetworkLoads(false)" in load_address.group(1)
assert load_address and load_address.group(1).index("setBlockNetworkLoads(false)") < load_address.group(1).index("browserWebView.loadUrl(address)"), "only a tapped navigation may release WebView network blocking"
assert load_address and "browserWebView.loadUrl(address)" in load_address.group(1), "active browsing must retain the full validated route/query"

for expected in (
    "settings.setBlockNetworkLoads(true)",
    "settings.setSafeBrowsingEnabled(safeBrowsingEnabled)",
    "WebSettings.MIXED_CONTENT_NEVER_ALLOW",
    "settings.setAllowFileAccess(false)",
    "settings.setAllowContentAccess(false)",
    "settings.setAllowFileAccessFromFileURLs(false)",
    "settings.setAllowUniversalAccessFromFileURLs(false)",
    "settings.setJavaScriptCanOpenWindowsAutomatically(false)",
    "settings.setSupportMultipleWindows(BrowserViewportPolicy.SUPPORT_MULTIPLE_WINDOWS)",
    "settings.setUseWideViewPort(BrowserViewportPolicy.USE_WIDE_VIEW_PORT)",
    "settings.setLoadWithOverviewMode(BrowserViewportPolicy.LOAD_WITH_OVERVIEW_MODE)",
    "settings.setSupportZoom(BrowserViewportPolicy.SUPPORT_ZOOM)",
    "settings.setBuiltInZoomControls(BrowserViewportPolicy.BUILT_IN_ZOOM_CONTROLS)",
    "settings.setDisplayZoomControls(BrowserViewportPolicy.DISPLAY_ZOOM_CONTROLS)",
    "BrowserViewportPolicy.allowsSeparateWindow()",
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
for forbidden in ("addJavascriptInterface(", "shouldInterceptRequest(", "loadUrl(request.getUrl",
                 "setSupportMultipleWindows(true)"):
    assert forbidden not in webview, f"unsafe/unrequested WebView bridge or interception found: {forbidden}"
viewport_policy = (main / "java/com/cue/daymark/BrowserViewportPolicy.java").read_text(encoding="utf-8")
viewport_smoke = (root / "tools/BrowserViewportPolicySmoke.java").read_text(encoding="utf-8")
assert "SUPPORT_MULTIPLE_WINDOWS = false" in viewport_policy, \
    "multiple windows must stay disabled so target=_blank result links are not dropped"
assert "USE_WIDE_VIEW_PORT = true" in viewport_policy and "LOAD_WITH_OVERVIEW_MODE = true" in viewport_policy, \
    "desktop result pages must fit the device width"
assert "SUPPORT_ZOOM = true" in viewport_policy, "pinch-zoom must be enabled for long result pages"
assert "target=_blank" in viewport_smoke and "loads in place" in viewport_smoke, \
    "the dropped-result-link regression must be covered by the viewport smoke test"

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
assert "BROWSER_HISTORY_KEY" in activity and "BrowserHistory.add(current, safeHistoryUrl)" in activity
clear = re.search(r"private void clearBrowserData\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert clear, "explicit browser data clear action is required"
for expected in ("clearHistory()", "clearCache(true)", "WebStorage.getInstance().deleteAllData()", "removeAllCookies("):
    assert expected in clear.group(1), f"clear action must include {expected}"
assert "BrowserHistory.clear()" in clear.group(1), "clear action must erase local history"
assert 'compactButton("Site history", false)' in activity and "Clear site history & data" in activity
assert "Site history stores only validated HTTPS origins" in activity and "no paths, queries, fragments, URL credentials, or page titles" in activity
assert "for all websites used in Daymark (not just the current site)" in activity, "clear scope must disclose all-site WebView storage/cookie deletion"
assert "saved Android Autofill or password-manager data is not cleared" in activity, "clear disclosure must not overstate WebView form-data clearing"
assert "browserStatus.setText(\"Clearing Daymark site history and local site data...\")" in clear.group(1)
assert "Daymark site history, WebView cache, Web SQL/HTML5 Storage, and cookies were cleared." in clear.group(1), "completion status must follow asynchronous cookie removal"
assert "HTTP redirect/downgrade was blocked" in activity and "No insecure page was opened" in activity

text_size_calls = re.findall(r"\.setTextSize\(([^)]*)\)", activity)
assert text_size_calls, "expected scalable text controls"
assert all("textScale" in call for call in text_size_calls), "every app text-size call must apply the user's text-size setting"
assert "textSizeMode == 0 ? 0.9f : textSizeMode == 2 ? 1.25f : 1.0f" in activity, "compact/standard/extra-large text choices changed"
for label in ("Search tasks by title", "Clear task search", "Choose low, medium, or high priority", "Edit task:", "Delete task:", "Mark \u201c"):
    assert label in activity, f"missing screen-reader label source: {label}"
assert "setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)" in activity
assert "screen text is English only" in activity, "language limitations must remain explicit"
task_logic = (main / "java/com/cue/daymark/TaskLogic.java").read_text(encoding="utf-8")
assert "Locale.getDefault()" in task_logic, "date formatting should follow the device locale"
store = (main / "java/com/cue/daymark/EncryptedTaskStore.java").read_text(encoding="utf-8")
assert "TaskLogic.isValidTaskList(tasks)" in store, "encrypted writer must reject invalid or duplicate task snapshots"
assert "TaskLogic.isValidTaskList(result)" in store, "encrypted reader must use the same task-list validation contract"
callback_gate = (main / "java/com/cue/daymark/ActivityCallbackGate.java").read_text(encoding="utf-8")
assert "AtomicBoolean" in callback_gate and "if (open.get()) callback.run();" in callback_gate
destroy = activity.split("protected void onDestroy", 1)[1].split("protected void onSaveInstanceState", 1)[0]
assert "activityCallbackGate.close();" in destroy, "Activity destruction must close the callback gate"
assert "private void postActivityCallback(Runnable callback)" in activity
assert "activityCallbackGate.guard(() ->" in activity, "posted UI callbacks must be guarded"
load_callback = activity.split("private void loadEncryptedTasks", 1)[1].split("private void saveTasksAsync", 1)[0]
save_callback = activity.split("private void saveTasksAsync", 1)[1].split("private String storageFailureStatus", 1)[0]
attachment_callback = activity.split("private void importAttachment", 1)[1].split("private void requestAttachmentCancel", 1)[0]
remove_callback = activity.split("private void removeAttachment", 1)[1].split("private Task findTask", 1)[0]
assert "postActivityCallback(() -> {" in load_callback and "postActivityCallback(() -> {" in save_callback
assert attachment_callback.count("postActivityCallback(() -> {") == 2
assert "postActivityCallback(() -> {" in remove_callback
browser_listener = activity.split("new DaymarkWebView.Listener()", 1)[1].split("browserWebView.setBackgroundColor", 1)[0]
assert browser_listener.count("if (!isActivityCallbackCurrent()) return;") == 6
cleanup_callback = activity.split("private void reconcileAndReleasePortableImportSelection", 1)[1].split(
    "private void releasePersistablePortableReadGrant", 1
)[0]
assert "postActivityCallback(() -> {" in cleanup_callback, "portable-import cleanup completion must use the lifecycle gate"
assert "mainHandler.post(" not in cleanup_callback, "portable-import cleanup must not bypass the lifecycle gate"
restore_worker = activity.split("private void beginPortableRestore", 1)[1].split("private String portableRestoreFailure", 1)[0]
restore_completion = restore_worker.split("List<Task> result = restored;", 1)[1].split("            });\n            });", 1)[0]
assert "postActivityCallback(() -> {" in restore_completion, "restore completion must use the lifecycle gate"
assert "activePortableImportUri = null;" in restore_completion
assert "activePortableImportOperationToken = null;" in restore_completion
assert "if (isFinishing() || isDestroyed()) return;" not in restore_completion, \
    "Activity-owned restore state must not be mutated before a late lifecycle check"
save_start = activity.split("private void startVerifiedApkSave", 1)[1].split("private void discardVerifiedUpdate", 1)[0]
assert "postActivityCallback(() -> showSaveFailureChoices" in save_start, \
    "queued APK save-failure UI must use the lifecycle gate"
assert "mainHandler.post(() -> showSaveFailureChoices" not in save_start

updater_dir = main / "java/com/cue/daymark/updater"
updater_sources = "\n".join(path.read_text(encoding="utf-8") for path in updater_dir.glob("*.java"))
release_client = (updater_dir / "GitHubReleaseClient.java").read_text(encoding="utf-8")
updater_core = (updater_dir / "UpdaterCore.java").read_text(encoding="utf-8")
publisher_config = (updater_dir / "UpdaterPublisherConfig.java").read_text(encoding="utf-8")
downloader = (updater_dir / "GitHubApkDownloader.java").read_text(encoding="utf-8")
saf_saver = (updater_dir / "SafApkSaver.java").read_text(encoding="utf-8")
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
for marker in ("parseRelease(fixture", "oversized metadata body", "untrusted APK redirect", "truncated body", "duplicate JSON keys", "productionUpdaterAcceptsExactly100MiBAndRejectsOneByteOver", "promotedCandidateIsRecoveredAndRevalidatedAfterRestart", "pickerSaveTransactionSurvivesRecreationBeforeResult", "safSaveFinalizesAfterIndependentReadBack", "interruptedSafCopyLeavesClearlyMarkedPartialDocument", "interruptedSafCopyIoFailureCleansOnlyCreatedDocument", "safFinalizationCollisionPreservesUnrelatedDocument"):
    assert marker in release_test, f"missing updater transport/parser fixture: {marker}"
assert ".daymark-incomplete-" in saf_saver, "SAF staging names must make incomplete copies conspicuous"
assert saf_saver.index("renameTo(incompleteName)") < saf_saver.index("destination.openForWrite()") < saf_saver.index("destination.openForRead()") < saf_saver.index("renameTo(finalName)"), \
    "SAF destinations must be staged before writing, independently read back, then finalized"
assert "DocumentsContract.renameDocument" in activity and "openOutputStream(documentUri, \"w\")" in activity \
    and "openInputStream(documentUri)" in activity, \
    "Android SAF operations must remain scoped to the picker-created URI"
assert "outState.putSerializable(PENDING_SAVE_STATE_KEY, pendingSaveTransaction)" in activity \
    and "savedInstanceState.getSerializable(PENDING_SAVE_STATE_KEY)" in activity, \
    "an Activity recreation while the picker is open must restore its pending transaction"
assert "revalidatePendingSaveTransaction(transaction" in activity and "UpdaterCore.verifyDownloadedArtifact" in activity, \
    "a restored picker callback must revalidate the app-private verified source before copying"
assert "cleanStalePickerResult(data.getData())" in activity \
    and "DocumentsContract.renameDocument(getContentResolver(), cleanupUri, orphanName)" in activity \
    and "DocumentsContract.deleteDocument(getContentResolver(), cleanupUri)" in activity, \
    "stale picker cleanup must mark and delete only the exact URI returned by ACTION_CREATE_DOCUMENT"
assert "Files.write(verifiedTemp.toPath(), APK)" not in release_test, \
    "restart recovery coverage must not fabricate a digest-addressed verified artifact"
assert "android.permission.REQUEST_INSTALL_PACKAGES" not in (root / "app/src/githubSideload/AndroidManifest.xml").read_text(encoding="utf-8"), "REQUEST_INSTALL_PACKAGES must remain absent"
assert not any("PackageInstaller" in path.read_text(encoding="utf-8") for path in main.rglob("*.java")), "PackageInstaller handoff must not ship in main source"
assert not list(updater_dir.glob("*PackageInstaller*.java")), "PackageInstaller adapter/status source must be absent"
assert "canRequestPackageInstalls()" not in activity and "ACTION_MANAGE_UNKNOWN_APP_SOURCES" not in activity, "no install-source Settings flow is enabled"
assert '"Download and verify"' in activity and '"Cancel"' in activity and "downloadAndVerify" in activity, "download requires explicit consent and stops after verification"
assert "VerificationStatus.VERIFIED" in activity and "APK verified \u2014 not installed" in activity, "successful verification must not imply installation"
assert '"Wi-Fi only (recommended)"' in activity and '"Allow mobile data"' in activity, "download requires a clear network choice with Wi-Fi as default"
assert "NETWORK_POLICY" in updater_core and "isWifiConnected" in downloader, "Wi-Fi-only choice must be enforced before and during transfer"
assert "ACTION_CREATE_DOCUMENT" in activity and "open the saved copy yourself from Files" in activity, "verified APK must be saved for user-directed manual opening"
recovery_store = (updater_dir / "UpdaterRecoveryStore.java").read_text(encoding="utf-8")
assert "updaterRecoveryStore.recordPending(release)" in activity and "UpdaterCore.verifyDownloadedArtifact" in activity, "restart recovery must persist expectations before download and revalidate before offering"
assert "removeAfterUserChoice(release)" in activity and "verifiedApk.delete()" not in activity, "verified cache APKs must not be implicitly deleted"
assert "clearIfNoVerifiedArtifact" in recovery_store and "verifiedArtifactFileName" in recovery_store, "recovery record cleanup must preserve any promoted artifact"
assert downloader.count("getNoBackupFilesDir()") == 2 and "getCacheDir()" not in downloader, "updater staging and cleanup must avoid evictable cache storage"
assert "TaskLogic" not in updater_sources and "EncryptedTaskStore" not in updater_sources, "updater must not depend on or upload task/history data"
assert updater_core.index("if (!consent.accept(release))") < updater_core.index("temporaryApk = downloader.download(release)"), "download must occur only after explicit consent"
assert "uri.getPort() == -1 || uri.getPort() == 443" in updater_core, "release asset URLs must reject non-default HTTPS ports"
assert "github.com:444" in (root / "tools/UpdaterSmoke.java").read_text(encoding="utf-8"), "unusual asset port regression is required"
assert "WorkManager" not in updater_sources and "JobScheduler" not in updater_sources, "updater must not add background polling"

print("PASS V1 source policy: main declares INTERNET for browser; Play inherits INTERNET without extra permissions; GitHub sideload adds ACCESS_NETWORK_STATE; no install permission/handoff, background polling, runtime dependencies, or optional binaries")
print("PASS accessibility/localization source checks: scalable text, labeled controls, explicit English-only scope, device-locale dates")
print("PASS permission/updater policy: release-only sideload gates, exact publisher verification, explicit install-choice boundary, Play permission isolation, task data isolated")
print("PASS V1 source policy: no permissions/network, background components or runtime dependencies; only the non-exported grant-only attachment provider; no optional media/model binaries")
print("PASS accessibility/localization source checks: scalable text, labeled controls, live status, explicit English-only scope, device-locale dates")
print("PASS permission policy: manifest-backed status only; no runtime permission prompt code")
print("PASS encrypted task-store policy: writer and reader share invalid/duplicate-task rejection")
print("PASS lifecycle policy: task/storage/attachment/browser callbacks are suppressed after Activity destruction")
print("PASS portable backup policy: bounded AES-GCM format, SAF create-only export, snapshot-last restore journal")
print("PASS task/browser separation: web input does not create tasks or receive task-draft prefill")
print("PASS browser policy: Offline by default with persisted opt-in, Daymark page/resource loads blocked while Offline, and a separate tap required for each request")
print("PASS browser policy: encoded explicit search, HTTPS-only with HTTP/redirect downgrade blocking, no JS bridge/request interceptor")
print("PASS browser settings: Safe Browsing defaults on, confirmed opt-out persists, and current/future WebViews track the preference")
print("PASS WebView source security: Safe Browsing, mixed-content/file-access restrictions, SSL cancel, site permission denial, pop-up/download handling")
print("PASS local browser data: capped origin-only site history, legacy-origin migration, reopen/dedup, secret redaction, and explicit history/cookie/cache/WebStorage clear")
print("PASS manifest/dependencies: INTERNET only, no background components, no added runtime dependency or optional media/model binaries")
print("PASS accessibility/localization source checks: scalable text, labeled controls, live status, explicit English-only scope, device-locale dates")
PY
python3 "$ROOT/tools/check-accessibility-contrast.py"

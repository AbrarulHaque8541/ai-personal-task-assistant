#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
sh ./tools/run-core-tests.sh
sh ./tools/run-activity-request-code-tests.sh
sh ./tools/run-portable-export-tests.sh
sh ./tools/run-window-insets-tests.sh
sh ./tools/run-attachment-tests.sh
sh ./tools/run-portable-backup-tests.sh
sh ./tools/run-portable-staging-tests.sh
sh ./tools/run-updater-picker-routing-tests.sh
sh ./tools/run-diagnostics-tests.sh
sh ./tools/run-web-mode-tests.sh
sh ./tools/run-text-scale-tests.sh
sh ./tools/run-check-guard-tests.sh
python3 ./tools/check-attachment-source.py "$ROOT"
python3 ./tools/check-browser-catalog.py "$ROOT"
python3 ./tools/check-extension-trust-confirmation.py "$ROOT"
python3 ./tools/check-browser-image-policy.py "$ROOT"
python3 ./tools/check-reader-mode.py "$ROOT"
python3 ./tools/check-release-updater-metadata.py "$ROOT"
python3 ./tools/check-schema-v1-fixture.py "$ROOT"
bash ./tools/check-release-identity.sh
python3 ./tools/check-merged-manifests.py "$ROOT"
python3 "$ROOT/tools/check-slsa-workflow.py"
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
sideload_network = (root / "app/src/githubSideload/java/com/cue/daymark/UpdaterNet
workAccess.java").read_text(encoding="utf-8")
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
assert "takePersistableUriPermiss
ion" in activity
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
assert "JOURNAL_MA
GIC" in manager and "operationToken" in manager, "URI journal must bind the token and URI"
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
assert codec.index("preflightManifestAttachmentCount(plaintext)") < codec.index("List<PortableTask> tasks = new 
ArrayList<>(taskCount)")
writer = codec.split("static String writeArchive", 1)[1].split("static VerifiedArchive readArchive", 1)[0]
assert writer.index("countAttachmentDescriptors(tasks)") < writer.index("TaskLogic.isValidTaskList(tasks)")
grant_recovery = (main / "java/com/cue/daymark/PortableImportGrantRecovery.java").read_text(encoding="utf-8")
assert "transaction.reconcile();" in grant_recovery and "releaseAndClear(journal, releaser, journaled);" in grant_recovery
assert "ACTIVE_SELECTIONS" in grant_recovery and "isValidLiveSelection" in grant_recovery
assert "restorePendingActivitySelection" in grant_recovery and "operationToken" in grant_recovery
webview = (main / "java/com/cue/daymark/DaymarkWebView.java").read_text(encoding="utf-8")
media_policy = (main / "java/com/cue/daymark/BrowserMediaPolicy.java").read_text(encoding="utf-8")
tab_policy = (main / "java/com/cue/daymark/BrowserTabPolicy.java").read_text(encoding="utf-8")
media_smoke = (root / "tools/BrowserMediaPolicySmoke.java").read_text(encoding="utf-8")
tab_smoke = (root / "tools/BrowserTabPolicySmoke.java").read_text(encoding="utf-8")
address = (main / "java/com/cue/daymark/BrowserAddress.java").read_text(encoding="utf-8")
history_source = (main / "java/com/cue/daymark/BrowserHistory.java").read_text(encoding="utf-8")
browser_smoke = (root / "tools/BrowserAddressSmoke.java").read_text(encoding="utf-8")
settings_policy = (main / "java/com/cue/daymark/BrowserSettingsPolicy.java").read_text(encoding="utf-8")
settings_smoke = (root / "tools/BrowserSettingsPolicySmoke.java").read_text(encoding="utf-8")
for expected in ("What do you want", "Power path", "DEMO SUGGESTION", "highContrast", "textScale"):
    assert expected in activity, f"missing task source feature marker: {expected}"
for expected in ('addSettingsRow(content, "Permission status"', "showPermissionStatus()", "PackageManager.GET_PERMISSIONS", "no Android permissions are declared"):
    assert expected in activity, f"missing permission-status beh
avior: {expected}"
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
assert "BrowserNetworkPolicy.DEFAULT_ONLINE_ENABLED" in activity, "browser network default must be explicit"
assert "browserPreferences.edit().putBoolean(BROWSER_ONLINE_ENABLED_KEY, true).apply();" in activity, "legacy Offline preference must migrate to enabled"
assert "browserPreferences.edit().remove(BROWSER_ONLINE_ENABLED_KEY).apply();" in activity, "runtime failure must not persist a hidden Offline state"
assert "putBoolean(BROWSER_ONLINE_ENABLED_KEY, true)" in activity and "putBoolean(BROWSER_ONLINE_ENABLED_KEY, false)" not in activity, "the removed Offli
ne control must not persist a hidden disabled state"
assert "browserOnlineToggle = null;" in activity and "Internet access is requested when you search or open a site." in activity, "browser UI must not expose an Online/Offline toggle"
assert "new CheckBox(this)" not in activity[activity.index("private View buildBrowserAddressBar()"):activity.index("private void navigateFromBrowserInput()")], "address bar must not create an Online/Offline checkbox"
assert "confirmBrowserOnlineAccess(() -> loadBrowserAddress(safeAddress))" in activity, "first navigation should continue after explicit network consent"
assert "confirmBrowserOnlineAccess()" in activity and "setPositiveButton(\"Enable Online\"" in activity, "Online must require confirmation after its disclosure"
assert "private String browserLastSearchQuery = \"\";" in activity, "browser should retain the last submitted query for provider switching"
assert "panel.addView(browserProviderRow, bottomMargin(dp(3)));" in activity, "provider shortcuts should be positioned after the weighted WebView as a bottom strip"
assert "browserLastSearchQuery" in activity and "selectSearchEngine(entry.searchEngine)" in activity, "provider shortcuts should reuse the previous query with the selected provider"
assert 'compactButton("Media ↓", false)' in activity and "e.currentSrc" in activity and "e.videoWidth+'×'+e.videoHeight" in activity, "media discovery should expose direct sources and available video dimensions"
assert "injectVideoDownloadOverlay(browserWebView);" in activity and "private void injectVideoDownloadOverlay(DaymarkWebView target)" in activity, "page finish should install the best-effort in-player download affordance"
overlay = re.search(r"private void injectVideoDownloadOverlay\(DaymarkWebView target\)\s*\{(.*?)\n    \}", activity, re.S)
assert overlay and "window.__daymarkVideoDownloadInstalled" in overlay.group(1) and "button.textContent='Download'" in overlay.group(1), "video overlay must be idempotent and visibly user-
triggered"
assert overlay and "target.evaluateJavascript(script, null)" in overlay.group(1) and "addEventListener('click'" in overlay.group(1), "video overlay must use a user click and avoid native JS bridges"
assert "DRM/manifest/blob extraction" in activity, "video overlay limitations must be explicit in source"
assert "browserReaderActionRow.setVisibility(available && hasPage ? View.VISIBLE : View.GONE);" in activity, "Reader Mode should appear only when a page is loaded"
assert "browserTabs = new ArrayList<>()" in activity and "showBrowserTabsDialog()" in activity, "browser must expose a tab switcher"
assert "createBrowserTab()" in activity and "switchBrowserTab(browserTabs.get(index))" in activity, "browser must create and switch tabs"
assert 'compactButton("+", false)' in activity and "requestNewBrowserTab()" in activity, "toolbar New Tab action must preserve other open tabs"
assert "if (webMode && browserTabs.size() > 1)" in activity and "closeCurrentBrowserTab()" in activity, "Android Back should close the current tab after its page history is exhausted"
assert "browserTabs.remove(current)" in activity and "destroyBrowserWebView(current, stopLoading)" in activity, "closing tabs must stop and destroy only the intended WebView"
assert "targetRef[0] != browserWebView" in activity, "inactive tabs must not overwrite the active tab UI"
assert "daymark-download://media?url=" in activity, "in-player download button must hand direct media to the native download pipeline"
assert "window.location.href='daymark-download://media?url='" in activity, "overlay click must use a bounded custom-scheme handoff rather than a fake HTML download"
assert "onMediaDownloadRequested(String url)" in webview and "request.hasGesture()" in webview, "media handoff must require a user gesture"
assert "BrowserMediaPolicy.allowsHandoff" in webview and "BrowserAddress.isAllowedWebUrl(mediaUrl)" in media_policy, "media handoff must centralize HTTPS validation"
for expected in ("offline mode mus
t block media handoff", "media handoff must require a user gesture", "cleartext media must be rejected"):
    assert expected in media_smoke, f"missing executable media-policy regression: {expected}"
assert "BrowserTabPolicy.MAX_TABS = 6" in tab_policy or "MAX_TABS = 6" in tab_policy, "browser tabs must have a memory-conscious limit"
for expected in ("tab count must be bounded", "closing the last tab should leave no selection", "selection must remain within the remaining tab list"):
    assert expected in tab_smoke, f"missing executable tab-policy regression: {expected}"
assert '"daymark-download".equalsIgnoreCase(request.getUrl().getScheme())' in webview, "custom media handoff must be intercepted before normal web navigation"
assert "onShowCustomView(View view, CustomViewCallback callback)" in webview and "onHideCustomView()" in webview, "WebView must support HTML5 full-screen video"
assert "onShowFullscreen(View view, WebChromeClient.CustomViewCallback callback)" in activity and "FLAG_FULLSCREEN" in activity, "host Activity must display full-screen media and restore the system UI"
assert "Browsing starts ready for explicit searches and sites" in activity and "tap Go or a provider shortcut" in activity
assert "selected destination receives your query or URL and normal connection data" in activity, "provider/site egress must remain explicit"
assert "may log it" in activity and "may contact and be logged by third-party endpoints" in activity, "provider and page endpoint logging must not be ruled out"
assert "Google/Play Services" in activity and "The Safe Browsing provider itself is not selectable in Daymark" in activity, "Safe Browsing provider and platform traffic must be disclosed separately from its local on/off setting"
assert "URL-hash-based checks" in activity and "WebView M126 and later may send a partial URL hash through a proxy" in activity
assert "This does not mean every full URL is sent" in activity
assert "Daymark sends no task text" in activity and "ad
ds no app analytics" in activity
assert "WebView diagnostic metrics are opted out" in activity
assert "Daymark's browser network policy controls page/resource loads only" in activity
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
# Browser home now keeps privacy copy compact and opens the full disclosure on demand.
# Keep asserting the complete disclosure text without depending on the old always-visible paragraph layout.
privacy_disclosure = re.search(r'String disclosureText\s*=\s*"(.*?)";', activity, re.S)
assert privacy_disclosure, "browser home must retain an
 explicit privacy disclosure"
disclosure_text = privacy_disclosure.group(1)
for expected in (
    "Site history keeps only validated HTTPS origins",
    "Paths, queries, fragments, URL credentials, and page titles are not saved",
    "Selecting a saved site opens its origin, not its last route",
):
    assert expected in disclosure_text, f"privacy disclosure must cover: {expected}"
assert 'text("ⓘ Privacy & connection details"' in activity, "full privacy details must be available from a compact home row"
assert 'showInfo("Privacy & connection details", disclosureText)' in activity, "privacy details must open on tap"
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
                    "secret-fragment", "user:password@example.com", "Private p
assword-reset page title with a secret code"):
    assert secret_case in browser_smoke, f"missing origin-history privacy regression: {secret_case}"
assert "BrowserAddress.isAllowedWebUrl(sites.get(0))" in browser_smoke, "a stored site origin must remain reopenable"
assert "active browsing URL" in browser_smoke, "history redaction must not mutate the current route/query"
assert "uri.getRawUserInfo() != null" in address, "browser navigation must continue to reject userinfo"
# Input handlers route through navigateBrowserTo, which requests explicit consent when
# the persisted network choice is Offline. The final load boundary still fails closed.
for method in ("navigateFromInput", "navigateFromBrowserInput"):
    body = re.search(r"private void " + method + r"\([^)]*\)\s*\{(.*?)\n    \}", activity, re.S)
    assert body and "navigateBrowserTo(address)" in body.group(1), f"{method} must use the validated browser navigation path"
navigate_browser = re.search(r"private void navigateBrowserTo\(String address\)\s*\{(.*?)\n    \}", activity, re.S)
assert navigate_browser and "confirmBrowserOnlineAccess(() -> loadBrowserAddress(safeAddress))" in navigate_browser.group(1), "first navigation from Offline must request consent then continue the requested page"
load_address = re.search(r"private void loadBrowserAddress\(String address\)\s*\{(.*?)\n    \}", activity, re.S)
assert load_address and "browserNetworkPolicy.allowsRemoteLoads()" in load_address.group(1), "final page-load boundary must still fail closed while Offline"
online_setting = re.search(r"private void setBrowserOnlineEnabled\(boolean enabled\)\s*\{(.*?)\n    \}", activity, re.S)
assert online_setting and "browserNetworkPolicy.setOnlineEnabled(enabled)" in online_setting.group(1)
assert online_setting and "showBrowserHome()" in online_setting.group(1)
assert online_setting and "loadUrl(" not in online_setting.group(1) and "loadBrowserAddress(" not in online_setting.group(1), "enabling Online must not itself load a p
age"
discard = re.search(r"private void discardBrowserWebView\(boolean stopLoading\)\s*\{(.*?)\n    \}", activity, re.S)
destroy_webview = re.search(r"private void destroyBrowserWebView\(DaymarkWebView current, boolean stopLoading\)\s*\{(.*?)\n    \}", activity, re.S)
assert discard and "destroyBrowserWebView(current, stopLoading)" in discard.group(1), "tab teardown must route through the common WebView destroy path"
assert destroy_webview and "setBlockNetworkLoads(true)" in destroy_webview.group(1) and "stopLoading()" in destroy_webview.group(1), "offline/background teardown must block and stop each closed page"
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
    "settings.setAllowUniversalAccessFromFileURLs(fals
e)",
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
    "listener.onDownloadRequested(url, userAgent, contentDisposition, mimeType, contentLength)",
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
assert "target=_blank" in viewport_smoke and "loads in 
place" in viewport_smoke, \
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
assert "AiSiteCatalog.entries()" in activity, \
    "browser AI shortcuts must be built from the shared catalog, not duplicated inline"
ai_catalog = (main / "java/com/cue/daymark/AiSiteCatalog.java").read_text(encoding="utf-8")
assert "ChatGPT" in ai_catalog and "Claude" in ai_catalog and "Gemini" in ai_catalog and "Perplexity" in ai_catalog
assert "https://chatgpt.com/" in ai_catalog and "https://claude.ai/" in ai_catalog
assert "new Entry(\"Mistral Le Chat\"" in ai_catalog and "new Entry(\"Duck.ai\"" in ai_catalog
assert "new Entry(\"Qwen Chat\"" in ai_catalog and "new Entry(\"Character AI\"" in ai_catalog
assert "browserSiteButton(AiSiteCatalog.Entry entry)" in activity, "AI shortcuts must use the shared catalog entries"
assert "BrowserAddress.isLikelyWebAddress(value)" in activity, "provider taps must not mistake a typed URL for a question"
assert "entry.searchEngine.searchUrl(value)" in activity, "provider tap must submit the current query without a separate Go tap"
assert "static boolean isLikelyWebAddress(String value)" in address, "provider tap must distinguish a query from a web address"
assert "BROWSER_HISTORY_KEY" in activity and "BrowserHistory.add(current, safeHistoryUrl)" in activity
clear = re.search(r"private void clearBrowserData\(\)\s*\{(.*?)\n    \}", activity, re.S)
assert clear, 
"explicit browser data clear action is required"
for expected in ("clearHistory()", "clearCache(true)", "WebStorage.getInstance().deleteAllData()", "removeAllCookies("):
    assert expected in clear.group(1), f"clear action must include {expected}"
assert "BrowserHistory.clear()" in clear.group(1), "clear action must erase local history"
assert 'compactButton("Site history", false)' in activity and "Clear site history & data" in activity
assert "Site history stores only validated HTTPS origins" in activity and "no paths, queries, fragments, URL credentials, or page titles" in activity
assert "for all websites used in Daymark (not just the current site)" in activity, "clear scope must disclose all-site WebView storage/cookie deletion"
assert "

... [Content truncated]
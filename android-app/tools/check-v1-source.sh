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
assert permissions == [
    "android.permission.INTERNET",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.SCHEDULE_EXACT_ALARM",
], "only browser and user-created local-reminder permissions may be declared"
assert app.get(android + "allowBackup") == "false", "backup must stay disabled"
assert app.get(android + "usesCleartextTraffic") == "false", "cleartext must remain disabled in HTTPS-only V1"
assert any(item.get(android + "name") == "android.webkit.WebView.MetricsOptOut"
           and item.get(android + "value") == "true" for item in app.findall("meta-data")), "WebView diagnostic metrics must be opted out"
assert not list(app.findall("service")), "browser must not add a service"
receivers = app.findall("receiver")
assert len(receivers) == 1, "only the private local-reminder receiver may be declared"
receiver = receivers[0]
assert receiver.get(android + "name") == ".ReminderReceiver" and receiver.get(android + "exported") == "false"
receiver_actions = {item.get(android + "name") for item in receiver.findall("intent-filter/action")}
assert {
    "android.intent.action.BOOT_COMPLETED",
    "android.intent.action.TIMEZONE_CHANGED",
    "android.intent.action.TIME_CHANGED",
    "com.cue.daymark.action.REMINDER_FIRE",
    "com.cue.daymark.action.REMINDER_FALLBACK",
    "com.cue.daymark.action.REMINDER_SNOOZE",
    "com.cue.daymark.action.REMINDER_CANCEL",
}.issubset(receiver_actions), "reminder receiver must restore and handle only scoped local alarm actions"
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
history_source = (main / "java/com/cue/daymark/BrowserHistory.java").read_text(encoding="utf-8")
browser_smoke = (root / "tools/BrowserAddressSmoke.java").read_text(encoding="utf-8")
settings_policy = (main / "java/com/cue/daymark/BrowserSettingsPolicy.java").read_text(encoding="utf-8")
settings_smoke = (root / "tools/BrowserSettingsPolicySmoke.java").read_text(encoding="utf-8")
for expected in ("What do you want", "Power path", "DEMO SUGGESTION", "highContrast", "textScale"):
    assert expected in activity, f"missing task source feature marker: {expected}"
for expected in ("Permission status:", "showPermissionStatus()", "PackageManager.GET_PERMISSIONS", "POST_NOTIFICATIONS is requested only when you save a reminder"):
    assert expected in activity, f"missing permission-status behavior: {expected}"
assert "requestPermissions(" in activity and "Manifest.permission.POST_NOTIFICATIONS" in activity
assert "ACTION_RINGTONE_PICKER" in activity and "Use exact reminder timing?" in activity
assert "shouldOfferExactAccess(Build.VERSION.SDK_INT" in activity, "exact access must be offered only for a user-created reminder"
assert "ReminderScheduler.canScheduleExactAlarms(this)" in activity, "exact-alarm access must be checked"
assert "lastKnownNotificationAccess" in activity, "notification settings changes must be rechecked on resume"
assert "dueReminderNeedsRearm" in activity, "overdue reminders must be recovered after a missed notification-permission transition"
assert "if (persisted)" in activity and "reminderStore.put(previous)" in activity, "a failed replacement must restore the previous encrypted reminder"
for forbidden in ("ActivityResultContracts.RequestPermission", "registerForActivityResult"):
    assert forbidden not in activity, f"unexpected runtime-permission framework dependency: {forbidden}"

reminder = (main / "java/com/cue/daymark/Reminder.java").read_text(encoding="utf-8")
reminder_logic = (main / "java/com/cue/daymark/ReminderLogic.java").read_text(encoding="utf-8")
reminder_store = (main / "java/com/cue/daymark/EncryptedReminderStore.java").read_text(encoding="utf-8")
reminder_scheduler = (main / "java/com/cue/daymark/ReminderScheduler.java").read_text(encoding="utf-8")
reminder_receiver = (main / "java/com/cue/daymark/ReminderReceiver.java").read_text(encoding="utf-8")
for expected in ("MODE_LOCAL_DATE_TIME", "MODE_TIMER", "soundUri", "triggerAtMillis", "zoneId", "offsetSeconds", "deliveryPending"):
    assert expected in reminder, f"reminder record must include validated {expected} state"
for expected in ("resolveLocalDateTime", "absoluteEpochMillis", "afterTimezoneChange", "shouldRestoreAfterReboot", "deliveryRecoveryAction", "beginDelivery", "completeDelivery", "revocationFallbackAtMillis", "snooze", "removeForTask", "MIN_IDLE_ALARM_INTERVAL_MILLIS", "needsInexactRevocationFallback"):
    assert expected in reminder_logic, f"missing testable reminder rule: {expected}"
for expected in ("AES/GCM/NoPadding", "AndroidKeyStore", "updateAAD(MAGIC)", "reminders.enc", "synchronized (FILE_LOCK)", "markDeliveryPending", "markDelivered", "migrateLegacyReminder", "document.put(\"version\", 2)"):
    assert expected in reminder_store, f"reminder storage must preserve encryption/serialization protection: {expected}"
for expected in ("canScheduleExactAlarms()", "setExactAndAllowWhileIdle", "setAndAllowWhileIdle", "fallbackPendingIntent", "manager.cancel(fallback)", "getNotificationChannel(id)", "createNotificationChannel(channel)"):
    assert expected in reminder_scheduler, f"missing alarm/channel safety boundary: {expected}"
assert "if (enabled) schedule(context, reminder)" in reminder_scheduler and "else cancel(context, reminder.taskId)" in reminder_scheduler, "notification revocation must cancel alarms without deleting active reminder data"
assert "setSound(sound, attributes)" in reminder_scheduler
assert reminder_scheduler.index("getNotificationChannel(id) != null") < reminder_scheduler.index("createNotificationChannel(channel)"), "existing channel settings must never be rewritten"
for expected in ("ACTION_BOOT_COMPLETED", "ACTION_TIMEZONE_CHANGED", "ACTION_TIME_CHANGED", "ACTION_FALLBACK", "ACTION_SNOOZE", "ACTION_CANCEL", "markDeliveryPending", "markDelivered", "reconcilePendingNotifications"):
    assert expected in reminder_receiver or expected in reminder_receiver.replace("Intent.", ""), f"missing receiver action: {expected}"
assert "goAsync()" in reminder_receiver and "setExact" not in reminder_receiver
assert "shouldDeliverForTask(pending, task)" in reminder_receiver and "belongsToOpenTask(current, task)" in reminder_receiver, "fire and snooze actions must verify the task is still open"
assert "reconcileAndReschedule(context, store)" in reminder_receiver, "boot/time/permission restoration must reconcile reminders against current tasks"
assert "if (task == null || task.completed)" in activity, "startup reconciliation must remove reminders for deleted or completed tasks"
assert "saveTasksAsync(() ->" in activity and "cancelReminderAsync(task.id, false)" in activity, "task completion/deletion must cancel reminders only after a successful task save"
assert "restoreReminderAsync(restore)" in activity, "undo must restore a reminder only after the task save succeeds"
delivery_flow = reminder_receiver.split("private void deliverIfCurrent", 1)[1].split("private boolean isSystemRescheduleAction", 1)[0]
assert delivery_flow.index("store.markDeliveryPending(taskId)") < delivery_flow.rindex("postPendingNotification(context, store, taskId)"), "persist retryable pending state before attempting the notification post"
pending_flow = reminder_receiver.split("private void postPendingNotification", 1)[1].split("private Task findTask", 1)[0]
assert pending_flow.index("if (!postNotification(context, pending)) return;") < pending_flow.index("store.markDelivered(taskId)"), "do not mark delivered until Android accepts a notification post"
assert "manager.notify(ReminderScheduler.notificationTag(reminder.taskId)" in reminder_receiver, "restart retries must replace the same stable tagged notification"
assert "manager.cancel(notificationTag(taskId), notificationId(taskId))" in reminder_scheduler, "cancellation must target the stable notification identity"
assert "reconcilePendingNotifications(getApplicationContext(), reminderStore" in activity, "app start/resume must reconcile durable pending posts"
for expected in ("showOverlapOccurrenceChoice", "Time doesn't exist", "will not shift it automatically", "Time zone:", "choiceLabel"):
    assert expected in activity, f"missing explicit DST UX: {expected}"
reminder_smoke = (root / "tools/ReminderLogicSmoke.java").read_text(encoding="utf-8")
for expected in ("dstGapsAreRejectedAndBothOverlapOffsetsMatchTheirPreview", "absoluteReminderBoundsAreEpochMillisBoundsWithoutShortHorizon", "notificationPostingSurvivesEveryCrashWindow", "Long.MAX_VALUE"):
    assert expected in reminder_smoke, f"missing focused reminder regression test: {expected}"
assert "<uses-permission android:name=\"android.permission.USE_EXACT_ALARM\"" not in (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for blanket in ("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WAKE_LOCK", "android.permission.FOREGROUND_SERVICE"):
    assert blanket not in (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8"), f"unexpected broad permission: {blanket}"
build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
assert "compileSdk = 35" in build and "targetSdk = 35" in build, "do not move this branch beyond the existing API 35 toolchain"

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
for label in ("Search tasks by title", "Clear task search", "Choose low, medium, or high priority", "Edit task:", "Delete task:", "Mark “"):
    assert label in activity, f"missing screen-reader label source: {label}"
assert "setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)" in activity
assert "screen text is English only" in activity, "language limitations must remain explicit"
task_logic = (main / "java/com/cue/daymark/TaskLogic.java").read_text(encoding="utf-8")
assert "Locale.getDefault()" in task_logic, "date formatting should follow the device locale"

print("PASS task/browser separation: web input does not create tasks or receive task-draft prefill")
print("PASS browser policy: Offline by default with persisted opt-in, Daymark page/resource loads blocked while Offline, and a separate tap required for each request")
print("PASS browser policy: encoded explicit search, HTTPS-only with HTTP/redirect downgrade blocking, no JS bridge/request interceptor")
print("PASS browser settings: Safe Browsing defaults on, confirmed opt-out persists, and current/future WebViews track the preference")
print("PASS WebView source security: Safe Browsing, mixed-content/file-access restrictions, SSL cancel, site permission denial, pop-up/download handling")
print("PASS local browser data: capped origin-only site history, legacy-origin migration, reopen/dedup, secret redaction, and explicit history/cookie/cache/WebStorage clear")
print("PASS reminder safety: only scoped reminder permissions/private receiver; encrypted local store; exact/inexact idle alarms; unchanged notification channels")
print("PASS manifest/dependencies: existing browser INTERNET plus reminder-only permissions, no service, no runtime dependency or optional media/model binaries")
print("PASS accessibility/localization source checks: scalable text, labeled controls, live status, explicit English-only scope, device-locale dates")
PY
python3 "$ROOT/tools/check-suggestion-navigation.py"
python3 "$ROOT/tools/check-accessibility-contrast.py"

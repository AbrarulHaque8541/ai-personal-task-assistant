# In-app bug report (easy path)

## User experience

1. **More → Report a bug** → dialog → **Send report**
2. Or **shake the phone** → same dialog
3. Daymark switches to Web mode and opens a **pre-filled GitHub Issues / new** page **inside the app**
4. If the user is logged into GitHub in that WebView, they tap **Submit new issue** once
5. Offline fallback: **Copy report**

No API token is embedded in the APK (anyone could extract it and spam the repo). Submission uses the user’s own GitHub session in WebView.

## Code

- `BugReportComposer.java` — body + URL builder
- `MainActivity` hooks — see `patches/mainactivity-bug-report-shake.patch` and methods below

### Methods to add to MainActivity

```java
private void registerShakeToReport() {
    if (sensorManager == null) return;
    if (shakeListener != null) return;
    Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    if (accelerometer == null) return;
    shakeListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            if (event == null || event.values == null || event.values.length < 3) return;
            float x = event.values[0], y = event.values[1], z = event.values[2];
            double g = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
            if (g < 2.4) return;
            long now = System.currentTimeMillis();
            if (now - lastShakeMs < 2500) return;
            lastShakeMs = now;
            runOnUiThread(() -> openBugReportFlow("shake"));
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };
    sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
}

private void unregisterShakeToReport() {
    if (sensorManager != null && shakeListener != null) {
        sensorManager.unregisterListener(shakeListener);
        shakeListener = null;
    }
}

private void openBugReportFlow(String trigger) {
    String versionName = "1.0.5";
    int versionCode = 6;
    try {
        PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
        if (info != null) {
            versionName = info.versionName == null ? versionName : info.versionName;
            versionCode = info.versionCode;
        }
    } catch (Exception ignored) { }
    String screen = webMode ? "browser" : "tasks";
    String url = BugReportComposer.githubNewIssueUrl(
            versionName, versionCode, trigger, screen, storageFailureDetails, taskDraft);
    BugReportComposer.rememberTrigger(trigger);
    new AlertDialog.Builder(this)
            .setTitle("Report a bug")
            .setMessage("Daymark will open a pre-filled GitHub Issue inside the app. "
                    + "If you are logged into GitHub in the browser, tap Submit once. "
                    + "Nothing is uploaded until you submit. Shake the phone anytime to open this again.")
            .setPositiveButton("Send report", (d, w) -> {
                setWebMode(true);
                if (browserWebView != null) {
                    browserWebView.loadUrl(url);
                } else {
                    mainHandler.postDelayed(() -> {
                        if (browserWebView != null) browserWebView.loadUrl(url);
                        else showToast("Open Web mode, then try Report a bug again.");
                    }, 400);
                }
            })
            .setNeutralButton("Copy report", (d, w) -> {
                String body = BugReportComposer.buildBody(
                        versionName, versionCode, trigger, screen, storageFailureDetails, taskDraft);
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Daymark bug report", body));
                    showToast("Report copied. Paste into a GitHub Issue.");
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
}
```

Agents: prefer applying this on a branch and running CI; do not embed a GitHub PAT in the client.

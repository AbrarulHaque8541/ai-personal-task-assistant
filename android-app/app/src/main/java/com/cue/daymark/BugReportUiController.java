package com.cue.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.widget.EditText;
import android.webkit.WebView;

/**
 * Owns shake detection, token dialog, and issue submission UI so MainActivity
 * only needs a few lifecycle and menu calls.
 */
final class BugReportUiController {
    interface Host {
        boolean isWebMode();
        String storageFailureDetails();
        String taskDraft();
        void setWebMode(boolean web);
        WebView browserWebView();
        void showToast(String message);
        int dp(int value);
    }

    private final Activity activity;
    private final Handler mainHandler;
    private final Host host;
    private final BugReportTokenStore tokenStore;
    private SensorManager sensorManager;
    private SensorEventListener shakeListener;
    private long lastShakeMs;

    BugReportUiController(Activity activity, Handler mainHandler, Host host) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.host = host;
        this.tokenStore = new BugReportTokenStore(activity);
    }

    void onCreate() {
        sensorManager = (SensorManager) activity.getSystemService(Activity.SENSOR_SERVICE);
        DaymarkBugReporter.install(activity, tokenStore);
        DaymarkBugReporter.flushPending(activity, tokenStore);
    }

    void onResume() {
        registerShake();
    }

    void onPause() {
        unregisterShake();
    }

    boolean hasToken() {
        return tokenStore.hasToken();
    }

    void openTokenDialog() {
        final EditText input = new EditText(activity);
        input.setHint("github_pat_… or ghp_… (Issues write, this repo only)");
        input.setSingleLine(true);
        int pad = host.dp(16);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(activity)
                .setTitle("GitHub Issues token")
                .setMessage("Fine-grained PAT: only ai-personal-task-assistant, permission Issues Read/Write. "
                        + "Stored only on this phone. Enables one-tap Issue creation and crash auto-report.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String raw = input.getText() == null ? "" : input.getText().toString().trim();
                    if (raw.isEmpty()) {
                        tokenStore.clear();
                        host.showToast("Token cleared");
                    } else {
                        tokenStore.saveToken(raw);
                        host.showToast("Token saved — reports can auto-create Issues");
                    }
                })
                .setNeutralButton("Clear", (d, w) -> {
                    tokenStore.clear();
                    host.showToast("Token cleared");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void openReport(String trigger) {
        String versionName = "1.0.5";
        int versionCode = 6;
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            if (info != null) {
                versionName = info.versionName == null ? versionName : info.versionName;
                versionCode = info.versionCode;
            }
        } catch (Exception ignored) { }
        String screen = host.isWebMode() ? "browser" : "tasks";
        String title = BugReportComposer.issueTitle(trigger, versionName);
        String body = BugReportComposer.buildBody(
                versionName, versionCode, trigger, screen,
                host.storageFailureDetails(), host.taskDraft());
        BugReportComposer.rememberTrigger(trigger);
        boolean canAuto = tokenStore.hasToken();
        new AlertDialog.Builder(activity)
                .setTitle("Report a bug")
                .setMessage(canAuto
                        ? "Token saved. Create Issue now posts directly to your GitHub repo."
                        : "Save a GitHub Issues token in More for fully automatic submit. "
                                + "Otherwise Send opens a prefilled form inside Daymark.")
                .setPositiveButton(canAuto ? "Create Issue now" : "Send report", (d, w) -> {
                    if (canAuto) submitApi(title, body);
                    else openWeb(BugReportComposer.githubNewIssueUrl(
                            versionName, versionCode, trigger, screen,
                            host.storageFailureDetails(), host.taskDraft()));
                })
                .setNeutralButton("Copy report", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("Daymark bug report", body));
                        host.showToast("Report copied");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitApi(String title, String body) {
        host.showToast("Creating GitHub Issue…");
        final String token = tokenStore.getToken();
        new Thread(() -> {
            try {
                String url = BugReportApiClient.createIssue(token, title, body);
                mainHandler.post(() -> {
                    host.showToast("Issue created");
                    if (url != null) openWeb(url);
                });
            } catch (Exception e) {
                mainHandler.post(() -> host.showToast("Issue failed: " + e.getMessage()));
            }
        }, "daymark-bug-report").start();
    }

    private void openWeb(String url) {
        host.setWebMode(true);
        WebView web = host.browserWebView();
        if (web != null) {
            web.loadUrl(url);
        } else {
            mainHandler.postDelayed(() -> {
                WebView again = host.browserWebView();
                if (again != null) again.loadUrl(url);
                else host.showToast("Open Web mode, then try again");
            }, 400);
        }
    }

    private void registerShake() {
        if (sensorManager == null || shakeListener != null) return;
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
                mainHandler.post(() -> openReport("shake"));
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };
        sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    private void unregisterShake() {
        if (sensorManager != null && shakeListener != null) {
            sensorManager.unregisterListener(shakeListener);
            shakeListener = null;
        }
    }
}

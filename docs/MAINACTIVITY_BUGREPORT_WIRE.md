# Minimal MainActivity wire (copy-paste)

After classes on branch `feat/auto-github-issue-bug-report`:

```java
// field
private BugReportUiController bugReportUi;

// end of onCreate after loadEncryptedTasks:
bugReportUi = new BugReportUiController(this, mainHandler, new BugReportUiController.Host() {
    @Override public boolean isWebMode() { return webMode; }
    @Override public String storageFailureDetails() { return storageFailureDetails; }
    @Override public String taskDraft() { return taskDraft; }
    @Override public void setWebMode(boolean web) { MainActivity.this.setWebMode(web); }
    @Override public android.webkit.WebView browserWebView() { return browserWebView; }
    @Override public void showToast(String message) { MainActivity.this.showToast(message); }
    @Override public int dp(int value) { return MainActivity.this.dp(value); }
});
bugReportUi.onCreate();

// onResume after super:
if (bugReportUi != null) bugReportUi.onResume();

// onPause start:
if (bugReportUi != null) bugReportUi.onPause();

// More settings rows:
addSettingsRow(content, "Report a bug",
    bugReportUi != null && bugReportUi.hasToken()
        ? "Token on · one tap creates a GitHub Issue"
        : "Shake or tap · save token for automatic Issues",
    () -> { if (bugReportUi != null) bugReportUi.openReport("settings"); });
addSettingsRow(content, "Bug report GitHub token",
    bugReportUi != null && bugReportUi.hasToken() ? "Saved on this device" : "Optional · Issues write PAT",
    () -> { if (bugReportUi != null) bugReportUi.openTokenDialog(); });
```

Owner setup: GitHub → Settings → Fine-grained token → only this repo → Issues Read/Write → paste in app once.

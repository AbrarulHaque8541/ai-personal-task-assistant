# MainActivity wire: About + updates

## Settings (in `showSettingsDialog`)

Add an **ABOUT** section (and move Check for updates here):

```java
addSettingsSection(content, "ABOUT");
addSettingsRow(content, "About this app",
        "Version, package, privacy summary",
        () -> showAboutApp());
addSettingsRow(content, "Check for updates",
        "Look for a newer signed release on GitHub",
        () -> checkForUpdates(true));
```

Remove any duplicate **Check for updates** row under POWER FEATURES.

## Update dialog (`showUpdateDetails`)

Append the APK URL to the message, and offer:

- **Download and verify** → existing `beginUpdateDownload` (preferred)
- **Open APK link** → `openUpdateApkLink(release)` (starts GitHub APK download in WebView/system browser)

## Methods to add

```java
private void showAboutApp() {
    String versionName = "?";
    long versionCode = 0L;
    try {
        PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
        if (info != null) {
            versionName = info.versionName == null ? "?" : info.versionName;
            if (Build.VERSION.SDK_INT >= 28) versionCode = info.getLongVersionCode();
            else versionCode = info.versionCode;
        }
    } catch (Exception ignored) { }
    String message = "Daymark\n"
            + "Version " + versionName + " (" + versionCode + ")\n"
            + "Package " + getPackageName() + "\n\n"
            + "Local-first tasks with encrypted storage. Optional HTTPS browser. "
            + "No account and no cloud task sync.\n\n"
            + "Updates: Check for updates uses the public GitHub Releases page. "
            + "If a newer signed build exists, download and verify in-app, or open the APK link.\n\n"
            + "Source & releases:\n"
            + "https://github.com/AbrarulHaque8541/ai-personal-task-assistant\n"
            + "https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/latest";
    new AlertDialog.Builder(this)
            .setTitle("About this app")
            .setMessage(message)
            .setPositiveButton("Check for updates", (d, w) -> checkForUpdates(true))
            .setNeutralButton("Open releases", (d, w) -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/latest")));
                } catch (Exception e) {
                    showToast("Could not open releases page");
                }
            })
            .setNegativeButton("Close", null)
            .show();
}

private void openUpdateApkLink(UpdaterCore.Release release) {
    if (release == null || release.assetUrl == null || release.assetUrl.trim().isEmpty()) {
        showToast("No APK link on this release");
        return;
    }
    String url = release.assetUrl.trim();
    try {
        if (BrowserAddress.isAllowedWebUrl(url)) {
            setWebMode(true);
            if (browserWebView != null) {
                browserWebView.loadUrl(url);
                showToast("Opening APK download link…");
                return;
            }
        }
    } catch (Exception ignored) { }
    try {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (Exception e) {
        showToast("Could not open APK link");
    }
}
```

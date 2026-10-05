package com.cue.daymark.updater;

import java.io.Serializable;

/**
 * Small saved-instance-state reference to a picker operation. Release metadata and APK bytes remain
 * in UpdaterRecoveryStore; this snapshot contains no provider URI, credential, or authentication data.
 */
public final class PendingSaveTransaction implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String TOKEN_PATTERN = "[a-f0-9]{32}";

    public final String apkSha256;
    public final String versionName;
    public final String token;
    public final String preferredFinalName;
    public final String pickerTitle;

    public PendingSaveTransaction(UpdaterCore.Release release, String token) {
        if (release == null || UpdaterCore.normalizeSha256(release.apkSha256) == null
                || release.versionName == null || !release.versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || token == null || !token.matches(TOKEN_PATTERN)) {
            throw new IllegalArgumentException("A valid release and unique picker transaction token are required.");
        }
        this.apkSha256 = UpdaterCore.normalizeSha256(release.apkSha256);
        this.versionName = release.versionName;
        this.token = token;
        this.preferredFinalName = "Daymark-v" + versionName + ".apk";
        this.pickerTitle = preferredFinalName + ".daymark-pending-" + token + ".tmp";
    }

    public boolean isValidFor(UpdaterCore.Release release) {
        return release != null && token != null && token.matches(TOKEN_PATTERN)
                && apkSha256 != null && apkSha256.equals(UpdaterCore.normalizeSha256(release.apkSha256))
                && versionName != null && versionName.equals(release.versionName)
                && preferredFinalName != null && preferredFinalName.equals("Daymark-v" + versionName + ".apk")
                && pickerTitle != null
                && pickerTitle.equals(preferredFinalName + ".daymark-pending-" + token + ".tmp");
    }
}

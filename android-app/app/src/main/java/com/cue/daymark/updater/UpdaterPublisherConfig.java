package com.cue.daymark.updater;

import com.cue.daymark.BuildConfig;

/**
 * Publisher gate for the GitHub sideload in-app updater.
 *
 * <p>Same production signing certificate as GitHub Release v1.0.1 so installed users
 * can receive a same-app update (package {@code com.cue.daymark}, higher versionCode)
 * without losing app-private encrypted data. Play flavor stays disabled via BuildConfig.
 */
public final class UpdaterPublisherConfig {
    public static final boolean UPDATER_ENABLED = true;
    /** Production certificate SHA-256 from v1.0.1 release metadata (lowercase hex). */
    public static final String PUBLISHER_SIGNER_SHA256 =
            "ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580";

    private UpdaterPublisherConfig() { }

    public static boolean isUpdaterConfigured() {
        return !BuildConfig.DEBUG
                && BuildConfig.UPDATER_ENABLED
                && UPDATER_ENABLED
                && UpdaterCore.normalizeSha256(PUBLISHER_SIGNER_SHA256) != null;
    }
}

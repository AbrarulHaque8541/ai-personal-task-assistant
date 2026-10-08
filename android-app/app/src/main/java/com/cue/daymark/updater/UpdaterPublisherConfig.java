package com.cue.daymark.updater;

import com.cue.daymark.BuildConfig;

/**
 * Publisher gate for the GitHub sideload updater.
 *
 * <p>Enabled only when: not a debug build, the {@code githubSideload} flavor sets
 * {@code BuildConfig.UPDATER_ENABLED}, this flag is true, and the production signer
 * certificate SHA-256 is pinned. The Play flavor keeps BuildConfig.UPDATER_ENABLED false.
 *
 * <p>Pin matches the production certificate published with GitHub Release v1.0.1.
 * Rotation requires a coordinated app update — do not change lightly.
 */
public final class UpdaterPublisherConfig {
    public static final boolean UPDATER_ENABLED = true;
    /** Lowercase hex SHA-256 of the production signing certificate (v1.0.1). */
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

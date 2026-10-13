package com.cue.daymark.updater;

import com.cue.daymark.BuildConfig;

/**
 * The owner-approved com.abrar.daymark identity is a fresh install, not an in-place
 * update of legacy com.cue.daymark. Keep the same pinned production signer for integrity.
 */
public final class UpdaterPublisherConfig {
    public static final boolean UPDATER_ENABLED = true;
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

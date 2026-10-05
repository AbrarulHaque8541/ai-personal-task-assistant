package com.cue.daymark.updater;

import com.cue.daymark.BuildConfig;

/**
 * Fail-closed publisher gate. Keep updater network and verification disabled until the
 * publisher has a protected release signing setup and has pinned the exact signer SHA-256.
 * Only the GitHub sideload flavor may opt in; the Play flavor always remains disabled.
 * Never use the debug key fingerprint here.
 */
public final class UpdaterPublisherConfig {
    public static final boolean UPDATER_ENABLED = false;
    public static final String PUBLISHER_SIGNER_SHA256 = "";

    private UpdaterPublisherConfig() { }

    public static boolean isUpdaterConfigured() {
        return !BuildConfig.DEBUG
                && BuildConfig.UPDATER_ENABLED
                && UPDATER_ENABLED
                && UpdaterCore.normalizeSha256(PUBLISHER_SIGNER_SHA256) != null;
    }
}

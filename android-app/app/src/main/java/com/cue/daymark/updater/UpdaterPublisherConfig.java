package com.cue.daymark.updater;

import com.cue.daymark.BuildConfig;

/**
 * Fail-closed publisher gate. Keep installation disabled until the publisher has a
 * protected release signing setup and has pinned the exact signer certificate SHA-256.
 * Never use the debug key fingerprint here.
 */
public final class UpdaterPublisherConfig {
    public static final boolean INSTALLATION_ENABLED = false;
    public static final String PUBLISHER_SIGNER_SHA256 = "";

    private UpdaterPublisherConfig() { }

    public static boolean isInstallationConfigured() {
        return !BuildConfig.DEBUG
                && INSTALLATION_ENABLED
                && UpdaterCore.normalizeSha256(PUBLISHER_SIGNER_SHA256) != null;
    }
}

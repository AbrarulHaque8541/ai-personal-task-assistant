package com.cue.daymark;

import android.content.Context;

/** The Play flavor has no updater connectivity permission and therefore always fails closed. */
final class UpdaterNetworkAccess {
    private UpdaterNetworkAccess() { }

    static boolean isWifiConnected(Context context) {
        return false;
    }
}

package com.cue.daymark;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Network-state inspection is confined to the sideload flavor that declares ACCESS_NETWORK_STATE. */
final class UpdaterNetworkAccess {
    private UpdaterNetworkAccess() { }

    static boolean isWifiConnected(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network activeNetwork = manager.getActiveNetwork();
        NetworkCapabilities capabilities = activeNetwork == null
                ? null : manager.getNetworkCapabilities(activeNetwork);
        return capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
}

package com.cue.daymark.updater;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/** Receives PackageInstaller status through an explicit immutable PendingIntent. */
public final class PackageInstallerStatusActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent callback = getIntent();
        String expectedAction = getPackageName() + AndroidPackageInstallerHandoff.STATUS_ACTION_SUFFIX;
        if (callback == null || !expectedAction.equals(callback.getAction())) {
            finish();
            return;
        }

        int status = callback.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent systemConfirmation = getPendingUserAction(callback);
            if (systemConfirmation != null) {
                startActivity(systemConfirmation);
            } else {
                Toast.makeText(this, "Android did not provide an installation confirmation screen. Nothing was installed.",
                        Toast.LENGTH_LONG).show();
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(this, "Android completed the approved Daymark update.", Toast.LENGTH_LONG).show();
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            Toast.makeText(this, "Update cancelled. The existing app remains installed.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Android did not install the update. The existing app remains installed. Do not uninstall Daymark; encrypted tasks may be lost.",
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }

    @SuppressWarnings("deprecation")
    private static Intent getPendingUserAction(Intent callback) {
        if (Build.VERSION.SDK_INT >= 33) return callback.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        return (Intent) callback.getParcelableExtra(Intent.EXTRA_INTENT);
    }
}

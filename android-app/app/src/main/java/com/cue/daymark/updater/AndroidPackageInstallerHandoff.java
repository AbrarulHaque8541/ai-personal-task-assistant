package com.cue.daymark.updater;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import com.cue.daymark.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Copies only a fully verified APK into a system PackageInstaller session; Android retains final approval. */
public final class AndroidPackageInstallerHandoff implements UpdaterCore.VerifiedHandoff {
    static final String STATUS_ACTION_SUFFIX = ".updater.PACKAGE_INSTALL_STATUS";
    private final Context context;

    public AndroidPackageInstallerHandoff(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        this.context = context.getApplicationContext();
    }

    @Override
    public void handoff(File verifiedApk) throws UpdaterCore.UpdateException {
        if (!BuildConfig.UPDATER_INSTALLATION_ENABLED || BuildConfig.DEBUG
                || !UpdaterPublisherConfig.isInstallationConfigured()) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.HANDOFF,
                    "APK installation is disabled for this build or publisher configuration.");
        }
        if (Build.VERSION.SDK_INT < 26 || !context.getPackageManager().canRequestPackageInstalls()) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.HANDOFF,
                    "Android has not approved this app as an install source.");
        }
        if (verifiedApk == null || !verifiedApk.isFile() || verifiedApk.length() <= 0L
                || verifiedApk.length() > UpdaterCore.MAX_APK_BYTES) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                    "The verified APK is unavailable or outside the safe byte limit.");
        }

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams parameters = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        parameters.setAppPackageName(UpdaterCore.APPLICATION_ID);
        parameters.setSize(verifiedApk.length());
        int sessionId = -1;
        boolean committed = false;
        try {
            sessionId = installer.createSession(parameters);
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                copyVerifiedApk(session, verifiedApk);
                Intent statusIntent = new Intent(context, PackageInstallerStatusActivity.class)
                        .setAction(context.getPackageName() + STATUS_ACTION_SUFFIX)
                        .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                // PackageInstaller must attach status and pending-user-action extras to this explicit callback.
                if (Build.VERSION.SDK_INT >= 31) pendingIntentFlags |= PendingIntent.FLAG_MUTABLE;
                PendingIntent statusPendingIntent = PendingIntent.getActivity(context, sessionId, statusIntent,
                        pendingIntentFlags);
                session.commit(statusPendingIntent.getIntentSender());
                committed = true;
            }
        } catch (UpdaterCore.UpdateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.HANDOFF,
                    "Android could not accept the verified APK for user-confirmed installation.", exception);
        } finally {
            if (!committed && sessionId >= 0) {
                try { installer.abandonSession(sessionId); } catch (RuntimeException ignored) { }
            }
        }
    }

    private static void copyVerifiedApk(PackageInstaller.Session session, File apk)
            throws IOException, UpdaterCore.UpdateException {
        long expectedBytes = apk.length();
        long copiedBytes = 0L;
        try (InputStream input = new FileInputStream(apk);
             OutputStream output = session.openWrite("base.apk", 0L, expectedBytes)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                copiedBytes += read;
                if (copiedBytes > expectedBytes || copiedBytes > UpdaterCore.MAX_APK_BYTES) {
                    throw new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                            "Verified APK changed while it was being handed to Android.");
                }
                output.write(buffer, 0, read);
            }
            output.flush();
            session.fsync(output);
        }
        if (copiedBytes != expectedBytes) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                    "Verified APK changed while it was being handed to Android.");
        }
    }
}

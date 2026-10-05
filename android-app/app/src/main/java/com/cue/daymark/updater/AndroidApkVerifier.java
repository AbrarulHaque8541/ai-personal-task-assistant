package com.cue.daymark.updater;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Uses Android's package parser; rejects ambiguous multi-signer packages. */
public final class AndroidApkVerifier implements UpdaterCore.ApkVerifier {
    private final Context context;

    public AndroidApkVerifier(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public UpdaterCore.ApkIdentity inspect(File apk) throws UpdaterCore.UpdateException {
        final PackageInfo info;
        try {
            info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), signingFlags());
        } catch (RuntimeException exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                    "Android could not read the downloaded APK metadata.", exception);
        }
        if (info == null || info.applicationInfo == null) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                    "Downloaded file is not a readable Android APK.");
        }
        String signer = signerSha256(info);
        return new UpdaterCore.ApkIdentity(info.packageName, info.versionName,
                versionCode(info), info.applicationInfo.minSdkVersion, signer);
    }

    public String installedSignerSha256() throws UpdaterCore.UpdateException {
        try {
            PackageInfo installed = getInstalledPackageInfo();
            String signer = signerSha256(installed);
            if (signer == null) {
                throw new UpdaterCore.UpdateException(UpdaterCore.Failure.SIGNER_MISMATCH,
                        "Installed app has an unsupported or ambiguous signing identity.");
            }
            return signer;
        } catch (PackageManager.NameNotFoundException exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.SIGNER_MISMATCH,
                    "Unable to read the installed app signing identity.", exception);
        }
    }

    private PackageInfo getInstalledPackageInfo() throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), signingFlags());
    }

    private static int signingFlags() {
        return Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static String signerSha256(PackageInfo info) throws UpdaterCore.UpdateException {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null || signingInfo.hasMultipleSigners()) return null;
            signatures = signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length != 1) return null;
        try {
            byte[] certificate = signatures[0].toByteArray();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.SIGNER_MISMATCH,
                    "SHA-256 is unavailable for APK signer verification.", exception);
        }
    }
}

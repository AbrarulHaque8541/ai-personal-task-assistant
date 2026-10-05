package com.cue.daymark;

import com.cue.daymark.updater.UpdaterCore;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

public final class UpdaterSmoke {
    private static final String SIGNER = repeat('a', 64);
    private static final byte[] APK_BYTES = "fake-apk-payload-for-host-tests".getBytes(StandardCharsets.UTF_8);
    private static int assertions;

    private UpdaterSmoke() { }

    public static void main(String[] args) throws Exception {
        rateLimitAllowsForegroundCadenceAndManualCheck();
        publisherConfigurationGatesNetworkChecks();
        assetRedirectsAreRestrictedToTrustedHosts();
        offlineHttpRateLimitAndInvalidMetadataAreHandled();
        missingAndUnstableReleasesAreIgnored();
        stableNewerReleaseIsFound();
        cancellationDoesNotDownloadOrHandoff();
        sizeMismatchNeverHandsOff();
        hashMismatchNeverHandsOff();
        packageAndVersionMismatchesNeverHandOff();
        signingCertificateMismatchNeverHandsOff();
        emptyPublisherSignerBlocksConsentAndDownload();
        verifiedArtifactReachesHandoffExactlyOnce();
        System.out.println("PASS updater host tests: " + assertions + " assertions");
    }

    private static void rateLimitAllowsForegroundCadenceAndManualCheck() {
        long now = 2_000_000L;
        check(UpdaterCore.shouldCheck(0L, now, false), "first foreground check is due");
        check(!UpdaterCore.shouldCheck(now, now + UpdaterCore.CHECK_INTERVAL_MILLIS - 1, false),
                "repeat foreground is rate limited for 24 hours");
        check(UpdaterCore.shouldCheck(now, now + UpdaterCore.CHECK_INTERVAL_MILLIS, false),
                "foreground check is due after 24 hours");
        check(UpdaterCore.shouldCheck(now, now + 1, true), "manual check bypasses rate limit");
    }

    private static void publisherConfigurationGatesNetworkChecks() {
        check(UpdaterCore.isNetworkCheckAllowed(true, true), "network check requires permission and publisher config");
        check(!UpdaterCore.isNetworkCheckAllowed(false, true), "missing Internet permission blocks metadata request");
        check(!UpdaterCore.isNetworkCheckAllowed(true, false), "unknown publisher signer blocks metadata request");
        check(!UpdaterCore.isNetworkCheckAllowed(false, false), "missing permission and signer fail closed");
    }

    private static void assetRedirectsAreRestrictedToTrustedHosts() {
        check(UpdaterCore.isAllowedAssetRedirectUrl(
                "https://release-assets.githubusercontent.com/download/asset?token=signed"),
                "GitHub release asset host with signed query is allowed");
        check(UpdaterCore.isAllowedAssetRedirectUrl(
                "https://objects.githubusercontent.com/github-production/asset"),
                "GitHub object asset host is allowed");
        check(UpdaterCore.isAllowedAssetRedirectUrl(assetUrl()), "original fixed GitHub asset path is allowed");
        check(!UpdaterCore.isAllowedAssetRedirectUrl("https://evil.example/Daymark.apk"),
                "untrusted redirect host is rejected");
        check(!UpdaterCore.isAllowedAssetRedirectUrl("http://release-assets.githubusercontent.com/a.apk"),
                "cleartext redirect is rejected");
        check(!UpdaterCore.isAllowedAssetRedirectUrl("https://release-assets.githubusercontent.com.evil/a.apk"),
                "lookalike asset host is rejected");
        check(!UpdaterCore.isAllowedAssetRedirectUrl("https://user@release-assets.githubusercontent.com/a.apk"),
                "userinfo redirect is rejected");
        check(!UpdaterCore.isAllowedAssetRedirectUrl("https://release-assets.githubusercontent.com:444/a.apk"),
                "nonstandard redirect port is rejected");
    }

    private static void offlineHttpRateLimitAndInvalidMetadataAreHandled() throws Exception {
        expectFailure(UpdaterCore.Failure.OFFLINE, () -> UpdaterCore.check(
                () -> { throw new UpdaterCore.UpdateException(UpdaterCore.Failure.OFFLINE, "offline"); },
                UpdaterCore.APPLICATION_ID, 1L), "offline is a contained updater error");
        expectFailure(UpdaterCore.Failure.HTTP, () -> UpdaterCore.check(
                () -> { throw new UpdaterCore.UpdateException(UpdaterCore.Failure.HTTP, "server error"); },
                UpdaterCore.APPLICATION_ID, 1L), "HTTP failure is classified");
        expectFailure(UpdaterCore.Failure.RATE_LIMITED, () -> UpdaterCore.check(
                () -> { throw new UpdaterCore.UpdateException(UpdaterCore.Failure.RATE_LIMITED, "limited"); },
                UpdaterCore.APPLICATION_ID, 1L), "GitHub rate limiting is classified");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA, () -> UpdaterCore.check(
                () -> release("v1.1.0", 2L, "not-a-digest", SIGNER, false, false),
                UpdaterCore.APPLICATION_ID, 1L), "invalid release metadata is rejected");
    }

    private static void missingAndUnstableReleasesAreIgnored() throws Exception {
        check(UpdaterCore.check(() -> null, UpdaterCore.APPLICATION_ID, 1L).status
                == UpdaterCore.CheckStatus.NO_UPDATE, "empty releases mean no update");
        check(UpdaterCore.check(() -> release("v1.1.0", 2L, hash(APK_BYTES), SIGNER, true, false),
                UpdaterCore.APPLICATION_ID, 1L).status == UpdaterCore.CheckStatus.NO_UPDATE,
                "draft release is ignored");
        check(UpdaterCore.check(() -> release("v1.1.0", 2L, hash(APK_BYTES), SIGNER, false, true),
                UpdaterCore.APPLICATION_ID, 1L).status == UpdaterCore.CheckStatus.NO_UPDATE,
                "prerelease is ignored");
        check(UpdaterCore.check(() -> release("v1.0.0", 1L, hash(APK_BYTES), SIGNER, false, false),
                UpdaterCore.APPLICATION_ID, 1L).status == UpdaterCore.CheckStatus.NO_UPDATE,
                "same version is not offered again");
    }

    private static void stableNewerReleaseIsFound() throws Exception {
        UpdaterCore.Release stable = release("v1.1.0", 2L, hash(APK_BYTES), SIGNER, false, false);
        UpdaterCore.CheckResult result = UpdaterCore.check(() -> stable, UpdaterCore.APPLICATION_ID, 1L);
        check(result.status == UpdaterCore.CheckStatus.UPDATE_AVAILABLE, "stable newer release is offered");
        check(result.release == stable, "the stable release details are retained for consent UI");
    }

    private static void cancellationDoesNotDownloadOrHandoff() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        AtomicInteger handoffs = new AtomicInteger();
        UpdaterCore.InstallStatus status = UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> false,
                release -> { downloads.incrementAndGet(); return writeTemp(APK_BYTES); },
                apk -> identity(),
                apk -> handoffs.incrementAndGet());
        check(status == UpdaterCore.InstallStatus.CANCELLED, "cancel returns without changing app state");
        check(downloads.get() == 0, "cancel occurs before APK download");
        check(handoffs.get() == 0, "cancel never reaches installer handoff");
    }

    private static void sizeMismatchNeverHandsOff() throws Exception {
        AtomicInteger handoffs = new AtomicInteger();
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(new byte[] { 1, 2 }), apk -> identity(),
                apk -> handoffs.incrementAndGet()), "actual file size must match declared bytes");
        check(handoffs.get() == 0, "size mismatch never reaches installer handoff");
    }

    private static void hashMismatchNeverHandsOff() throws Exception {
        AtomicInteger handoffs = new AtomicInteger();
        UpdaterCore.Release wrongHash = release("v1.1.0", 2L, repeat('0', 64), SIGNER, false, false);
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadVerifyAndHandoff(
                wrongHash, UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES), apk -> identity(),
                apk -> handoffs.incrementAndGet()), "SHA-256 must match release metadata");
        check(handoffs.get() == 0, "hash mismatch never reaches installer handoff");
    }

    private static void packageAndVersionMismatchesNeverHandOff() throws Exception {
        AtomicInteger handoffs = new AtomicInteger();
        expectFailure(UpdaterCore.Failure.INVALID_METADATA, () -> UpdaterCore.downloadVerifyAndHandoff(
                new UpdaterCore.Release("v1.1.0", "1.1.0", "Daymark 1.1.0", "Notes",
                        "com.attacker.app", 2L, 26, APK_BYTES.length, hash(APK_BYTES), SIGNER,
                        assetUrl(), false, false), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES), apk -> identity(),
                apk -> handoffs.incrementAndGet()), "release package ID must be fixed to Daymark");
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES),
                apk -> new UpdaterCore.ApkIdentity("com.cue.daymark", "1.1.1", 2L, 26, SIGNER),
                apk -> handoffs.incrementAndGet()), "APK version name must match release metadata");
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES),
                apk -> new UpdaterCore.ApkIdentity("com.attacker.app", "1.1.0", 2L, 26, SIGNER),
                apk -> handoffs.incrementAndGet()), "APK package ID must match the installed application");
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES),
                apk -> new UpdaterCore.ApkIdentity("com.cue.daymark", "1.1.0", 3L, 26, SIGNER),
                apk -> handoffs.incrementAndGet()), "APK version code must match release metadata");
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES),
                apk -> new UpdaterCore.ApkIdentity("com.cue.daymark", "1.1.0", 2L, 28, SIGNER),
                apk -> handoffs.incrementAndGet()), "APK minimum SDK must match release metadata");
        check(handoffs.get() == 0, "package/version mismatch never reaches installer handoff");
    }

    private static void signingCertificateMismatchNeverHandsOff() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        AtomicInteger handoffs = new AtomicInteger();
        expectFailure(UpdaterCore.Failure.SIGNER_MISMATCH, () -> UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, repeat('b', 64),
                release -> true, release -> { downloads.incrementAndGet(); return writeTemp(APK_BYTES); },
                apk -> identity(), apk -> handoffs.incrementAndGet()),
                "publisher configuration must match running app signer");
        check(downloads.get() == 0, "publisher mismatch blocks download");
        expectFailure(UpdaterCore.Failure.SIGNER_MISMATCH, () -> UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES),
                apk -> new UpdaterCore.ApkIdentity("com.cue.daymark", "1.1.0", 2L, 26, repeat('b', 64)),
                apk -> handoffs.incrementAndGet()), "APK signer must match installed and configured signer");
        check(handoffs.get() == 0, "signing-certificate mismatch never reaches installer handoff");
    }

    private static void emptyPublisherSignerBlocksConsentAndDownload() throws Exception {
        AtomicInteger consents = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();
        AtomicInteger handoffs = new AtomicInteger();
        expectFailure(UpdaterCore.Failure.SIGNER_MISMATCH, () -> UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, "",
                release -> { consents.incrementAndGet(); return true; },
                release -> { downloads.incrementAndGet(); return writeTemp(APK_BYTES); },
                apk -> identity(), apk -> handoffs.incrementAndGet()),
                "empty publisher signer must reject the update before consent or download");
        check(consents.get() == 0, "empty publisher signer blocks consent prompt");
        check(downloads.get() == 0, "empty publisher signer blocks APK network/download path");
        check(handoffs.get() == 0, "empty publisher signer blocks installer handoff");
    }

    private static void verifiedArtifactReachesHandoffExactlyOnce() throws Exception {
        AtomicInteger handoffs = new AtomicInteger();
        UpdaterCore.InstallStatus status = UpdaterCore.downloadVerifyAndHandoff(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES), apk -> identity(),
                apk -> {
                    check(apk.isFile(), "verified temp APK is available to handoff adapter");
                    handoffs.incrementAndGet();
                });
        check(status == UpdaterCore.InstallStatus.HANDED_OFF, "verified release completes handoff path");
        check(handoffs.get() == 1, "verified APK is handed off exactly once");
    }

    private static UpdaterCore.Release validRelease() {
        return release("v1.1.0", 2L, hash(APK_BYTES), SIGNER, false, false);
    }

    private static UpdaterCore.Release release(String tag, long versionCode, String hash, String signer,
            boolean draft, boolean prerelease) {
        return new UpdaterCore.Release(tag, tag.startsWith("v") ? tag.substring(1) : tag,
                "Daymark " + tag, "Security and stability improvements.", UpdaterCore.APPLICATION_ID,
                versionCode, 26, APK_BYTES.length, hash, signer, assetUrl(), draft, prerelease);
    }

    private static UpdaterCore.ApkIdentity identity() {
        return new UpdaterCore.ApkIdentity(UpdaterCore.APPLICATION_ID, "1.1.0", 2L, 26, SIGNER);
    }

    private static String assetUrl() {
        return "https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.1.0/Daymark-v1.1.0.apk";
    }

    private static File writeTemp(byte[] bytes) throws UpdaterCore.UpdateException {
        try {
            File file = File.createTempFile("daymark-updater-test-", ".apk");
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
            return file;
        } catch (Exception exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.DOWNLOAD,
                    "Unable to create fake APK for updater test.", exception);
        }
    }

    private static String hash(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String repeat(char character, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int i = 0; i < count; i++) value.append(character);
        return value.toString();
    }

    private interface CheckedOperation { void run() throws Exception; }

    private static void expectFailure(UpdaterCore.Failure failure, CheckedOperation operation,
            String message) throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message + " (operation unexpectedly succeeded)");
        } catch (UpdaterCore.UpdateException exception) {
            if (exception.failure != failure) {
                throw new AssertionError(message + " (expected " + failure + ", got " + exception.failure + ")");
            }
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}

package com.cue.daymark;

import com.cue.daymark.updater.UpdaterCore;
import com.cue.daymark.updater.UpdaterRecoveryStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
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
        cancellationDoesNotDownload();
        cancellationAfterTransferStillDeletesTemporary();
        sizeMismatchFailsClosed();
        actual100MiBCeilingFailsClosed();
        hashMismatchFailsClosed();
        packageAndVersionMismatchesFailClosed();
        signingCertificateMismatchFailsClosed();
        emptyPublisherSignerBlocksConsentAndDownload();
        verifiedArtifactIsReportedWithoutInstallHandoff();
        verifiedArtifactRecoverySurvivesRestartAndRevalidates();
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
        check(UpdaterCore.isAllowedAssetUrl(assetUrl()), "HTTPS GitHub release download URL is allowed");
        check(UpdaterCore.isAllowedAssetUrl("https://github.com:443/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.1.0/Daymark-v1.1.0.apk"),
                "explicit default HTTPS port is allowed for the GitHub release host");
        check(!UpdaterCore.isAllowedAssetUrl("https://github.com:444/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.1.0/Daymark-v1.1.0.apk"),
                "unusual port on the GitHub release asset host is rejected");
        check(!UpdaterCore.isAllowedAssetUrl("https://evil.example/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.1.0/Daymark-v1.1.0.apk"),
                "non-GitHub release asset host is rejected");
        check(!UpdaterCore.isAllowedAssetUrl("http://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.1.0/Daymark-v1.1.0.apk"),
                "cleartext GitHub release asset URL is rejected");
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

    private static void cancellationDoesNotDownload() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        UpdaterCore.VerificationResult result = UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> false,
                release -> { downloads.incrementAndGet(); return writeTemp(APK_BYTES); },
                apk -> identity());
        check(result.status == UpdaterCore.VerificationStatus.CANCELLED, "cancel returns without changing app state");
        check(downloads.get() == 0, "cancel occurs before APK download");
    }

    private static void cancellationAfterTransferStillDeletesTemporary() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        File[] downloaded = new File[1];
        UpdaterCore.Downloader downloader = new UpdaterCore.Downloader() {
            @Override public File download(UpdaterCore.Release release) throws UpdaterCore.UpdateException {
                downloaded[0] = writeTemp(APK_BYTES);
                cancelled.set(true);
                return downloaded[0];
            }
            @Override public boolean isCancelled() { return cancelled.get(); }
        };
        expectFailure(UpdaterCore.Failure.CANCELLED, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, downloader, apk -> identity()),
                "cancellation at the transfer/verification boundary is honored");
        check(downloaded[0] != null && !downloaded[0].exists(),
                "boundary cancellation deletes the unverified temporary APK");
    }

    private static void sizeMismatchFailsClosed() throws Exception {
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(new byte[] { 1, 2 }), apk -> identity()),
                "actual file size must match declared bytes");
    }

    private static void actual100MiBCeilingFailsClosed() throws Exception {
        File oversized = File.createTempFile("daymark-updater-oversized-", ".apk");
        try (RandomAccessFile sparse = new RandomAccessFile(oversized, "rw")) {
            sparse.setLength(UpdaterCore.MAX_APK_BYTES + 1L);
        }
        AtomicInteger verifierCalls = new AtomicInteger();
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> oversized,
                apk -> { verifierCalls.incrementAndGet(); return identity(); }),
                "an actual cached APK larger than 100 MiB is rejected");
        check(verifierCalls.get() == 0, "the APK parser is not run after actual-size rejection");
        check(!oversized.exists(), "actual-size rejection deletes the temporary APK");
    }

    private static void hashMismatchFailsClosed() throws Exception {
        UpdaterCore.Release wrongHash = release("v1.1.0", 2L, repeat('0', 64), SIGNER, false, false);
        File[] downloaded = new File[1];
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                wrongHash, UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> { downloaded[0] = writeTemp(APK_BYTES); return downloaded[0]; },
                apk -> identity()),
                "SHA-256 must match release metadata");
        check(downloaded[0] != null && !downloaded[0].exists(), "hash verification failure deletes the temporary APK");
    }

    private static void packageAndVersionMismatchesFailClosed() throws Exception {
        expectFailure(UpdaterCore.Failure.INVALID_METADATA, () -> UpdaterCore.downloadAndVerify(
                new UpdaterCore.Release("v1.1.0", "1.1.0", "Daymark 1.1.0", "Notes",
                        "com.attacker.app", 2L, 26, APK_BYTES.length, hash(APK_BYTES), SIGNER,
                        assetUrl(), false, false), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES), apk -> identity()),
                "release package ID must be fixed to Daymark");
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES),
                apk -> new UpdaterCore.ApkIdentity("com.cue.daymark", "1.1.1", 2L, 26, SIGNER)),
                "APK version name must match release metadata");
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES),
                apk -> new UpdaterCore.ApkIdentity("com.attacker.app", "1.1.0", 2L, 26, SIGNER)),
                "APK package ID must match the installed application");
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES),
                apk -> new UpdaterCore.ApkIdentity("com.cue.daymark", "1.1.0", 3L, 26, SIGNER)),
                "APK version code must match release metadata");
        expectFailure(UpdaterCore.Failure.APK_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> writeTemp(APK_BYTES),
                apk -> new UpdaterCore.ApkIdentity("com.cue.daymark", "1.1.0", 2L, 28, SIGNER)),
                "APK minimum SDK must match release metadata");
    }

    private static void signingCertificateMismatchFailsClosed() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        expectFailure(UpdaterCore.Failure.SIGNER_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, repeat('b', 64),
                release -> true, release -> { downloads.incrementAndGet(); return writeTemp(APK_BYTES); },
                apk -> identity()),
                "publisher configuration must match running app signer");
        check(downloads.get() == 0, "publisher mismatch blocks download");
        File[] downloaded = new File[1];
        expectFailure(UpdaterCore.Failure.SIGNER_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true, release -> { downloaded[0] = writeTemp(APK_BYTES); return downloaded[0]; },
                apk -> new UpdaterCore.ApkIdentity("com.cue.daymark", "1.1.0", 2L, 26, repeat('b', 64))),
                "APK signer must match installed and configured signer");
        check(downloaded[0] != null && !downloaded[0].exists(), "signer verification failure deletes the temporary APK");
    }

    private static void emptyPublisherSignerBlocksConsentAndDownload() throws Exception {
        AtomicInteger consents = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();
        expectFailure(UpdaterCore.Failure.SIGNER_MISMATCH, () -> UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, "",
                release -> { consents.incrementAndGet(); return true; },
                release -> { downloads.incrementAndGet(); return writeTemp(APK_BYTES); },
                apk -> identity()),
                "empty publisher signer must reject the update before consent or download");
        check(consents.get() == 0, "empty publisher signer blocks consent prompt");
        check(downloads.get() == 0, "empty publisher signer blocks APK network/download path");
    }

    private static void verifiedArtifactIsReportedWithoutInstallHandoff() throws Exception {
        File[] downloadedFile = new File[1];
        UpdaterCore.VerificationResult result = UpdaterCore.downloadAndVerify(
                validRelease(), UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true,
                release -> { downloadedFile[0] = writeTemp(APK_BYTES); return downloadedFile[0]; },
                apk -> { check(apk.isFile(), "temporary APK is available to the verifier"); return identity(); });
        check(result.status == UpdaterCore.VerificationStatus.VERIFIED, "verified release returns a verification-only result");
        check(result.verifiedApk != null && result.verifiedApk.isFile(),
                "verified APK remains available for user-directed saving");
        check(downloadedFile[0] != null && !downloadedFile[0].exists(),
                "unverified staging file is renamed only after every verification passes");
        check(result.verifiedApk.getName().endsWith(".verified.apk"),
                "verified artifact has a distinct cache filename from partial downloads");
        check(result.verifiedApk.delete(), "caller can discard the verified temporary APK after saving or cancellation");
    }

    private static void verifiedArtifactRecoverySurvivesRestartAndRevalidates() throws Exception {
        File root = Files.createTempDirectory("daymark-updater-recovery-").toFile();
        File storageDirectory = new File(root, "updater-no-backup");
        File taskDataDirectory = new File(root, "tasks");
        check(storageDirectory.mkdirs() && taskDataDirectory.mkdirs(),
                "host recovery storage is persistent app-private and separate from task data");
        File taskData = new File(taskDataDirectory, "encrypted-tasks.data");
        Files.write(taskData.toPath(), new byte[] { 7, 8, 9 });

        UpdaterRecoveryStore firstProcess = new UpdaterRecoveryStore(storageDirectory);
        UpdaterCore.Release release = validRelease();
        firstProcess.recordPending(release);
        UpdaterRecoveryStore.PendingUpdate firstRecord = firstProcess.readPending();
        check(firstProcess.hasPendingRecord() && firstRecord != null,
                "release expectations are persisted before transfer for crash-safe recovery");
        File verifiedApk = firstRecord.verifiedApk;
        check(verifiedApk.getParentFile().mkdirs(), "verified-cache directory is created");
        try (FileOutputStream output = new FileOutputStream(verifiedApk)) { output.write(APK_BYTES); }

        UpdaterRecoveryStore restartedProcess = new UpdaterRecoveryStore(storageDirectory);
        UpdaterRecoveryStore.PendingUpdate recovered = restartedProcess.readPending();
        check(recovered != null && recovered.verifiedApk.isFile()
                        && recovered.verifiedApk.getName().equals(UpdaterCore.verifiedArtifactFileName(release.apkSha256)),
                "restart recovers the deterministic verified artifact using its persisted expected metadata");

        byte[] original = Files.readAllBytes(recovered.verifiedApk.toPath());
        try (FileOutputStream output = new FileOutputStream(recovered.verifiedApk)) { output.write(new byte[] { 1, 2, 3 }); }
        expectFailure(UpdaterCore.Failure.APK_MISMATCH,
                () -> UpdaterCore.verifyDownloadedArtifact(recovered.release, recovered.verifiedApk,
                        UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER, apk -> identity()),
                "recovery rechecks the stored expected digest before offering a file");
        check(recovered.verifiedApk.isFile() && restartedProcess.hasPendingRecord(),
                "failed digest revalidation preserves the artifact and recovery record for explicit discard");
        try (FileOutputStream output = new FileOutputStream(recovered.verifiedApk)) { output.write(original); }

        expectFailure(UpdaterCore.Failure.SIGNER_MISMATCH,
                () -> UpdaterCore.verifyDownloadedArtifact(recovered.release, recovered.verifiedApk,
                        UpdaterCore.APPLICATION_ID, 1L, 35, repeat('b', 64), repeat('b', 64), apk -> identity()),
                "recovery rechecks the installed/publisher signer before offering a file");
        expectFailure(UpdaterCore.Failure.APK_MISMATCH,
                () -> UpdaterCore.verifyDownloadedArtifact(recovered.release, recovered.verifiedApk,
                        "com.attacker.app", 1L, 35, SIGNER, SIGNER, apk -> identity()),
                "recovery rechecks the installed package identity before offering a file");
        UpdaterCore.verifyDownloadedArtifact(recovered.release, recovered.verifiedApk,
                UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER, apk -> identity());
        check(recovered.verifiedApk.isFile(), "valid recovery remains available after successful revalidation");

        restartedProcess.removeAfterUserChoice(recovered.release);
        check(!recovered.verifiedApk.exists() && !restartedProcess.hasPendingRecord(),
                "only an explicit user discard removes the retained APK and recovery record");
        check(taskData.isFile(), "explicit updater discard leaves task data untouched");
        deleteTree(root);
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

    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
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

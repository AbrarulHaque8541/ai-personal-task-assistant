package com.cue.daymark.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Pure-Java policy and verification logic for the opt-in GitHub APK updater. */
public final class UpdaterCore {
    public static final String APPLICATION_ID = "com.cue.daymark";
    public static final long CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L;
    public static final long MAX_APK_BYTES = 100L * 1024L * 1024L;
    private static final String RELEASE_ASSET_PREFIX =
            "/AbrarulHaque8541/ai-personal-task-assistant/releases/download/";

    private UpdaterCore() { }

    public enum Failure {
        OFFLINE,
        RATE_LIMITED,
        NETWORK_POLICY,
        HTTP,
        INVALID_METADATA,
        DOWNLOAD,
        CANCELLED,
        APK_MISMATCH,
        SIGNER_MISMATCH
    }

    public static final class UpdateException extends Exception {
        public final Failure failure;

        public UpdateException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public UpdateException(Failure failure, String message, Throwable cause) {
            super(message, cause);
            this.failure = failure;
        }
    }

    public interface ReleaseClient {
        /** Returns the newest stable release, or null when no stable release exists. */
        Release fetchLatestStable() throws UpdateException;
    }

    public interface Consent {
        /** Called before any APK bytes are downloaded. */
        boolean accept(Release release);
    }

    public interface Downloader {
        /** Writes the APK into private temporary storage and returns that file. */
        File download(Release release) throws UpdateException;

        /** Lets interactive downloaders honor cancellation through the verification boundary. */
        default boolean isCancelled() { return false; }
    }

    public interface ApkVerifier {
        ApkIdentity inspect(File apk) throws UpdateException;
    }

    public enum CheckStatus { UPDATE_AVAILABLE, NO_UPDATE }

    public static final class CheckResult {
        public final CheckStatus status;
        public final Release release;

        private CheckResult(CheckStatus status, Release release) {
            this.status = status;
            this.release = release;
        }

        public static CheckResult available(Release release) {
            return new CheckResult(CheckStatus.UPDATE_AVAILABLE, release);
        }

        public static CheckResult noUpdate() {
            return new CheckResult(CheckStatus.NO_UPDATE, null);
        }
    }

    public enum VerificationStatus { CANCELLED, VERIFIED }

    public static final class Release {
        public final String tag;
        public final String versionName;
        public final String name;
        public final String notes;
        public final String applicationId;
        public final long versionCode;
        public final int minSdkVersion;
        public final long apkSizeBytes;
        public final String apkSha256;
        public final String signerSha256;
        public final String assetUrl;
        public final boolean draft;
        public final boolean prerelease;

        public Release(String tag, String versionName, String name, String notes,
                String applicationId, long versionCode, int minSdkVersion,
                long apkSizeBytes, String apkSha256, String signerSha256,
                String assetUrl, boolean draft, boolean prerelease) {
            this.tag = tag;
            this.versionName = versionName;
            this.name = name;
            this.notes = notes;
            this.applicationId = applicationId;
            this.versionCode = versionCode;
            this.minSdkVersion = minSdkVersion;
            this.apkSizeBytes = apkSizeBytes;
            this.apkSha256 = apkSha256;
            this.signerSha256 = signerSha256;
            this.assetUrl = assetUrl;
            this.draft = draft;
            this.prerelease = prerelease;
        }
    }

    public static final class ApkIdentity {
        public final String applicationId;
        public final String versionName;
        public final long versionCode;
        public final int minSdkVersion;
        public final String signerSha256;

        public ApkIdentity(String applicationId, String versionName, long versionCode,
                int minSdkVersion, String signerSha256) {
            this.applicationId = applicationId;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.minSdkVersion = minSdkVersion;
            this.signerSha256 = signerSha256;
        }
    }

    public static final class VerificationResult {
        public final VerificationStatus status;
        public final File verifiedApk;

        private VerificationResult(VerificationStatus status, File verifiedApk) {
            this.status = status;
            this.verifiedApk = verifiedApk;
        }
    }

    /** Wall-clock rate limit, persisted by the Android UI; manual checks pass force=true. */
    public static boolean shouldCheck(long lastAttemptMillis, long nowMillis, boolean force) {
        if (force || lastAttemptMillis <= 0L || nowMillis < lastAttemptMillis) return true;
        return nowMillis - lastAttemptMillis >= CHECK_INTERVAL_MILLIS;
    }

    public static CheckResult check(ReleaseClient client, String installedApplicationId,
            long installedVersionCode) throws UpdateException {
        if (!APPLICATION_ID.equals(installedApplicationId)) {
            throw invalid("Running package ID is not the Daymark application ID.");
        }
        final Release release = client.fetchLatestStable();
        if (release == null || release.draft || release.prerelease) return CheckResult.noUpdate();
        validateRelease(release);
        if (release.versionCode <= installedVersionCode) return CheckResult.noUpdate();
        return CheckResult.available(release);
    }

    /**
     * Consent is asked before download. Any failed check prevents a verified result and
     * removes the private temporary file. A verified file is returned for user-directed saving.
     */
    public static VerificationResult downloadAndVerify(Release release,
            String installedApplicationId, long installedVersionCode, int deviceSdk,
            String runningSignerSha256, String configuredPublisherSignerSha256,
            Consent consent, Downloader downloader, ApkVerifier verifier) throws UpdateException {
        if (release == null || consent == null || downloader == null || verifier == null) {
            throw invalid("Updater operation is missing a required component.");
        }
        validateRelease(release);
        if (release.draft || release.prerelease || release.versionCode <= installedVersionCode) {
            throw invalid("Release is not a newer stable update.");
        }
        if (!APPLICATION_ID.equals(installedApplicationId)
                || !APPLICATION_ID.equals(release.applicationId)) {
            throw new UpdateException(Failure.APK_MISMATCH, "Update package ID does not match Daymark.");
        }
        if (deviceSdk < release.minSdkVersion) {
            throw new UpdateException(Failure.APK_MISMATCH, "This update requires a newer Android version.");
        }
        String configuredSigner = normalizeSha256(configuredPublisherSignerSha256);
        String runningSigner = normalizeSha256(runningSignerSha256);
        String releaseSigner = normalizeSha256(release.signerSha256);
        if (configuredSigner == null || runningSigner == null
                || !configuredSigner.equals(runningSigner)
                || !releaseSigner.equals(runningSigner)) {
            throw new UpdateException(Failure.SIGNER_MISMATCH,
                    "Publisher, release, and installed-app signing certificates do not match.");
        }
        if (!consent.accept(release)) return new VerificationResult(VerificationStatus.CANCELLED, null);

        File temporaryApk = null;
        boolean keepVerifiedApk = false;
        try {
            temporaryApk = downloader.download(release);
            ensureNotCancelled(downloader);
            if (temporaryApk == null || !temporaryApk.isFile()) {
                throw new UpdateException(Failure.DOWNLOAD, "APK download did not produce a file.");
            }
            long actualBytes = temporaryApk.length();
            if (actualBytes != release.apkSizeBytes || actualBytes <= 0L || actualBytes > MAX_APK_BYTES) {
                throw new UpdateException(Failure.APK_MISMATCH, "Downloaded APK size did not match release metadata.");
            }
            String actualHash = sha256(temporaryApk);
            ensureNotCancelled(downloader);
            if (!normalizeSha256(actualHash).equals(normalizeSha256(release.apkSha256))) {
                throw new UpdateException(Failure.APK_MISMATCH, "Downloaded APK SHA-256 did not match release metadata.");
            }
            ApkIdentity apk = verifier.inspect(temporaryApk);
            ensureNotCancelled(downloader);
            if (apk == null || !APPLICATION_ID.equals(apk.applicationId)
                    || !release.applicationId.equals(apk.applicationId)
                    || !release.versionName.equals(apk.versionName)
                    || release.versionCode != apk.versionCode
                    || release.minSdkVersion != apk.minSdkVersion
                    || apk.minSdkVersion > deviceSdk) {
                throw new UpdateException(Failure.APK_MISMATCH,
                        "APK package ID, version, or minimum Android version did not match release metadata.");
            }
            String apkSigner = normalizeSha256(apk.signerSha256);
            if (apkSigner == null || !apkSigner.equals(releaseSigner)
                    || !apkSigner.equals(runningSigner) || !apkSigner.equals(configuredSigner)) {
                throw new UpdateException(Failure.SIGNER_MISMATCH,
                        "APK signing certificate does not match the installed app and publisher configuration.");
            }
            ensureNotCancelled(downloader);
            temporaryApk = promoteVerifiedFile(temporaryApk);
            keepVerifiedApk = true;
            return new VerificationResult(VerificationStatus.VERIFIED, temporaryApk);
        } catch (UpdateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UpdateException(Failure.APK_MISMATCH, "Downloaded APK could not be safely verified.", exception);
        } finally {
            if (!keepVerifiedApk && temporaryApk != null && temporaryApk.exists()) {
                // Failed or cancelled downloads never leave an APK behind.
                temporaryApk.delete();
            }
        }
    }

    private static void ensureNotCancelled(Downloader downloader) throws UpdateException {
        if (downloader.isCancelled()) {
            throw new UpdateException(Failure.CANCELLED, "APK download or verification was cancelled.");
        }
    }

    private static File promoteVerifiedFile(File temporaryApk) throws UpdateException {
        String name = temporaryApk.getName();
        String verifiedName = name.endsWith(".partial")
                ? name.substring(0, name.length() - ".partial".length()) + ".verified.apk"
                : name + ".verified.apk";
        File verifiedApk = new File(temporaryApk.getParentFile(), verifiedName);
        if (verifiedApk.exists() || !temporaryApk.renameTo(verifiedApk)) {
            throw new UpdateException(Failure.DOWNLOAD,
                    "Verified APK could not be safely finalized in private storage.");
        }
        return verifiedApk;
    }

    public static void validateRelease(Release release) throws UpdateException {
        if (release == null || release.tag == null || !release.tag.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+")
                || release.versionName == null || !release.versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || !release.versionName.equals(release.tag.startsWith("v") ? release.tag.substring(1) : release.tag)
                || release.name == null || release.name.trim().isEmpty()
                || release.notes == null
                || !APPLICATION_ID.equals(release.applicationId)
                || release.versionCode <= 0L
                || release.minSdkVersion <= 0
                || release.apkSizeBytes <= 0L || release.apkSizeBytes > MAX_APK_BYTES
                || normalizeSha256(release.apkSha256) == null
                || normalizeSha256(release.signerSha256) == null
                || !isAllowedAssetUrl(release.assetUrl)) {
            throw invalid("Release metadata is incomplete or does not match Daymark's updater format.");
        }
    }

    public static boolean isAllowedAssetUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && path != null
                    && path.startsWith(RELEASE_ASSET_PREFIX)
                    && path.endsWith(".apk");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** Metadata checks stay disabled unless both network access and a pinned publisher are configured. */
    public static boolean isNetworkCheckAllowed(boolean internetPermissionGranted,
            boolean publisherConfigurationValid) {
        return internetPermissionGranted && publisherConfigurationValid;
    }

    /** Accept only the original GitHub release URL or known GitHub release-asset redirect hosts. */
    public static boolean isAllowedAssetRedirectUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            if ("github.com".equalsIgnoreCase(host)) {
                String path = uri.getPath();
                return uri.getQuery() == null && path != null
                        && path.startsWith(RELEASE_ASSET_PREFIX) && path.endsWith(".apk");
            }
            return "release-assets.githubusercontent.com".equalsIgnoreCase(host)
                    || "objects.githubusercontent.com".equalsIgnoreCase(host)
                    || "github-production-release-asset-2e65be.s3.amazonaws.com".equalsIgnoreCase(host);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String normalizeSha256(String value) {
        if (value == null) return null;
        String normalized = value.replace(":", "").trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[0-9a-f]{64}") ? normalized : null;
    }

    public static String sha256(File file) throws UpdateException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new UpdateException(Failure.DOWNLOAD, "Unable to verify the downloaded APK hash.", exception);
        }
    }

    private static UpdateException invalid(String message) {
        return new UpdateException(Failure.INVALID_METADATA, message);
    }
}

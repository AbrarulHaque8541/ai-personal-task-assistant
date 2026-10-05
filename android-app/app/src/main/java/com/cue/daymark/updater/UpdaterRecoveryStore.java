package com.cue.daymark.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Persists one app-private recovery record for an already-verified update, outside task data. */
public final class UpdaterRecoveryStore {
    private static final String RECORD_NAME = "daymark-pending-verified-update.properties";
    private static final String RECORD_TEMP_NAME = RECORD_NAME + ".tmp";
    private static final String TEMP_DIRECTORY_NAME = "daymark-update-tmp";
    private static final String FORMAT_VERSION = "1";

    public static final class PendingUpdate {
        public final UpdaterCore.Release release;
        public final File verifiedApk;

        private PendingUpdate(UpdaterCore.Release release, File verifiedApk) {
            this.release = release;
            this.verifiedApk = verifiedApk;
        }
    }

    private final File storageDirectory;
    private final File recordFile;

    public UpdaterRecoveryStore(File storageDirectory) {
        if (storageDirectory == null) {
            throw new IllegalArgumentException("Persistent updater storage directory is required.");
        }
        this.storageDirectory = storageDirectory;
        this.recordFile = new File(storageDirectory, RECORD_NAME);
    }

    public synchronized boolean hasPendingRecord() {
        return recordFile.isFile();
    }

    /** Write before transfer so a process death immediately after promotion remains recoverable. */
    public synchronized void recordPending(UpdaterCore.Release release)
            throws IOException, UpdaterCore.UpdateException {
        UpdaterCore.validateRelease(release);
        if (release.draft || release.prerelease) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA,
                    "Only a stable release can be retained for recovery.");
        }
        if (recordFile.exists()) throw new IOException("A pending update recovery record already exists.");
        if (!storageDirectory.isDirectory() && !storageDirectory.mkdirs()) {
            throw new IOException("Could not create app-private update recovery storage.");
        }

        Properties properties = new Properties();
        properties.setProperty("formatVersion", FORMAT_VERSION);
        properties.setProperty("tag", release.tag);
        properties.setProperty("versionName", release.versionName);
        properties.setProperty("name", release.name);
        properties.setProperty("notes", release.notes);
        properties.setProperty("applicationId", release.applicationId);
        properties.setProperty("versionCode", Long.toString(release.versionCode));
        properties.setProperty("minSdkVersion", Integer.toString(release.minSdkVersion));
        properties.setProperty("apkSizeBytes", Long.toString(release.apkSizeBytes));
        properties.setProperty("apkSha256", UpdaterCore.normalizeSha256(release.apkSha256));
        properties.setProperty("signerSha256", UpdaterCore.normalizeSha256(release.signerSha256));
        properties.setProperty("assetUrl", release.assetUrl);
        properties.setProperty("draft", Boolean.toString(release.draft));
        properties.setProperty("prerelease", Boolean.toString(release.prerelease));
        properties.setProperty("verifiedFileName", UpdaterCore.verifiedArtifactFileName(release.apkSha256));

        File temporaryRecord = new File(storageDirectory, RECORD_TEMP_NAME);
        try (FileOutputStream output = new FileOutputStream(temporaryRecord, false)) {
            properties.store(output, "Daymark update recovery metadata; not task data");
            output.flush();
            output.getFD().sync();
        }
        if (recordFile.exists() || !temporaryRecord.renameTo(recordFile)) {
            throw new IOException("Could not atomically persist update recovery metadata.");
        }
    }

    public synchronized PendingUpdate readPending()
            throws IOException, UpdaterCore.UpdateException {
        if (!recordFile.isFile()) return null;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(recordFile)) {
            properties.load(input);
        }
        if (!FORMAT_VERSION.equals(required(properties, "formatVersion"))) {
            throw new IOException("Unsupported update recovery record format.");
        }
        UpdaterCore.Release release;
        try {
            release = new UpdaterCore.Release(
                    required(properties, "tag"),
                    required(properties, "versionName"),
                    required(properties, "name"),
                    required(properties, "notes"),
                    required(properties, "applicationId"),
                    Long.parseLong(required(properties, "versionCode")),
                    Integer.parseInt(required(properties, "minSdkVersion")),
                    Long.parseLong(required(properties, "apkSizeBytes")),
                    required(properties, "apkSha256"),
                    required(properties, "signerSha256"),
                    required(properties, "assetUrl"),
                    Boolean.parseBoolean(required(properties, "draft")),
                    Boolean.parseBoolean(required(properties, "prerelease")));
        } catch (NumberFormatException exception) {
            throw new IOException("Update recovery record contains invalid numeric metadata.", exception);
        }
        UpdaterCore.validateRelease(release);
        if (release.draft || release.prerelease) {
            throw new IOException("Update recovery record is not for a stable release.");
        }
        String expectedFileName = UpdaterCore.verifiedArtifactFileName(release.apkSha256);
        if (!expectedFileName.equals(required(properties, "verifiedFileName"))) {
            throw new IOException("Update recovery record filename does not match its digest.");
        }
        File verifiedDirectory = verifiedDirectory();
        File verifiedApk = new File(verifiedDirectory, expectedFileName);
        File canonicalVerifiedApk = verifiedApk.getCanonicalFile();
        if (!canonicalVerifiedApk.getParentFile().equals(verifiedDirectory.getCanonicalFile())
                || !canonicalVerifiedApk.getName().equals(expectedFileName)) {
            throw new IOException("Update recovery file escaped its private cache directory.");
        }
        return new PendingUpdate(release, verifiedApk);
    }

    /** Clear a failed/cancelled transfer only when no promoted verified APK exists. */
    public synchronized boolean clearIfNoVerifiedArtifact(UpdaterCore.Release release)
            throws IOException, UpdaterCore.UpdateException {
        if (!recordFile.isFile()) return true;
        PendingUpdate pending = readPending();
        if (pending == null || !sameDigest(pending.release.apkSha256, release.apkSha256)) return false;
        if (pending.verifiedApk.exists()) return false;
        deleteRecordFile();
        return true;
    }

    /** Explicit user discard, or completion after the user saved a copy to their chosen location. */
    public synchronized void removeAfterUserChoice(UpdaterCore.Release release)
            throws IOException, UpdaterCore.UpdateException {
        File verifiedApk = verifiedFileFor(release);
        if (verifiedApk.exists() && !verifiedApk.delete()) {
            throw new IOException("Could not remove the explicitly discarded verified APK.");
        }
        if (!recordFile.isFile()) return;
        PendingUpdate pending = readPending();
        if (pending != null && sameDigest(pending.release.apkSha256, release.apkSha256)) {
            deleteRecordFile();
        }
    }

    /** Returns only direct-child digest-addressed files; does not delete or open them. */
    public synchronized List<File> listVerifiedArtifacts() throws IOException {
        File directory = verifiedDirectory();
        File[] children = directory.listFiles();
        List<File> result = new ArrayList<>();
        if (children == null) return result;
        File canonicalDirectory = directory.getCanonicalFile();
        for (File child : children) {
            if (!child.isFile() || !UpdaterCore.isVerifiedArtifactFileName(child.getName())) continue;
            File canonicalChild = child.getCanonicalFile();
            if (canonicalDirectory.equals(canonicalChild.getParentFile())
                    && canonicalChild.getName().equals(child.getName())) result.add(child);
        }
        return result;
    }

    /** Explicit orphan discard; the strict filename and canonical parent fence deletion. */
    public synchronized void discardOrphanAfterUserChoice(File artifact) throws IOException {
        if (!isSafeVerifiedArtifact(artifact)) {
            throw new IOException("Refusing to remove a file outside updater verified storage.");
        }
        if (artifact.exists() && !artifact.delete()) {
            throw new IOException("Could not remove the explicitly discarded verified APK.");
        }
        if (listVerifiedArtifacts().isEmpty() && recordFile.isFile()) deleteRecordFile();
    }

    /** Explicitly clear invalid metadata only after the UI has told the user no APK can be offered. */
    public synchronized void clearInvalidRecordAfterUserChoice() throws IOException {
        if (listVerifiedArtifacts().isEmpty()) deleteRecordFile();
    }

    private File verifiedFileFor(UpdaterCore.Release release) throws IOException, UpdaterCore.UpdateException {
        UpdaterCore.validateRelease(release);
        return new File(verifiedDirectory(), UpdaterCore.verifiedArtifactFileName(release.apkSha256));
    }

    private File verifiedDirectory() {
        return new File(storageDirectory, TEMP_DIRECTORY_NAME);
    }

    private boolean isSafeVerifiedArtifact(File artifact) throws IOException {
        if (artifact == null || !UpdaterCore.isVerifiedArtifactFileName(artifact.getName())) return false;
        File expectedParent = verifiedDirectory().getCanonicalFile();
        File canonical = artifact.getCanonicalFile();
        return expectedParent.equals(canonical.getParentFile())
                && canonical.getName().equals(artifact.getName());
    }

    private void deleteRecordFile() throws IOException {
        if (recordFile.exists() && !recordFile.delete()) {
            throw new IOException("Could not clear updater recovery metadata.");
        }
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) throw new IOException("Update recovery record is incomplete.");
        return value;
    }

    private static boolean sameDigest(String first, String second) {
        String normalizedFirst = UpdaterCore.normalizeSha256(first);
        String normalizedSecond = UpdaterCore.normalizeSha256(second);
        return normalizedFirst != null && normalizedFirst.equals(normalizedSecond);
    }
}

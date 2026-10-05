package com.cue.daymark.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Copies a verified APK to the new document returned by ACTION_CREATE_DOCUMENT.
 * A provider-dependent rename marks the document incomplete before writing and
 * finalizes it only after copy and independent destination read-back verification.
 * SAF providers do not promise universally atomic rename/finalization semantics.
 */
public final class SafApkSaver {
    private static final String INCOMPLETE_MARKER = ".daymark-incomplete-";

    /** Narrow adapter around one app-created SAF document URI. */
    public interface Document {
        String displayName() throws IOException;

        /** Rename this same document and return its actual new display name, or null on failure. */
        String renameTo(String displayName) throws IOException;

        /** Open this same document for truncating output; never resolve by display name. */
        OutputStream openForWrite() throws IOException;

        /** Independently reopen this same document for read-back verification. */
        InputStream openForRead() throws IOException;

        /** Best-effort removal of this app-created document only. */
        boolean delete() throws IOException;
    }

    private SafApkSaver() { }

    /** Compatibility path for providers/tests whose initial picker name is already the desired name. */
    public static void copyVerifiedApk(File source, UpdaterCore.Release release,
            Document destination, String uniqueToken) throws IOException, UpdaterCore.UpdateException {
        copyVerifiedApk(source, release, destination, uniqueToken, null, null);
    }

    /**
     * Writes only after the picker-created document has been visibly staged. If the provider returns
     * the unique temporary picker title unchanged, it is finalized to preferredFinalName; an
     * explicitly user-renamed document keeps the user's selected name. Transfer/read-back failure
     * triggers best-effort deletion, while process death leaves the staging marker rather than a
     * final-looking APK.
     */
    public static void copyVerifiedApk(File source, UpdaterCore.Release release,
            Document destination, String uniqueToken, String expectedPickerTitle, String preferredFinalName)
            throws IOException, UpdaterCore.UpdateException {
        try {
            UpdaterCore.validateRelease(release);
        } catch (UpdaterCore.UpdateException exception) {
            if (destination != null) bestEffortDelete(destination);
            throw exception;
        }
        if (destination == null) {
            throw new IOException("A verified source APK and newly created destination are required.");
        }
        if (source == null || !source.isFile()) {
            bestEffortDelete(destination);
            throw new IOException("A verified source APK and newly created destination are required.");
        }
        if (source.length() != release.apkSizeBytes || source.length() > UpdaterCore.MAX_APK_BYTES) {
            bestEffortDelete(destination);
            throw new IOException("The retained APK size no longer matches verified release metadata.");
        }
        if (uniqueToken == null || !uniqueToken.matches("[A-Za-z0-9_-]{1,64}")) {
            bestEffortDelete(destination);
            throw new IOException("A safe unique temporary-document token is required.");
        }
        if ((expectedPickerTitle == null) != (preferredFinalName == null)
                || (expectedPickerTitle != null && (!isSafeDisplayName(expectedPickerTitle)
                || !isSafeDisplayName(preferredFinalName)))) {
            bestEffortDelete(destination);
            throw new IOException("The suggested and final document names are invalid.");
        }

        String selectedName;
        try {
            selectedName = destination.displayName();
        } catch (IOException exception) {
            bestEffortDelete(destination);
            throw exception;
        }
        if (!isSafeDisplayName(selectedName)) {
            bestEffortDelete(destination);
            throw new IOException("The selected document did not provide a safe display name.");
        }
        String finalName = expectedPickerTitle != null && expectedPickerTitle.equals(selectedName)
                ? preferredFinalName : selectedName;
        String incompleteName = finalName + INCOMPLETE_MARKER + uniqueToken;
        boolean contentVerified = false;
        try {
            String stagedName = destination.renameTo(incompleteName);
            if (!incompleteName.equals(stagedName)) {
                throw new IOException("The document provider could not mark the new destination as incomplete.");
            }

            MessageDigest copyDigest = MessageDigest.getInstance("SHA-256");
            long copied = 0L;
            try (FileInputStream input = new FileInputStream(source);
                    OutputStream output = destination.openForWrite()) {
                if (output == null) throw new IOException("The selected location could not be opened.");
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    copied += count;
                    if (copied > release.apkSizeBytes || copied > UpdaterCore.MAX_APK_BYTES) {
                        throw new IOException("The saved APK exceeded its verified size.");
                    }
                    copyDigest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
            if (copied != release.apkSizeBytes
                    || !hexDigest(copyDigest.digest()).equalsIgnoreCase(release.apkSha256)) {
                throw new IOException("The copied APK did not match the verified release size and digest.");
            }

            MessageDigest readBackDigest = MessageDigest.getInstance("SHA-256");
            long readBackBytes = 0L;
            try (InputStream readBack = destination.openForRead()) {
                if (readBack == null) {
                    throw new IOException("The selected location could not be read back for verification.");
                }
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = readBack.read(buffer)) != -1) {
                    readBackBytes += count;
                    if (readBackBytes > release.apkSizeBytes || readBackBytes > UpdaterCore.MAX_APK_BYTES) {
                        throw new IOException("The saved document read-back exceeded its verified size.");
                    }
                    readBackDigest.update(buffer, 0, count);
                }
            }
            if (readBackBytes != release.apkSizeBytes
                    || !hexDigest(readBackDigest.digest()).equalsIgnoreCase(release.apkSha256)) {
                throw new IOException("The destination read-back did not match the verified release size and digest.");
            }
            contentVerified = true;

            String finalizedName = destination.renameTo(finalName);
            if (finalizedName == null || finalizedName.contains(INCOMPLETE_MARKER + uniqueToken)) {
                throw new IOException("The APK bytes were verified, but the document provider could not safely finalize the name; any retained copy remains marked incomplete.");
            }
        } catch (IOException | RuntimeException exception) {
            if (!contentVerified) bestEffortDelete(destination);
            throw exception;
        } catch (NoSuchAlgorithmException exception) {
            if (!contentVerified) bestEffortDelete(destination);
            throw new IOException("SHA-256 verification is unavailable.", exception);
        }
    }

    private static boolean isSafeDisplayName(String name) {
        return name != null && !name.trim().isEmpty() && !".".equals(name) && !"..".equals(name)
                && name.indexOf('/') < 0 && name.indexOf('\\') < 0
                && name.indexOf('\n') < 0 && name.indexOf('\r') < 0;
    }

    /** Exact reserved title used to safely identify and clean a stale returned picker URI. */
    public static boolean isReservedPendingPickerName(String name) {
        return name != null && name.matches(
                "Daymark-v[0-9]+\\.[0-9]+\\.[0-9]+\\.apk\\.daymark-pending-[a-f0-9]{32}\\.tmp");
    }

    private static void bestEffortDelete(Document destination) {
        try {
            destination.delete();
        } catch (Exception ignored) {
            // A provider may refuse deletion; the staging name remains visibly marked incomplete.
        }
    }

    private static String hexDigest(byte[] bytes) {
        StringBuilder result = new StringBuilder(64);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}

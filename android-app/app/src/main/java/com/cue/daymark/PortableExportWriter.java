package com.cue.daymark;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Writes a staged portable backup to the app-created SAF document returned by
 * {@code ACTION_CREATE_DOCUMENT}.
 *
 * <p>The destination is not a fresh file that this app owns: Android hands back an already-created
 * document, so the previous implementation opened it truncated ({@code "w"}) and started streaming.
 * A process death or provider error part-way through left a partially written file still carrying
 * its final {@code daymark-backup.dmbackup} name, so a truncated archive could be mistaken for a
 * complete backup and its recovery key trusted.
 *
 * <p>This mirrors {@link com.cue.daymark.updater.SafApkSaver}'s contract for the same reason:
 * <ol>
 *   <li>rename the app-created document to a conspicuous incomplete marker <em>before</em> writing;</li>
 *   <li>stream the staged archive, verifying the byte count and the SHA-256 of the bytes written;</li>
 *   <li>independently reopen the document and re-read it, comparing the read-back digest;</li>
 *   <li>only then rename it back to the final name.</li>
 * </ol>
 * A crash therefore leaves the marked name, never a final-looking partial backup.
 *
 * <p>The writer is pure Java with an injected document adapter, so the interrupted-export contract
 * is exercised by host smoke tests as well as on a device.
 */
final class PortableExportWriter {
    /** Reserved marker that makes an interrupted write conspicuous to the user. */
    static final String INCOMPLETE_MARKER = ".daymark-incomplete-";

    /** Narrow adapter around one app-created SAF document URI. */
    interface Document {
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

    interface CancellationCheck {
        boolean isCancelled();
    }

    private PortableExportWriter() {
    }

    /**
     * Copy {@code stagedArchive} into {@code destination} and finalize it only after the destination
     * has been independently verified as byte-identical.
     *
     * @return the name the document was finalized under
     */
    static String write(File stagedArchive, Document destination, String uniqueToken,
                        String preferredFinalName, CancellationCheck cancellation) throws IOException {
        if (destination == null) {
            throw new IOException("A newly created destination document is required.");
        }
        if (stagedArchive == null || !stagedArchive.isFile()) {
            throw new IOException("The staged encrypted backup is missing.");
        }
        if (uniqueToken == null || !uniqueToken.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IOException("A safe unique temporary-document token is required.");
        }
        if (!isSafeDisplayName(preferredFinalName)) {
            throw new IOException("The preferred backup file name is invalid.");
        }
        long expectedBytes = stagedArchive.length();
        if (expectedBytes <= 0 || expectedBytes > PortableBackupCodec.MAX_ARCHIVE_BYTES) {
            throw new IOException("The staged encrypted backup has an invalid size.");
        }
        byte[] expectedDigest = digestOf(stagedArchive);

        String selectedName = destination.displayName();
        if (selectedName != null && selectedName.trim().isEmpty()) selectedName = null;
        // If the provider kept our suggested title, finalize to the desired name; if the user renamed
        // the document in the picker, honour the user's chosen name. A provider that reports no name
        // at all falls back to the suggested title.
        String finalName = selectedName == null || preferredFinalName.equals(selectedName)
                ? preferredFinalName : selectedName;
        if (!isSafeDisplayName(finalName)) {
            throw new IOException("The selected document did not provide a safe display name.");
        }
        String incompleteName = finalName + INCOMPLETE_MARKER + uniqueToken;

        boolean contentVerified = false;
        try {
            String stagedName = destination.renameTo(incompleteName);
            if (!incompleteName.equals(stagedName)) {
                throw new IOException(
                        "The document provider could not mark the new backup as incomplete before writing.");
            }

            MessageDigest copyDigest = newDigest();
            long copied = 0L;
            try (InputStream input = new FileInputStream(stagedArchive);
                    OutputStream output = destination.openForWrite()) {
                if (output == null) throw new IOException("The selected location could not be opened.");
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled(cancellation);
                    copied += count;
                    if (copied > expectedBytes) {
                        throw new IOException("The written backup exceeded its staged size.");
                    }
                    copyDigest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.flush();
                checkCancelled(cancellation);
            }
            if (copied != expectedBytes || !MessageDigest.isEqual(expectedDigest, copyDigest.digest())) {
                throw new IOException("The written backup did not match the staged archive size and digest.");
            }

            MessageDigest readBackDigest = newDigest();
            long readBackBytes = 0L;
            try (InputStream readBack = destination.openForRead()) {
                if (readBack == null) {
                    throw new IOException("The saved backup could not be read back for verification.");
                }
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = readBack.read(buffer)) != -1) {
                    readBackBytes += count;
                    if (readBackBytes > expectedBytes) {
                        throw new IOException("The saved backup read-back exceeded its staged size.");
                    }
                    readBackDigest.update(buffer, 0, count);
                }
            }
            if (readBackBytes != expectedBytes
                    || !MessageDigest.isEqual(expectedDigest, readBackDigest.digest())) {
                throw new IOException("The saved backup read-back did not match the staged archive.");
            }
            contentVerified = true;

            String finalizedName = destination.renameTo(finalName);
            if (finalizedName == null || finalizedName.contains(INCOMPLETE_MARKER + uniqueToken)) {
                throw new IOException("The backup bytes were verified, but the document provider could not "
                        + "finalize the name; any retained copy remains visibly marked incomplete.");
            }
            return finalizedName;
        } catch (IOException | RuntimeException exception) {
            if (!contentVerified) bestEffortDelete(destination);
            throw exception;
        }
    }

    /** True when the provider's name still carries an interrupted-write marker. */
    static boolean isMarkedIncomplete(String displayName) {
        return displayName != null && displayName.contains(INCOMPLETE_MARKER);
    }

    private static boolean isSafeDisplayName(String name) {
        return name != null && !name.trim().isEmpty() && !".".equals(name) && !"..".equals(name)
                && name.indexOf('/') < 0 && name.indexOf('\\') < 0
                && name.indexOf('\n') < 0 && name.indexOf('\r') < 0;
    }

    private static void checkCancelled(CancellationCheck cancellation) throws IOException {
        if (cancellation != null && cancellation.isCancelled()) {
            throw new IOException("Portable backup cancelled.");
        }
    }

    private static MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 verification is unavailable.", exception);
        }
    }

    private static byte[] digestOf(File file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return digest.digest();
    }

    private static void bestEffortDelete(Document destination) {
        try {
            destination.delete();
        } catch (Exception ignored) {
            // A provider may refuse deletion; the staging name still leaves the copy conspicuously
            // marked incomplete rather than looking like a finished backup.
        }
    }
}

package com.cue.daymark;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Android document-provider adapter; the selected URI is used once and is never saved. */
final class AndroidAttachmentStore {
    private static final String KEY_ALIAS = "daymark.attachment-payload.aes-gcm.v1";
    private static final Object TRANSACTION_LOCK = new Object();
    private final ContentResolver resolver;
    private final AttachmentBlobStore blobs;

    AndroidAttachmentStore(Context context) {
        resolver = context.getContentResolver();
        blobs = new AttachmentBlobStore(new File(context.getNoBackupFilesDir(), "attachments"),
                new AndroidKeyAccess());
    }

    static Object transactionLock() {
        return TRANSACTION_LOCK;
    }

    Imported importSelected(Uri uri, String taskId, String appOwnedId, long remainingTotalBytes,
                            AttachmentBlobStore.CancellationCheck cancellation) throws Exception {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            throw new IOException("The selected document provider returned an unsupported item.");
        }
        String displayName = AttachmentLogic.sanitizeDisplayName(readDisplayName(uri));
        String mimeType = AttachmentLogic.normalizeMimeType(resolver.getType(uri));
        long reportedSize = readReportedSize(uri);
        long sizeHint = reportedSize >= 0 && reportedSize <= AttachmentLogic.MAX_FILE_BYTES
                && reportedSize <= remainingTotalBytes ? reportedSize : -1;
        InputStream input = resolver.openInputStream(uri);
        if (input == null) throw new IOException("The selected file could not be opened.");
        long copied;
        try (InputStream selected = input) {
            copied = blobs.importStream(taskId, appOwnedId, selected, remainingTotalBytes, sizeHint, cancellation);
        }
        return new Imported(appOwnedId, displayName, mimeType, copied);
    }

    boolean exists(String appOwnedId) {
        return blobs.exists(appOwnedId);
    }

    InputStream openDecrypted(String taskId, String appOwnedId) throws IOException {
        return blobs.openInput(taskId, appOwnedId);
    }

    void delete(String appOwnedId) throws IOException {
        blobs.delete(appOwnedId);
    }

    int cleanupOrphans(java.util.Set<String> referencedIds) throws IOException {
        return blobs.cleanupOrphans(referencedIds);
    }

    private String readDisplayName(Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } catch (RuntimeException ignored) {
            // A provider may omit metadata; use a generic safe display name and still try the stream.
        }
        return "Untitled attachment";
    }

    private long readReportedSize(Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[] { OpenableColumns.SIZE }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (column >= 0 && !cursor.isNull(column)) return cursor.getLong(column);
            }
        } catch (RuntimeException ignored) {
            // Unknown provider size is still bounded while reading the stream.
        }
        return -1;
    }

    static final class Imported {
        final String id;
        final String displayName;
        final String mimeType;
        final long sizeBytes;

        Imported(String id, String displayName, String mimeType, long sizeBytes) {
            this.id = id;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
        }
    }

    /** Package-scoped for instrumentation of the real Android Keystore-backed blob path. */
    static final class AndroidKeyAccess implements AttachmentBlobStore.KeyAccess {
        @Override
        public SecretKey loadExistingKey() throws IOException {
            KeyStore keyStore = loadKeyStore();
            try {
                if (!keyStore.containsAlias(KEY_ALIAS)) throw new IOException("The attachment encryption key is missing.");
                Key key = keyStore.getKey(KEY_ALIAS, null);
                if (!(key instanceof SecretKey)) throw new IOException("The attachment encryption key is unavailable.");
                return (SecretKey) key;
            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException("The attachment encryption key is unavailable.", exception);
            }
        }

        @Override
        public SecretKey createKeyForNewStore() throws IOException {
            KeyStore keyStore = loadKeyStore();
            try {
                if (keyStore.containsAlias(KEY_ALIAS)) return loadExistingKey();
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                KeyGenParameterSpec specification = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build();
                generator.init(specification);
                return generator.generateKey();
            } catch (Exception exception) {
                throw new IOException("An attachment encryption key could not be created.", exception);
            }
        }

        private static KeyStore loadKeyStore() throws IOException {
            try {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                return keyStore;
            } catch (Exception exception) {
                throw new IOException("Android Keystore is unavailable for attachments.", exception);
            }
        }
    }
}

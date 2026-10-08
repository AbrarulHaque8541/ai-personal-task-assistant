package com.cue.daymark;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.IOException;

/**
 * Reads a SAF document's display name through its own URI.
 *
 * <p>Both the picker cleanup path and the portable-export writer must name a document from the exact
 * URI they were handed by ACTION_CREATE_DOCUMENT. Resolving a document by display name could match a
 * different, unrelated file, so this helper only ever queries the URI it is given.
 */
final class SafDocumentNames {
    private SafDocumentNames() {
    }

    /** Return the provider's display name for this exact document, or null when it is unavailable. */
    static String displayName(ContentResolver resolver, Uri documentUri) throws IOException {
        if (resolver == null || documentUri == null) {
            throw new IOException("The selected document could not be identified.");
        }
        try (Cursor cursor = resolver.query(documentUri,
                new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0 && !cursor.isNull(column)) return cursor.getString(column);
            }
        } catch (RuntimeException failure) {
            throw new IOException("The selected document's name could not be read.", failure);
        }
        return null;
    }
}

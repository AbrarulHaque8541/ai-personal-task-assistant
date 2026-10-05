package com.cue.daymark;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Read-only, non-exported streaming bridge for an explicitly user-shared attachment. */
public final class AttachmentContentProvider extends ContentProvider {
    private AndroidAttachmentStore attachmentStore;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return false;
        attachmentStore = new AndroidAttachmentStore(context.getApplicationContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        AttachmentBinding binding = findBinding(uri);
        AttachmentRef reference = binding == null ? null : binding.reference;
        return reference == null || !AttachmentLogic.isSafeToOpenExternally(reference)
                ? null : reference.mimeType;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        AttachmentBinding binding = findBinding(uri);
        AttachmentRef reference = binding == null ? null : binding.reference;
        if (reference == null || !AttachmentLogic.isSafeToOpenExternally(reference)) return null;
        String[] columns = projection == null
                ? new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE } : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) values[index] = reference.displayName;
            else if (OpenableColumns.SIZE.equals(columns[index])) values[index] = reference.sizeBytes;
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Attachments are read-only.");
        AttachmentBinding binding = findBinding(uri);
        AttachmentRef reference = binding == null ? null : binding.reference;
        if (reference == null || !AttachmentLogic.isSafeToOpenExternally(reference)
                || attachmentStore == null || !attachmentStore.exists(reference.id)) {
            throw new FileNotFoundException("The attachment is no longer available.");
        }
        return openPipeHelper(uri, reference.mimeType, null, binding,
                (pipe, ignoredUri, ignoredType, ignoredOptions, attachment) -> {
                    try (InputStream input = attachmentStore.openDecrypted(attachment.taskId,
                            attachment.reference.id);
                         OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe)) {
                        byte[] buffer = new byte[32 * 1024];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (count > 0) output.write(buffer, 0, count);
                        }
                    } catch (IOException ignored) {
                        try { pipe.close(); } catch (IOException closeIgnored) { }
                    }
                });
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    private AttachmentBinding findBinding(Uri uri) {
        if (uri == null || getContext() == null || attachmentStore == null
                || !"content".equals(uri.getScheme())
                || !(getContext().getPackageName() + ".attachments").equals(uri.getAuthority())
                || uri.getPathSegments().size() != 1) return null;
        String id = uri.getLastPathSegment();
        if (!AttachmentLogic.isValidId(id)) return null;
        try {
            List<Task> tasks = new EncryptedTaskStore(getContext()).load();
            for (Task task : tasks) {
                for (AttachmentRef reference : task.attachments) {
                    if (reference.id.equals(id)) return new AttachmentBinding(task.id, reference);
                }
            }
        } catch (Exception ignored) {
            // Fail closed if the encrypted task snapshot is unavailable.
        }
        return null;
    }

    private static final class AttachmentBinding {
        final String taskId;
        final AttachmentRef reference;

        AttachmentBinding(String taskId, AttachmentRef reference) {
            this.taskId = taskId;
            this.reference = reference;
        }
    }
}

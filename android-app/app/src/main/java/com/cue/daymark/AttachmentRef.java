package com.cue.daymark;

/** Encrypted task-snapshot metadata for one app-owned attachment payload. */
final class AttachmentRef {
    final String id;
    final String displayName;
    final String mimeType;
    final long sizeBytes;

    AttachmentRef(String id, String displayName, String mimeType, long sizeBytes) {
        this.id = id;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }
}

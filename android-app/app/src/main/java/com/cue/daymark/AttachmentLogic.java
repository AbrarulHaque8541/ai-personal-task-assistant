package com.cue.daymark;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Rules shared by the UI, encrypted task snapshot validation, and JDK smoke tests. */
final class AttachmentLogic {
    static final long MAX_FILE_BYTES = 20L * 1024L * 1024L;
    static final long MAX_TOTAL_BYTES = 100L * 1024L * 1024L;
    static final int MAX_PER_TASK = 5;
    static final int MAX_TOTAL_COUNT = 100;
    static final int MAX_NAME_CHARS = 120;
    private static final Pattern MIME_TYPE = Pattern.compile(
            "[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,63}");
    private static final Set<String> SAFE_EXTERNAL_MIME_TYPES = new HashSet<>(java.util.Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif",
            "audio/mpeg", "audio/mp4", "audio/aac", "audio/wav", "audio/x-wav", "audio/ogg", "audio/flac",
            "video/mp4", "video/webm", "video/3gpp", "video/quicktime",
            "application/pdf", "text/plain", "text/csv", "text/markdown", "text/rtf", "application/rtf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation"));
    private static final Set<String> UNSAFE_EXTENSIONS = new HashSet<>(java.util.Arrays.asList(
            "apk", "dex", "jar", "class", "so", "exe", "dll", "msi", "com", "bat", "cmd",
            "sh", "bash", "zsh", "ps1", "py", "pyc", "js", "mjs", "html", "htm", "xhtml",
            "svg", "wasm", "scr", "app", "deb", "rpm", "docm", "xlsm", "pptm"));

    private AttachmentLogic() { }

    static String sanitizeDisplayName(String candidate) {
        if (candidate == null) return "Untitled attachment";
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < candidate.length() && safe.length() < MAX_NAME_CHARS; index++) {
            char value = candidate.charAt(index);
            if (Character.isISOControl(value) || value == '/' || value == '\\' || value == '\u202e'
                    || value == '\u202d' || value == '\u2066' || value == '\u2067'
                    || value == '\u2068' || value == '\u2069') {
                safe.append('_');
            } else {
                safe.append(value);
            }
        }
        String result = safe.toString().trim();
        while (result.startsWith(".")) result = result.substring(1).trim();
        if (result.isEmpty() || ".".equals(result) || "..".equals(result)) {
            return "Untitled attachment";
        }
        return result;
    }

    static String normalizeMimeType(String candidate) {
        if (candidate == null) return "application/octet-stream";
        String normalized = candidate.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 129 || !MIME_TYPE.matcher(normalized).matches()) {
            return "application/octet-stream";
        }
        return normalized;
    }

    static boolean isValidId(String id) {
        if (id == null) return false;
        try {
            UUID parsed = UUID.fromString(id);
            return parsed.version() == 4 && parsed.variant() == 2 && parsed.toString().equals(id);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean isValid(AttachmentRef attachment) {
        return attachment != null && isValidId(attachment.id)
                && attachment.displayName != null
                && attachment.displayName.equals(sanitizeDisplayName(attachment.displayName))
                && attachment.displayName.length() <= MAX_NAME_CHARS
                && attachment.mimeType != null
                && attachment.mimeType.equals(normalizeMimeType(attachment.mimeType))
                && attachment.sizeBytes >= 0 && attachment.sizeBytes <= MAX_FILE_BYTES;
    }

    static boolean isValidTaskAttachments(List<AttachmentRef> attachments) {
        if (attachments == null || attachments.size() > MAX_PER_TASK) return false;
        Set<String> ids = new HashSet<>();
        for (AttachmentRef attachment : attachments) {
            if (!isValid(attachment) || !ids.add(attachment.id)) return false;
        }
        return true;
    }

    static boolean canAddToTask(Task task, List<Task> allTasks) {
        if (task == null || allTasks == null || task.attachments == null
                || task.attachments.size() >= MAX_PER_TASK) return false;
        int count = 0;
        long bytes = 0;
        for (Task candidate : allTasks) {
            if (candidate.attachments == null) return false;
            count += candidate.attachments.size();
            for (AttachmentRef attachment : candidate.attachments) {
                if (attachment == null || attachment.sizeBytes < 0) return false;
                if (bytes > MAX_TOTAL_BYTES - attachment.sizeBytes) return false;
                bytes += attachment.sizeBytes;
            }
        }
        return count < MAX_TOTAL_COUNT && bytes < MAX_TOTAL_BYTES;
    }

    static long remainingBytes(List<Task> tasks) {
        if (tasks == null) return 0;
        long bytes = 0;
        for (Task task : tasks) {
            if (task == null || task.attachments == null) return 0;
            for (AttachmentRef attachment : task.attachments) {
                if (attachment == null || attachment.sizeBytes < 0
                        || bytes > MAX_TOTAL_BYTES - attachment.sizeBytes) return 0;
                bytes += attachment.sizeBytes;
            }
        }
        return MAX_TOTAL_BYTES - bytes;
    }

    static boolean isSafeToOpenExternally(AttachmentRef attachment) {
        if (!isValid(attachment)) return false;
        String lowerName = attachment.displayName.toLowerCase(Locale.ROOT);
        int dot = lowerName.lastIndexOf('.');
        if (dot >= 0 && UNSAFE_EXTENSIONS.contains(lowerName.substring(dot + 1))) return false;
        if ("image/svg+xml".equals(attachment.mimeType) || "text/html".equals(attachment.mimeType)
                || "application/xhtml+xml".equals(attachment.mimeType)
                || "application/javascript".equals(attachment.mimeType)
                || "application/vnd.android.package-archive".equals(attachment.mimeType)
                || attachment.mimeType.contains("macroenabled") || attachment.mimeType.contains("macro-enabled")) {
            return false;
        }
        return SAFE_EXTERNAL_MIME_TYPES.contains(attachment.mimeType);
    }

    static boolean isValidTaskListAttachments(List<Task> tasks) {
        if (tasks == null) return false;
        int count = 0;
        long bytes = 0;
        Set<String> ids = new HashSet<>();
        for (Task task : tasks) {
            if (task == null || !isValidTaskAttachments(task.attachments)) return false;
            count += task.attachments.size();
            for (AttachmentRef attachment : task.attachments) {
                if (!ids.add(attachment.id) || bytes > MAX_TOTAL_BYTES - attachment.sizeBytes) return false;
                bytes += attachment.sizeBytes;
            }
        }
        return count <= MAX_TOTAL_COUNT && bytes <= MAX_TOTAL_BYTES;
    }
}

package com.cue.daymark;

import java.net.URI;
import java.util.Locale;

/** Fail-closed policy for direct media URLs exposed by the current page's HTML media elements. */
final class BrowserMediaUrlPolicy {
    private BrowserMediaUrlPolicy() { }

    static boolean isAllowedDirectMediaUrl(String url, String mediaType) {
        if (!BrowserAddress.isAllowedWebUrl(url)) return false;
        final String path;
        try {
            path = new URI(url).getPath();
        } catch (Exception invalidUrl) {
            return false;
        }
        if (path == null || path.isEmpty()) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".m3u8") || lower.endsWith(".mpd")
                || lower.endsWith(".ism") || lower.endsWith(".ismc")
                || lower.endsWith(".m4s") || lower.endsWith(".ts")) {
            return false;
        }
        if (lower.matches(".*\\.(mp4|webm|m4v|mov|mkv|mp3|m4a|aac|ogg|oga|opus|wav|flac)$")) {
            return true;
        }
        String type = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
        return type.equals("video/mp4") || type.equals("video/webm")
                || type.equals("video/ogg") || type.equals("audio/mpeg")
                || type.equals("audio/mp4") || type.equals("audio/aac")
                || type.equals("audio/ogg") || type.equals("audio/wav")
                || type.equals("audio/webm");
    }
}

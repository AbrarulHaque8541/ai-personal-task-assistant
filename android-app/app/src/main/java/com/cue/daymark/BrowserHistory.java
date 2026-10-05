package com.cue.daymark;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Local history stores only HTTPS origin/path; query, fragment, and userinfo are never persisted. */
final class BrowserHistory {
    static final int MAX_ENTRIES = 50;

    private BrowserHistory() { }

    static String sanitizeUrl(String address) {
        if (address == null || address.trim().isEmpty()) return null;
        try {
            URI source = new URI(address.trim());
            if (!"https".equalsIgnoreCase(source.getScheme())) return null;
            String host = source.getHost();
            if (host == null || host.trim().isEmpty()) return null;

            host = host.toLowerCase(Locale.ROOT);
            if (host.indexOf(':') >= 0 && !(host.startsWith("[") && host.endsWith("]"))) {
                host = "[" + host + "]";
            }
            StringBuilder safe = new StringBuilder("https://").append(host);
            if (source.getPort() >= 0) safe.append(':').append(source.getPort());
            String path = source.getRawPath();
            safe.append(path == null || path.isEmpty() ? "/" : path);

            URI sanitized = new URI(safe.toString());
            if (sanitized.getRawUserInfo() != null || sanitized.getRawQuery() != null
                    || sanitized.getRawFragment() != null) return null;
            return sanitized.toASCIIString();
        } catch (Exception invalidUrl) {
            return null;
        }
    }

    static List<String> decode(String serialized) {
        LinkedHashSet<String> addresses = new LinkedHashSet<>();
        if (serialized != null && !serialized.isEmpty()) {
            for (String line : serialized.split("\\n")) {
                if (line.isEmpty()) continue;
                try {
                    String address = new String(Base64.getUrlDecoder().decode(line), StandardCharsets.UTF_8);
                    String safeAddress = sanitizeUrl(address);
                    if (safeAddress != null) addresses.add(safeAddress);
                } catch (IllegalArgumentException ignored) {
                    // Ignore invalid local history instead of failing browser startup.
                }
            }
        }
        List<String> result = new ArrayList<>();
        for (String address : addresses) {
            if (result.size() == MAX_ENTRIES) break;
            result.add(address);
        }
        return result;
    }

    static String add(String serialized, String address) {
        String safeAddress = sanitizeUrl(address);
        if (safeAddress == null) {
            throw new IllegalArgumentException("Only valid HTTPS origin/path values can be saved in browser history.");
        }
        List<String> entries = decode(serialized);
        entries.remove(safeAddress);
        entries.add(0, safeAddress);
        return encode(entries);
    }

    static String encode(List<String> addresses) {
        LinkedHashSet<String> safeAddresses = new LinkedHashSet<>();
        if (addresses != null) {
            for (String address : addresses) {
                String safeAddress = sanitizeUrl(address);
                if (safeAddress != null) safeAddresses.add(safeAddress);
                if (safeAddresses.size() == MAX_ENTRIES) break;
            }
        }
        StringBuilder encoded = new StringBuilder();
        int index = 0;
        for (String address : safeAddresses) {
            if (index++ > 0) encoded.append('\n');
            encoded.append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(address.getBytes(StandardCharsets.UTF_8)));
        }
        return encoded.toString();
    }

    static String sanitizeSerialized(String serialized) {
        return encode(decode(serialized));
    }

    static String clear() {
        return "";
    }
}

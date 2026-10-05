package com.cue.daymark;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;

/** Local history storage format. Callers persist the returned value only in app-private storage. */
final class BrowserHistory {
    static final int MAX_ENTRIES = 50;

    private BrowserHistory() { }

    static List<String> decode(String serialized) {
        LinkedHashSet<String> addresses = new LinkedHashSet<>();
        if (serialized != null && !serialized.isEmpty()) {
            for (String line : serialized.split("\\n")) {
                if (line.isEmpty()) continue;
                try {
                    String address = new String(Base64.getUrlDecoder().decode(line), StandardCharsets.UTF_8);
                    if (BrowserAddress.isAllowedWebUrl(address)) addresses.add(address);
                } catch (IllegalArgumentException ignored) {
                    // Ignore an invalid local history entry instead of failing browser startup.
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
        String validAddress = BrowserAddress.requireAllowedWebUrl(address);
        List<String> entries = decode(serialized);
        entries.remove(validAddress);
        entries.add(0, validAddress);
        StringBuilder encoded = new StringBuilder();
        for (int index = 0; index < Math.min(entries.size(), MAX_ENTRIES); index++) {
            if (index > 0) encoded.append('\n');
            encoded.append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(entries.get(index).getBytes(StandardCharsets.UTF_8)));
        }
        return encoded.toString();
    }

    static String clear() {
        return "";
    }
}

package org.json;

import com.cue.daymark.StrictJsonParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal host-test stand-in for Android's org.json.JSONObject. Exists only under
 * tools/host-stubs so the extension package parser can run in host smoke tests
 * without the Android runtime; it is never included in app sources. Backed by the
 * production StrictJsonParser, so parse behavior matches real JSON input handling.
 */
public class JSONObject {
    private final Map<String, Object> values;

    public JSONObject() {
        this.values = new LinkedHashMap<>();
    }

    public JSONObject(String json) {
        this.values = StrictJsonParser.object(StrictJsonParser.parse(json), "JSON input");
    }

    public JSONObject(Map<String, Object> values) {
        this.values = values;
    }

    public Object opt(String key) {
        return values.get(key);
    }

    public boolean has(String key) {
        return values.get(key) != null;
    }

    public JSONObject put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public String optString(String key) {
        return optString(key, "");
    }

    public String optString(String key, String fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        return value instanceof String ? (String) value : String.valueOf(value);
    }

    public boolean optBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text)) return false;
        }
        return fallback;
    }

    public JSONArray optJSONArray(String key) {
        Object value = values.get(key);
        if (!(value instanceof List)) return null;
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) value;
        return new JSONArray(list);
    }

    public JSONObject optJSONObject(String key) {
        Object value = values.get(key);
        if (!(value instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return new JSONObject(map);
    }

    @Override
    public String toString() {
        return JsonWriter.write(values);
    }

    public String toString(int indentFactor) {
        // Indentation is not observable in host tests; a single-line document is produced.
        return JsonWriter.write(values);
    }

    Map<String, Object> backing() {
        return values;
    }
}

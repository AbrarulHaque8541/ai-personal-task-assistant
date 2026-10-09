package org.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal host-test stand-in for Android's org.json.JSONArray; host-stubs only.
 * Mirrors only the accessor surface the extension package parser uses.
 */
public class JSONArray {
    private final List<Object> values;

    public JSONArray() {
        this.values = new ArrayList<>();
    }

    public JSONArray(List<Object> values) {
        this.values = values;
    }

    public JSONArray put(Object value) {
        values.add(value);
        return this;
    }

    public int length() {
        return values.size();
    }

    public Object opt(int index) {
        return index >= 0 && index < values.size() ? values.get(index) : null;
    }

    public String optString(int index, String fallback) {
        Object value = opt(index);
        if (value == null) return fallback;
        return value instanceof String ? (String) value : String.valueOf(value);
    }

    public JSONObject optJSONObject(int index) {
        Object value = opt(index);
        if (!(value instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return new JSONObject(map);
    }

    @Override
    public String toString() {
        return JsonWriter.write(values);
    }

    List<Object> backing() {
        return values;
    }
}

package com.cue.daymark;

/** Strict scalar-type checks for the encrypted task snapshot schema. */
final class TaskSnapshotSchema {
    private TaskSnapshotSchema() { }

    static boolean isVersionOne(Object value) {
        return value instanceof Number && ((Number) value).doubleValue() == 1.0d;
    }

    static String requireString(Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("A task snapshot field has the wrong JSON type.");
        }
        return (String) value;
    }
}

package com.cue.daymark;

/** Strict scalar-type checks for the encrypted task snapshot schema. */
final class TaskSnapshotSchema {
    private TaskSnapshotSchema() { }

    static boolean isVersionOne(Object value) {
        return value instanceof Number && ((Number) value).doubleValue() == 1.0d;
    }

    static boolean isVersionTwo(Object value) {
        return value instanceof Number && ((Number) value).doubleValue() == 2.0d;
    }

    static boolean isVersionThree(Object value) {
        return value instanceof Number && ((Number) value).doubleValue() == 3.0d;
    }

    static boolean isSupportedVersion(Object value) {
        return isVersionOne(value) || isVersionTwo(value) || isVersionThree(value);
    }

    static String requireString(Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("A task snapshot field has the wrong JSON type.");
        }
        return (String) value;
    }
}

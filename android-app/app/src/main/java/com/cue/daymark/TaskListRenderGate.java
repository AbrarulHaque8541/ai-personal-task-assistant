package com.cue.daymark;

import java.util.List;

/** Cheap fingerprint so task list UI can skip identical rebuilds. */
final class TaskListRenderGate {
    private String lastKey = "";

    private TaskListRenderGate() {}

    static TaskListRenderGate create() {
        return new TaskListRenderGate();
    }

    /** @return true if the list should be rebuilt (key changed). */
    boolean shouldRebuild(String filter, String query, List<Task> visible) {
        String key = fingerprint(filter, query, visible);
        if (key.equals(lastKey)) {
            return false;
        }
        lastKey = key;
        return true;
    }

    void invalidate() {
        lastKey = "";
    }

    private static String fingerprint(String filter, String query, List<Task> visible) {
        StringBuilder sb = new StringBuilder(64 + (visible == null ? 0 : visible.size() * 24));
        sb.append(filter == null ? "" : filter).append('|');
        sb.append(query == null ? "" : query).append('|');
        if (visible != null) {
            for (Task t : visible) {
                if (t == null) {
                    continue;
                }
                sb.append(t.id == null ? "" : t.id).append(':');
                sb.append(t.completed).append(':');
                sb.append(t.updatedAt == null ? "" : t.updatedAt).append(';');
            }
        }
        return sb.toString();
    }
}

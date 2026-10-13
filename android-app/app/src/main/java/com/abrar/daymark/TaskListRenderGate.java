package com.abrar.daymark;

import java.util.List;

/** Cheap fingerprint so task list UI can skip identical rebuilds. */
final class TaskListRenderGate {
    private String lastKey = "";

    TaskListRenderGate() {}

    static TaskListRenderGate create() {
        return new TaskListRenderGate();
    }

    /** @return true if the list should be rebuilt (key changed). */
    boolean shouldRebuild(String filter, String query, List<Task> visible, boolean saveFailed) {
        String key = fingerprint(filter, query, visible, saveFailed);
        if (key.equals(lastKey)) {
            return false;
        }
        lastKey = key;
        return true;
    }

    void invalidate() {
        lastKey = "";
    }

    private static String fingerprint(String filter, String query, List<Task> visible, boolean saveFailed) {
        StringBuilder sb = new StringBuilder(64 + (visible == null ? 0 : visible.size() * 32));
        sb.append(filter == null ? "" : filter).append('|');
        sb.append(query == null ? "" : query).append('|');
        sb.append(saveFailed).append('|');
        if (visible != null) {
            for (Task t : visible) {
                if (t == null) {
                    continue;
                }
                sb.append(t.id == null ? "" : t.id).append(':');
                sb.append(t.completed).append(':');
                sb.append(t.priority == null ? "" : t.priority).append(':');
                sb.append(t.dueDate == null ? "" : t.dueDate).append(':');
                sb.append(t.updatedAt == null ? "" : t.updatedAt).append(';');
            }
        }
        return sb.toString();
    }
}

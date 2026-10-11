package com.cue.daymark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JDK-only regression tests for TaskListRenderGate fingerprinting. */
public final class TaskListRenderGateSmoke {
    private TaskListRenderGateSmoke() {}

    public static void main(String[] args) {
        TaskListRenderGate gate = TaskListRenderGate.create();
        List<Task> list = new ArrayList<>();
        list.add(new Task("t1", "Buy groceries", "2026-10-15", "high", false, "2026-10-11T10:00:00Z", "2026-10-11T10:00:00Z"));

        // First render should trigger rebuild
        assert gate.shouldRebuild("all", "", list, false) : "initial list should require rebuild";

        // Identical parameters should skip rebuild
        assert !gate.shouldRebuild("all", "", list, false) : "identical list should skip rebuild";

        // Query change should require rebuild
        assert gate.shouldRebuild("all", "buy", list, false) : "query change should require rebuild";
        assert !gate.shouldRebuild("all", "buy", list, false) : "repeated query should skip rebuild";

        // Filter change should require rebuild
        assert gate.shouldRebuild("today", "buy", list, false) : "filter change should require rebuild";

        // Save failed flag change should require rebuild
        assert gate.shouldRebuild("today", "buy", list, true) : "save failure state change should require rebuild";

        // Task state modification (completion toggle) should require rebuild
        List<Task> updatedList = new ArrayList<>();
        updatedList.add(list.get(0).withCompleted(true, "2026-10-11T10:05:00Z"));
        assert gate.shouldRebuild("today", "buy", updatedList, true) : "task modification should require rebuild";

        // Invalidation should force rebuild
        gate.invalidate();
        assert gate.shouldRebuild("today", "buy", updatedList, true) : "invalidated gate should require rebuild";

        System.out.println("PASS task list render gate: skips identical rebuilds and detects changes");
    }
}

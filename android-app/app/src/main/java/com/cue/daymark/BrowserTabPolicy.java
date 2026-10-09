package com.cue.daymark;

/** Small deterministic rules for the embedded browser's in-memory tab strip. */
final class BrowserTabPolicy {
    static final int MAX_TABS = 6;

    private BrowserTabPolicy() { }

    static boolean canCreate(int openTabCount) {
        return openTabCount >= 0 && openTabCount < MAX_TABS;
    }

    /** Return a valid next index after removing the tab at oldIndex. */
    static int indexAfterClose(int oldIndex, int remainingCount) {
        if (remainingCount <= 0) return -1;
        if (oldIndex < 0) return 0;
        return Math.min(Math.max(oldIndex - 1, 0), remainingCount - 1);
    }
}

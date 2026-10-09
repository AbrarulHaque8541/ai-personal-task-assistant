package com.cue.daymark;

/** JDK-only regression tests for bounded browser tab state. */
public final class BrowserTabPolicySmoke {
    private BrowserTabPolicySmoke() { }

    public static void main(String[] args) {
        assert BrowserTabPolicy.canCreate(0) : "first tab should be allowed";
        assert BrowserTabPolicy.canCreate(BrowserTabPolicy.MAX_TABS - 1) : "last available slot should be allowed";
        assert !BrowserTabPolicy.canCreate(BrowserTabPolicy.MAX_TABS) : "tab count must be bounded";
        assert !BrowserTabPolicy.canCreate(-1) : "invalid tab counts must be rejected";
        assert BrowserTabPolicy.indexAfterClose(0, 0) == -1 : "closing the last tab should leave no selection";
        assert BrowserTabPolicy.indexAfterClose(2, 2) == 1 : "closing a later tab should select the previous tab";
        assert BrowserTabPolicy.indexAfterClose(0, 3) == 0 : "closing the first tab should select the new first tab";
        assert BrowserTabPolicy.indexAfterClose(99, 2) == 1 : "selection must remain within the remaining tab list";
        System.out.println("PASS browser tab policy: bounded tab count and safe next-selection after close");
    }
}

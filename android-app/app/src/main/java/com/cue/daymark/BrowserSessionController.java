package com.cue.daymark;

import java.util.ArrayList;
import java.util.List;

/** Pause/resume WebView tabs without destroying them (background performance). */
final class BrowserSessionController {
    private BrowserSessionController() {}

    static void pauseAll(List<DaymarkWebView> tabs) {
        if (tabs == null) return;
        for (DaymarkWebView tab : new ArrayList<>(tabs)) {
            if (tab == null) continue;
            try {
                tab.onPause();
                tab.pauseTimers();
            } catch (RuntimeException ignored) {
            }
        }
    }

    static void resumeAll(List<DaymarkWebView> tabs) {
        if (tabs == null) return;
        for (DaymarkWebView tab : new ArrayList<>(tabs)) {
            if (tab == null) continue;
            try {
                tab.resumeTimers();
                tab.onResume();
            } catch (RuntimeException ignored) {
            }
        }
    }
}

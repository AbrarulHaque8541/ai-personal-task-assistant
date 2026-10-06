package com.cue.daymark.updater;

/** Pure request/result classification shared by the updater callback and host regression tests. */
public final class UpdaterActivityResultRouter {
    public static final int REQUEST_CODE = 7344;

    public enum Route {
        NOT_UPDATER,
        CANCELLED,
        SELECTED
    }

    private UpdaterActivityResultRouter() {
    }

    /**
     * Classifies whether a picker result belongs to the updater and whether it contains a selection.
     * Unrelated request codes are left for the Activity's other result handlers.
     */
    public static Route route(int requestCode, boolean resultOk, boolean hasUri) {
        if (requestCode != REQUEST_CODE) {
            return Route.NOT_UPDATER;
        }
        return resultOk && hasUri ? Route.SELECTED : Route.CANCELLED;
    }
}

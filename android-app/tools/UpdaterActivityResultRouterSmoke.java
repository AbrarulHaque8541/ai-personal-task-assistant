package com.cue.daymark.updater;

public final class UpdaterActivityResultRouterSmoke {
    private UpdaterActivityResultRouterSmoke() {
    }

    public static void main(String[] args) {
        require(UpdaterActivityResultRouter.REQUEST_CODE == 7344,
                "updater picker request code must remain 7344");
        expect(UpdaterActivityResultRouter.Route.SELECTED,
                UpdaterActivityResultRouter.route(
                        UpdaterActivityResultRouter.REQUEST_CODE, true, true),
                "matching successful picker result dispatches to updater");
        expect(UpdaterActivityResultRouter.Route.CANCELLED,
                UpdaterActivityResultRouter.route(
                        UpdaterActivityResultRouter.REQUEST_CODE, false, false),
                "matching picker cancellation dispatches to updater cancellation path");
        expect(UpdaterActivityResultRouter.Route.CANCELLED,
                UpdaterActivityResultRouter.route(
                        UpdaterActivityResultRouter.REQUEST_CODE, true, false),
                "matching result without a URI is treated as cancellation");
        expect(UpdaterActivityResultRouter.Route.NOT_UPDATER,
                UpdaterActivityResultRouter.route(7341, true, true),
                "unrelated attachment result remains available to its Activity handler");
        System.out.println("Updater activity-result routing checks passed (4 routes).");
    }

    private static void expect(UpdaterActivityResultRouter.Route expected,
            UpdaterActivityResultRouter.Route actual, String description) {
        if (expected != actual) {
            throw new AssertionError(description + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}

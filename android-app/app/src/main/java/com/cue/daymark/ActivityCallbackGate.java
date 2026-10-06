package com.cue.daymark;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Drops callbacks that have not started by the time their Activity is destroyed. */
final class ActivityCallbackGate {
    private final AtomicBoolean open = new AtomicBoolean(true);

    boolean isOpen() {
        return open.get();
    }

    void close() {
        open.set(false);
    }

    Runnable guard(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        return () -> {
            if (open.get()) callback.run();
        };
    }
}

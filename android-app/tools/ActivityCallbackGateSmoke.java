package com.cue.daymark;

import java.util.concurrent.atomic.AtomicInteger;

public final class ActivityCallbackGateSmoke {
    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        ActivityCallbackGate gate = new ActivityCallbackGate();

        gate.guard(calls::incrementAndGet).run();
        if (calls.get() != 1) throw new AssertionError("open gate dropped a callback");

        Runnable queuedBeforeDestroy = gate.guard(calls::incrementAndGet);
        gate.close();
        queuedBeforeDestroy.run();
        if (calls.get() != 1) throw new AssertionError("queued callback ran after gate closure");

        gate.guard(calls::incrementAndGet).run();
        gate.close();
        if (calls.get() != 1) throw new AssertionError("closed gate accepted a new callback");

        System.out.println("PASS ActivityCallbackGate: queued UI work is dropped after Activity destruction");
    }
}

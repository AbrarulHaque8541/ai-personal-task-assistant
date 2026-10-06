package com.cue.daymark;

/** Serializes reminder delivery, recovery, replacement, and cancellation within Daymark's single app process. */
final class ReminderDeliveryLock {
    static final Object LOCK = new Object();

    private ReminderDeliveryLock() { }
}

package com.eu.habbo.habbohotel.wired.core;

/** Bounds signal fan-out within one synchronous WIRED event chain. */
final class WiredSignalChainBudget {
    private final int maximumEvents;
    private final long maximumDurationNanos;
    private final long startedAtNanos = System.nanoTime();
    private int admittedEvents;
    private boolean limitReported;
    private boolean aborted;

    WiredSignalChainBudget(int maximumEvents, int maximumDurationMs) {
        this.maximumEvents = maximumEvents;
        this.maximumDurationNanos = maximumDurationMs > 0 ? maximumDurationMs * 1_000_000L : 0L;
    }

    boolean tryAdmit() {
        if (this.isDurationExceeded()) {
            this.aborted = true;
            return false;
        }

        if (this.aborted) {
            return false;
        }

        if (this.maximumEvents <= 0) {
            return true;
        }

        if (this.admittedEvents >= this.maximumEvents) {
            this.aborted = true;
            return false;
        }

        this.admittedEvents++;
        return true;
    }

    boolean markLimitReported() {
        if (this.limitReported) {
            return false;
        }

        this.limitReported = true;
        return true;
    }

    int maximumEvents() {
        return this.maximumEvents;
    }

    int admittedEvents() {
        return this.admittedEvents;
    }

    boolean isExhausted() {
        return this.aborted || (this.maximumEvents > 0 && this.admittedEvents >= this.maximumEvents);
    }

    boolean isAborted() {
        return this.aborted;
    }

    boolean abortIfDurationExceeded() {
        if (this.admittedEvents > 0 && !this.aborted && this.isDurationExceeded()) {
            this.aborted = true;
        }
        return this.aborted;
    }

    String limitReason() {
        if (this.maximumEvents > 0 && this.admittedEvents >= this.maximumEvents) {
            return "event limit " + this.maximumEvents;
        }
        return "duration limit " + (this.maximumDurationNanos / 1_000_000L) + "ms";
    }

    private boolean isDurationExceeded() {
        return this.maximumDurationNanos > 0 && System.nanoTime() - this.startedAtNanos >= this.maximumDurationNanos;
    }
}

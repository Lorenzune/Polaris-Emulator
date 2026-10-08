package com.eu.habbo.habbohotel.wired.core;

import java.util.function.LongSupplier;

/** Slice limits yield work; the independent admission guard bounds runaway feedback. */
final class WiredSignalChainBudget {
    private final int maximumEvents;
    private final long maximumDurationNanos;
    private final int maximumTotalEvents;
    private final LongSupplier clock;
    private long startedAtNanos;
    private int admittedEvents;
    private int processedInSlice;
    private boolean limitReported;
    private boolean signalSeen;

    WiredSignalChainBudget(int maximumEvents, int maximumDurationMs) {
        this(maximumEvents, maximumDurationMs, 100_000, System::nanoTime);
    }

    WiredSignalChainBudget(int maximumEvents, int maximumDurationMs, int maximumTotalEvents, LongSupplier clock) {
        this.maximumEvents = maximumEvents;
        this.maximumDurationNanos = Math.max(0L, maximumDurationMs) * 1_000_000L;
        this.maximumTotalEvents = maximumTotalEvents;
        this.clock = clock;
        beginSlice();
    }

    boolean tryAdmit() {
        if (maximumTotalEvents > 0 && admittedEvents >= maximumTotalEvents) return false;
        admittedEvents++;
        return true;
    }

    void markSignal() { signalSeen = true; }

    void beginSlice() {
        startedAtNanos = clock.getAsLong();
        processedInSlice = 0;
    }

    void processed(int count) { processedInSlice += count; }

    boolean shouldYield() {
        return signalSeen && ((maximumEvents > 0 && processedInSlice >= maximumEvents)
                || (maximumDurationNanos > 0 && clock.getAsLong() - startedAtNanos >= maximumDurationNanos));
    }

    boolean markLimitReported() {
        if (limitReported) return false;
        limitReported = true;
        return true;
    }

    int admittedEvents() { return admittedEvents; }

    String limitReason() { return "chain admission limit " + maximumTotalEvents; }
}

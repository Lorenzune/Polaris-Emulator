package com.eu.habbo.habbohotel.wired.core;

/** Marks deliveries paced by the continuation queue rather than destructive caps. */
final class WiredSignalExecutionScope implements AutoCloseable {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    private final Integer previous;

    private WiredSignalExecutionScope() {
        previous = DEPTH.get();
        DEPTH.set(previous == null ? 1 : previous + 1);
    }

    static WiredSignalExecutionScope begin() {
        return new WiredSignalExecutionScope();
    }

    static boolean active() {
        return DEPTH.get() != null;
    }

    public void close() {
        if (previous == null) DEPTH.remove();
        else DEPTH.set(previous);
    }
}

package com.eu.habbo.habbohotel.wired.core;

/** SWF origin bits: this room, another room, inspection and external management. */
public final class WiredVariableChangeOrigin implements AutoCloseable {
    public static final int THIS_ROOM = 1;
    public static final int OTHER_ROOM = 2;
    public static final int INSPECTION = 4;
    public static final int EXTERNAL = 8;
    private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<>();
    private final Integer previous;
    private boolean closed;

    private WiredVariableChangeOrigin(int origin) {
        if (origin != 1 && origin != 2 && origin != 4 && origin != 8)
            throw new IllegalArgumentException("Invalid variable origin");
        previous = CURRENT.get();
        CURRENT.set(origin);
    }

    public static WiredVariableChangeOrigin begin(int origin) {
        return new WiredVariableChangeOrigin(origin);
    }

    public static int current() {
        Integer origin = CURRENT.get();
        return origin == null ? THIS_ROOM : origin;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}

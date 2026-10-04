package com.eu.habbo.messages.incoming.rooms;

/** Entry history uses Unix seconds; pending-load deadlines use monotonic-sized epoch milliseconds. */
final class RoomLoadTiming {
    private RoomLoadTiming() { }
    static boolean expired(long startedAtMillis, long nowMillis) {
        return startedAtMillis > 0 && nowMillis - startedAtMillis > 5000;
    }
    static boolean canStart(int loadingRoom, long enteredAt, long nowMillis) {
        long enteredAtMillis = enteredAt > 0 && enteredAt < 1_000_000_000_000L ? enteredAt * 1000 : enteredAt;
        return loadingRoom == 0 && nowMillis - enteredAtMillis > 1000;
    }
}

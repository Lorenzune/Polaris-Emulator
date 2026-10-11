package com.eu.habbo.habbohotel.users.levels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Bounded, coalesced checkpoints; database work never holds the producer lock. */
final class AvatarHostingQueue {
    @FunctionalInterface
    interface Writer {
        boolean write(int ownerId, String sessionId, long cumulativeMillis);
    }

    private record Checkpoint(int ownerId, String sessionId, long cumulativeMillis) {}

    private final LinkedHashMap<String, Checkpoint> pending = new LinkedHashMap<>();
    private final int capacity;
    private final Writer writer;

    AvatarHostingQueue(int capacity, Writer writer) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
        this.writer = java.util.Objects.requireNonNull(writer);
    }

    synchronized boolean offer(int ownerId, String sessionId, long cumulativeMillis) {
        if (ownerId <= 0 || cumulativeMillis <= 0) return true;
        java.util.Objects.requireNonNull(sessionId);
        Checkpoint old = pending.get(sessionId);
        if (old != null && old.ownerId() != ownerId) throw new IllegalArgumentException("Checkpoint owner changed");
        if (old == null && pending.size() >= capacity) return false;
        pending.put(
                sessionId,
                new Checkpoint(
                        ownerId,
                        sessionId,
                        old == null ? cumulativeMillis : Math.max(old.cumulativeMillis(), cumulativeMillis)));
        return true;
    }

    void drain(int limit) {
        List<Checkpoint> batch;
        synchronized (this) {
            batch = new ArrayList<>(pending.values().stream().limit(limit).toList());
        }
        for (Checkpoint checkpoint : batch) {
            if (writer.write(checkpoint.ownerId(), checkpoint.sessionId(), checkpoint.cumulativeMillis())) {
                synchronized (this) {
                    pending.remove(checkpoint.sessionId(), checkpoint);
                }
            }
        }
    }

    synchronized int size() {
        return pending.size();
    }
}

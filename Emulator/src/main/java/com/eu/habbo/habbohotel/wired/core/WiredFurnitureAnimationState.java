package com.eu.habbo.habbohotel.wired.core;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/** Reset epochs retained by movement snapshots, without retaining completed packets or room items. */
public final class WiredFurnitureAnimationState {
    private static final ConcurrentHashMap<Integer, EpochReference> CURRENT = new ConcurrentHashMap<>();
    private static final ReferenceQueue<Epoch> EXPIRED = new ReferenceQueue<>();

    private WiredFurnitureAnimationState() {}

    public static final class Epoch {
        private volatile boolean current = true;

        public boolean isCurrent() {
            return this.current;
        }
    }

    private static final class EpochReference extends WeakReference<Epoch> {
        final int itemId;

        EpochReference(int itemId, Epoch epoch) {
            super(epoch, EXPIRED);
            this.itemId = itemId;
        }
    }

    public static Epoch capture(int itemId) {
        drainExpired();
        // Hold the epoch strongly until it is returned, even if GC runs during compute.
        Epoch[] result = new Epoch[1];
        CURRENT.compute(itemId, (id, reference) -> {
            Epoch epoch = reference == null ? null : reference.get();
            if (epoch == null) {
                epoch = new Epoch();
                reference = new EpochReference(id, epoch);
            }
            result[0] = epoch;
            return reference;
        });
        return result[0];
    }

    public static void invalidate(int itemId) {
        drainExpired();
        CURRENT.computeIfPresent(itemId, (id, reference) -> {
            Epoch epoch = reference.get();
            if (epoch != null) epoch.current = false;
            return null;
        });
    }

    private static void drainExpired() {
        for (int i = 0; i < 64; i++) {
            EpochReference reference = (EpochReference) EXPIRED.poll();
            if (reference == null) return;
            CURRENT.remove(reference.itemId, reference);
        }
    }
}

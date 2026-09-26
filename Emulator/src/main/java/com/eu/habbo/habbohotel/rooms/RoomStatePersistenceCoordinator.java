package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.threading.ThreadPooling;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates versioned room-state snapshots and keeps scheduled writes off room workers. */
final class RoomStatePersistenceCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomStatePersistenceCoordinator.class);

    private final Room room;
    private final RoomPersistence persistence;
    private final Object persistenceLock = new Object();
    private final AtomicBoolean savePending = new AtomicBoolean();
    private final AtomicLong stateRevision = new AtomicLong();
    private final AtomicLong persistedRevision = new AtomicLong();

    RoomStatePersistenceCoordinator(Room room, RoomPersistence persistence) {
        this.room = room;
        this.persistence = persistence;
    }

    void markDirty() {
        this.stateRevision.incrementAndGet();
    }

    void markPersisted() {
        this.persistedRevision.set(this.stateRevision.get());
    }

    boolean hasUnsavedState() {
        return this.stateRevision.get() > this.persistedRevision.get();
    }

    void save() {
        synchronized (this.persistenceLock) {
            RoomSaveSnapshot snapshot = this.captureSnapshot();
            if (snapshot != null) {
                this.persist(snapshot);
            }
        }
    }

    void scheduleSave() {
        if (!this.hasUnsavedState() || !this.savePending.compareAndSet(false, true)) {
            return;
        }

        Runnable saveTask = () -> {
            try {
                synchronized (this.persistenceLock) {
                    RoomSaveSnapshot snapshot = this.captureSnapshot();
                    if (snapshot != null) {
                        this.persist(snapshot);
                    }
                }
            } finally {
                this.savePending.set(false);
            }
        };

        ThreadPooling threading = this.room.threading();
        if (threading == null) {
            saveTask.run();
            return;
        }

        try {
            if (!threading.tryRunPersistence(saveTask)) {
                // Leave the state dirty and retry on the next room cycle instead of blocking it.
                this.savePending.set(false);
            }
        } catch (RuntimeException rejected) {
            this.savePending.set(false);
            LOGGER.error("Could not queue room {} save; it will retry on a later cycle", this.room.getId(), rejected);
        }
    }

    private RoomSaveSnapshot captureSnapshot() {
        synchronized (this.room) {
            for (int attempt = 0; attempt < 3; attempt++) {
                long revision = this.stateRevision.get();
                if (revision <= this.persistedRevision.get()) {
                    return null;
                }

                RoomPersistence.State state = RoomPersistentStateFactory.capture(this.room);
                if (revision == this.stateRevision.get()) {
                    return new RoomSaveSnapshot(revision, state);
                }
            }
        }

        // A concurrent settings change left this snapshot torn; a later cycle will retry it.
        return null;
    }

    private void persist(RoomSaveSnapshot snapshot) {
        try {
            this.persistence.save(snapshot.state());
            this.persistedRevision.accumulateAndGet(snapshot.revision(), Math::max);
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception saving room {}", this.room.getId(), exception);
        }
    }

    private record RoomSaveSnapshot(long revision, RoomPersistence.State state) {}
}

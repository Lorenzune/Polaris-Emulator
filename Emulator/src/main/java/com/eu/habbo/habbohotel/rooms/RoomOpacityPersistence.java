package com.eu.habbo.habbohotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persists per-furniture visual state in the furniture row without blocking the room cycle. */
final class RoomOpacityPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomOpacityPersistence.class);
    private static final long WRITE_COALESCE_MILLIS = 200;
    private static final String UPDATE_SQL =
            "UPDATE items SET wired_opacity = ?, wired_scaling = ?, custom_color = ? WHERE id = ?";

    private final int roomId;
    private final RoomDependencies.ConnectionProvider database;
    private final Executor executor;
    private final Object lock = new Object();
    private final Object writeLock = new Object();
    private final Map<Integer, PersistedState> pending = new HashMap<>();
    private boolean scheduled;

    RoomOpacityPersistence(int roomId, RoomDependencies.ConnectionProvider database, Executor executor) {
        this.roomId = roomId;
        this.database = Objects.requireNonNull(database, "database");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    Map<Integer, PersistedState> load() throws SQLException {
        Map<Integer, PersistedState> states = new HashMap<>();
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id AS furni_id, wired_opacity AS opacity, wired_scaling AS scaling, custom_color "
                                + "FROM items WHERE room_id = ? AND (wired_opacity <> 100 OR wired_scaling <> 100 OR custom_color IS NOT NULL)")) {
            statement.setInt(1, this.roomId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    states.put(
                            resultSet.getInt("furni_id"),
                            new PersistedState(
                                    resultSet.getInt("opacity"),
                                    resultSet.getInt("scaling"),
                                    resultSet.getString("custom_color")));
                }
            }
        }
        synchronized (this.lock) {
            this.pending.forEach((furniId, state) -> {
                if (state.isDefault()) {
                    states.remove(furniId);
                } else {
                    states.put(furniId, state);
                }
            });
        }
        return Map.copyOf(states);
    }

    void save(Collection<WiredOpacityState> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        Map<Integer, PersistedState> changes = new HashMap<>();
        for (WiredOpacityState state : updates) {
            if (state != null && state.itemId() > 0) {
                changes.put(state.itemId(), new PersistedState(state.opacity(), state.scaling(), state.customColor()));
            }
        }
        this.enqueue(changes);
    }

    private void enqueue(Map<Integer, PersistedState> changes) {
        boolean startFlush = false;
        synchronized (this.lock) {
            this.pending.putAll(changes);
            if (!this.scheduled && !this.pending.isEmpty()) {
                this.scheduled = true;
                startFlush = true;
            }
        }

        if (startFlush) {
            try {
                java.util.concurrent.CompletableFuture.delayedExecutor(
                                WRITE_COALESCE_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS, this.executor)
                        .execute(this::flush);
            } catch (RuntimeException exception) {
                synchronized (this.lock) {
                    this.scheduled = false;
                }
                LOGGER.error("Unable to schedule opacity persistence for room {}", this.roomId, exception);
            }
        }
    }

    private void flush() {
        synchronized (this.writeLock) {
            while (true) {
                Map<Integer, PersistedState> batch;
                synchronized (this.lock) {
                    if (this.pending.isEmpty()) {
                        this.scheduled = false;
                        return;
                    }
                    batch = new HashMap<>(this.pending);
                    this.pending.clear();
                }

                try {
                    this.write(batch);
                } catch (SQLException exception) {
                    synchronized (this.lock) {
                        batch.forEach(this.pending::putIfAbsent);
                        this.scheduled = false;
                    }
                    LOGGER.error("Unable to persist furniture opacity and scale for room {}", this.roomId, exception);
                    return;
                }
            }
        }
    }

    /** The BC palette commits the complete selection before publishing its new color. */
    void saveBuildersBlockColors(Collection<WiredOpacityState> updates, Map<Integer, Integer> expectedBaseIds)
            throws SQLException {
        if (updates.isEmpty()) return;
        synchronized (this.writeLock) {
            try (Connection connection = this.database.openConnection()) {
                if (!connection.getAutoCommit()) throw new SQLException("Palette requires its own transaction");
                connection.setAutoCommit(false);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE items SET wired_opacity = ?, wired_scaling = ?, custom_color = ? WHERE id = ? AND room_id = ? AND item_id = ?"
                                + " AND EXISTS (SELECT 1 FROM builders_club_items bc WHERE bc.item_id = items.id AND bc.room_id = items.room_id)")) {
                    for (WiredOpacityState state : updates) {
                        update.setInt(1, state.opacity());
                        update.setInt(2, state.scaling());
                        update.setString(3, state.customColor());
                        update.setInt(4, state.itemId());
                        update.setInt(5, this.roomId);
                        update.setInt(6, expectedBaseIds.get(state.itemId()));
                        update.addBatch();
                    }
                    int[] counts = update.executeBatch();
                    if (counts.length != updates.size()) throw new SQLException("Incomplete palette batch");
                    for (int count : counts) if (count != 1) throw new SQLException("Palette target changed");
                    connection.commit();
                } catch (SQLException | RuntimeException failure) {
                    connection.rollback();
                    throw failure;
                }
                synchronized (this.lock) {
                    for (WiredOpacityState state : updates) this.pending.remove(state.itemId());
                }
            }
        }
    }

    private void write(Map<Integer, PersistedState> batch) throws SQLException {
        if (batch.isEmpty()) {
            return;
        }

        try (Connection connection = this.database.openConnection();
                PreparedStatement update = connection.prepareStatement(UPDATE_SQL)) {
            for (Map.Entry<Integer, PersistedState> entry : batch.entrySet()) {
                update.setInt(1, entry.getValue().opacity());
                update.setInt(2, entry.getValue().scaling());
                update.setString(3, entry.getValue().customColor());
                update.setInt(4, entry.getKey());
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    record PersistedState(int opacity, int scaling, String customColor) {
        private static final PersistedState DEFAULT = new PersistedState(100, 100, null);

        PersistedState(int opacity, int scaling) {
            this(opacity, scaling, null);
        }

        PersistedState {
            opacity = Math.max(0, Math.min(100, opacity));
            scaling = WiredFurnitureScaling.normalize(scaling);
            try {
                customColor = WiredFurnitureCustomColor.normalize(customColor);
            } catch (IllegalArgumentException exception) {
                customColor = null;
            }
        }

        boolean isDefault() {
            return this.opacity == 100 && this.scaling == 100 && this.customColor == null;
        }
    }
}

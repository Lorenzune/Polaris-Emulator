package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics.HistoryEntry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Permanent database-only room history. Writes are acknowledged after transaction commit. */
final class WiredLogRepository {
    private final DataSource dataSource;

    WiredLogRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    void append(int roomId, HistoryEntry entry) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO wired_log_history
                        (event_key, room_id, occurred_at_ms, log_type, level, message, source_label, source_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE event_key = VALUES(event_key)
                    """)) {
                statement.setString(1, entry.getEventKey());
                statement.setInt(2, roomId);
                statement.setLong(3, entry.getOccurredAtMs());
                statement.setString(4, entry.getType().name());
                statement.setString(5, entry.getSeverity().name());
                statement.setString(6, entry.getReason());
                statement.setString(7, entry.getSourceLabel());
                statement.setInt(8, entry.getSourceId());
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot persist wired log for room " + roomId, exception);
        }
    }

    List<HistoryEntry> load(int roomId) {
        List<HistoryEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                    SELECT event_key, occurred_at_ms, log_type, level, message, source_label, source_id
                    FROM wired_log_history WHERE room_id = ? ORDER BY occurred_at_ms DESC, id DESC
                    """)) {
            statement.setInt(1, roomId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    HistoryEntry entry = new HistoryEntry(
                            WiredRoomDiagnostics.Type.valueOf(rows.getString("log_type")),
                            WiredRoomDiagnostics.Severity.valueOf(rows.getString("level")),
                            rows.getLong("occurred_at_ms"),
                            rows.getString("message"),
                            rows.getString("source_label"),
                            rows.getInt("source_id"));
                    entry.setEventKey(rows.getString("event_key"));
                    entries.add(entry);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read wired log history for room " + roomId, exception);
        }
        return entries;
    }
}

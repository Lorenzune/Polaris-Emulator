package com.eu.habbo.habbohotel.permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Append-only audit. Mutations append on their own transaction, never asynchronously. */
public final class PermissionAuditLog {
    public record Actor(int id, String name, String via, String reason) {
        public static Actor system(String via) {
            return new Actor(0, "system", via, "");
        }
    }

    public record Entry(
            long id,
            long timestamp,
            int actorId,
            String actorName,
            String action,
            String targetType,
            int targetId,
            String subject,
            String oldValue,
            String newValue,
            String via,
            String reason) {}

    private PermissionAuditLog() {}

    public static void observeChanges(Connection connection, Actor actor, List<PermissionChange> changes)
            throws SQLException {
        if (!connection.getAutoCommit()) throw new SQLException("Audit observation requires its own transaction");
        connection.setAutoCommit(false);
        try {
            for (PermissionChange change : changes)
                append(
                        connection,
                        actor,
                        "permission_observed",
                        "rank",
                        change.rankId(),
                        change.key(),
                        change.oldValue(),
                        change.newValue());
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public static void append(
            Connection connection,
            Actor actor,
            String action,
            String targetType,
            int targetId,
            String subject,
            String oldValue,
            String newValue)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "INSERT INTO permission_audit (`timestamp`, actor_id, actor_name, action, target_type, target_id, subject, old_value, new_value, via, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            query.setLong(1, System.currentTimeMillis() / 1000);
            query.setInt(2, actor.id());
            query.setString(3, clip(actor.name(), 64));
            query.setString(4, clip(action, 32));
            query.setString(5, targetType);
            query.setInt(6, targetId);
            query.setString(7, clip(subject, 64));
            query.setString(8, clip(oldValue, 255));
            query.setString(9, clip(newValue, 255));
            query.setString(10, clip(actor.via(), 64));
            query.setString(11, clip(actor.reason(), 255));
            query.executeUpdate();
        }
    }

    public static List<Entry> recent(Connection connection, int userId, long beforeId) throws SQLException {
        List<Entry> result = new ArrayList<>();
        String sql = "SELECT * FROM permission_audit WHERE id < ?"
                + (userId > 0 ? " AND target_type = 'user' AND target_id = ?" : "")
                + " ORDER BY id DESC LIMIT 15";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, beforeId > 0 ? beforeId : Long.MAX_VALUE);
            if (userId > 0) query.setInt(2, userId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next())
                    result.add(new Entry(
                            row.getLong("id"),
                            row.getLong("timestamp"),
                            row.getInt("actor_id"),
                            row.getString("actor_name"),
                            row.getString("action"),
                            row.getString("target_type"),
                            row.getInt("target_id"),
                            row.getString("subject"),
                            row.getString("old_value"),
                            row.getString("new_value"),
                            row.getString("via"),
                            row.getString("reason")));
            }
        }
        return List.copyOf(result);
    }

    static String clip(String text, int max) {
        if (text == null) return "";
        int count = text.codePointCount(0, text.length());
        return count > max ? text.substring(0, text.offsetByCodePoints(0, max)) : text;
    }
}

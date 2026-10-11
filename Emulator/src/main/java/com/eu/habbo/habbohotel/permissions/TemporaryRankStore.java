package com.eu.habbo.habbohotel.permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Durable rank transitions; users row is always locked before its lease row. */
public final class TemporaryRankStore {
    public record State(int rank, Integer previousRank, long expiresAt) {}

    private TemporaryRankStore() {}

    public static long durationMillis(String input) {
        if (input == null || !input.matches("[1-9][0-9]{0,5}[mhd]"))
            throw new IllegalArgumentException("Usa 30m, 2h oppure 7d");
        long units = Long.parseLong(input.substring(0, input.length() - 1));
        long multiplier =
                switch (input.charAt(input.length() - 1)) {
                    case 'm' -> 60_000L;
                    case 'h' -> 3_600_000L;
                    default -> 86_400_000L;
                };
        long duration = units * multiplier;
        if (duration > 30L * 86_400_000L) throw new IllegalArgumentException("Durata massima: 30 giorni");
        return duration;
    }

    public static State refresh(Connection connection, int userId, long now) throws SQLException {
        return refresh(connection, userId, now, rank -> rank);
    }

    public static State refresh(Connection connection, int userId, long now, IntUnaryOperator rankResolver)
            throws SQLException {
        return transition(connection, userId, null, 0, 0, "", now, rank -> true, rankResolver);
    }

    public static State grant(
            Connection connection,
            int userId,
            int assigned,
            long duration,
            int actorId,
            String reason,
            long now,
            IntPredicate allowed)
            throws SQLException {
        return grant(connection, userId, assigned, duration, actorId, reason, now, allowed, rank -> rank);
    }

    public static State grant(
            Connection connection,
            int userId,
            int assigned,
            long duration,
            int actorId,
            String reason,
            long now,
            IntPredicate allowed,
            IntUnaryOperator rankResolver)
            throws SQLException {
        if (duration < 60_000L || duration > 30L * 86_400_000L || userId == actorId || assigned <= 0)
            throw new IllegalArgumentException("Invalid temporary rank grant");
        return transition(connection, userId, assigned, duration, actorId, reason, now, allowed, rankResolver);
    }

    private static State transition(
            Connection connection,
            int userId,
            Integer assigned,
            long duration,
            int actorId,
            String reason,
            long now,
            IntPredicate allowed,
            IntUnaryOperator rankResolver)
            throws SQLException {
        if (!connection.getAutoCommit()) throw new SQLException("Rank transition requires its own transaction");
        connection.setAutoCommit(false);
        try {
            int current = lockedRank(connection, userId);
            Integer previous = null;
            int leaseRank = 0;
            long expires = 0;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT previous_rank, assigned_rank, expires_at FROM user_temporary_ranks WHERE user_id = ? FOR UPDATE")) {
                query.setInt(1, userId);
                try (ResultSet row = query.executeQuery()) {
                    if (row.next()) {
                        previous = row.getInt(1);
                        leaseRank = row.getInt(2);
                        expires = row.getLong(3);
                    }
                }
            }
            if (previous != null && current != leaseRank) {
                // An external permanent update supersedes the lease.
                clear(connection, userId);
                previous = null;
                expires = 0;
            }
            if (previous != null && expires <= now) {
                previous = rankResolver.applyAsInt(previous);
                writeRank(connection, userId, previous);
                PermissionAuditLog.append(
                        connection,
                        PermissionAuditLog.Actor.system("temporary_rank_expiry"),
                        "rank_set",
                        "user",
                        userId,
                        "rank",
                        "" + current,
                        "" + previous);
                current = previous;
                clear(connection, userId);
                previous = null;
                expires = 0;
            }
            if (assigned != null) {
                int returnRank = rankResolver.applyAsInt(previous == null ? current : previous);
                if (!allowed.test(current) || !allowed.test(returnRank) || !allowed.test(assigned))
                    throw new SecurityException("Temporary rank hierarchy denied");
                if (returnRank == assigned)
                    throw new IllegalArgumentException("Il rank richiesto è già quello permanente");
                expires = Math.addExact(now, duration);
                try (PreparedStatement update = connection.prepareStatement(
                        "INSERT INTO user_temporary_ranks (user_id, previous_rank, assigned_rank, expires_at, actor_id, reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE previous_rank = VALUES(previous_rank), assigned_rank = VALUES(assigned_rank), expires_at = VALUES(expires_at), actor_id = VALUES(actor_id), reason = VALUES(reason), created_at = VALUES(created_at)")) {
                    update.setInt(1, userId);
                    update.setInt(2, returnRank);
                    update.setInt(3, assigned);
                    update.setLong(4, expires);
                    update.setInt(5, actorId);
                    update.setString(6, reason == null ? "" : reason.substring(0, Math.min(255, reason.length())));
                    update.setLong(7, now);
                    update.executeUpdate();
                }
                writeRank(connection, userId, assigned);
                PermissionAuditLog.append(
                        connection,
                        new PermissionAuditLog.Actor(actorId, "", "temporary_rank", reason),
                        "rank_set",
                        "user",
                        userId,
                        "rank",
                        "" + current,
                        "" + assigned + ";expires=" + expires);
                current = assigned;
                previous = returnRank;
            }
            connection.commit();
            return new State(current, previous, expires);
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public static void permanent(Connection connection, int userId, int rank) throws SQLException {
        permanent(connection, userId, rank, PermissionAuditLog.Actor.system("system"));
    }

    public static void permanent(Connection connection, int userId, int rank, PermissionAuditLog.Actor actor)
            throws SQLException {
        if (!connection.getAutoCommit()) throw new SQLException("Rank transition requires its own transaction");
        connection.setAutoCommit(false);
        try {
            int previous = lockedRank(connection, userId);
            writeRank(connection, userId, rank);
            clear(connection, userId);
            PermissionAuditLog.append(connection, actor, "rank_set", "user", userId, "rank", "" + previous, "" + rank);
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static int lockedRank(Connection connection, int userId) throws SQLException {
        try (PreparedStatement query =
                connection.prepareStatement("SELECT `rank` FROM users WHERE id = ? FOR UPDATE")) {
            query.setInt(1, userId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("Unknown rank target");
                return row.getInt(1);
            }
        }
    }

    private static void writeRank(Connection connection, int userId, int rank) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE users SET `rank` = ? WHERE id = ?")) {
            update.setInt(1, rank);
            update.setInt(2, userId);
            update.executeUpdate();
        }
    }

    private static void clear(Connection connection, int userId) throws SQLException {
        try (PreparedStatement update =
                connection.prepareStatement("DELETE FROM user_temporary_ranks WHERE user_id = ?")) {
            update.setInt(1, userId);
            update.executeUpdate();
        }
    }
}

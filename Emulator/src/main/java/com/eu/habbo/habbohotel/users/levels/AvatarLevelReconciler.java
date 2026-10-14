package com.eu.habbo.habbohotel.users.levels;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Rebuild disposable projections from committed ownership and achievement rows. */
final class AvatarLevelReconciler {
    private AvatarLevelReconciler() {}

    static void reconcile(Connection connection, int userId) throws SQLException {
        reconcile(connection, userId, true);
    }

    /** Normal dirty delivery uses the transactional projections; periodically audit their sources. */
    static void reconcile(Connection connection, int userId, boolean force) throws SQLException {
        // This must be the first operation in a fresh transaction: take the owner
        // lock BEFORE establishing the consistent source snapshot. Grant triggers
        // cannot commit that owner's changes until this transaction finishes.
        try (var lock = connection.prepareCall("{CALL avatar_levels_lock(?, 0)}")) {
            lock.setInt(1, userId);
            lock.execute();
        }
        if (!force) {
            try (var query =
                    connection.prepareStatement("SELECT last_reconciled_at <= CURRENT_TIMESTAMP - INTERVAL 5 MINUTE "
                            + "FROM users_avatar_levels WHERE user_id = ?")) {
                query.setInt(1, userId);
                try (var row = query.executeQuery()) {
                    if (row.next() && !row.getBoolean(1)) return;
                }
            }
        }
        // Plain SELECTs intentionally do not lock source records. A concurrent
        // grant may already own a source record while waiting for our owner lock.
        Map<String, Integer> badges = new LinkedHashMap<>();
        try (var query = connection.prepareStatement(
                "SELECT UPPER(badge_code), COUNT(*) FROM users_badges WHERE user_id = ? GROUP BY UPPER(badge_code)")) {
            query.setInt(1, userId);
            try (var rows = query.executeQuery()) {
                while (rows.next()) badges.put(rows.getString(1), rows.getInt(2));
            }
        }
        Map<Integer, Integer> furni = new LinkedHashMap<>();
        try (var query = connection.prepareStatement("SELECT i.item_id, COUNT(*) FROM items i "
                + "LEFT JOIN builders_club_items bc ON bc.item_id = i.id "
                + "WHERE i.user_id = ? AND i.avatar_level_excluded = 0 AND bc.item_id IS NULL GROUP BY i.item_id")) {
            query.setInt(1, userId);
            try (var rows = query.executeQuery()) {
                while (rows.next()) furni.put(rows.getInt(1), rows.getInt(2));
            }
        }
        Map<String, long[]> achievements = new LinkedHashMap<>();
        try (var query = connection.prepareStatement(
                "SELECT achievement_name, MAX(progress) FROM users_achievements WHERE user_id = ? GROUP BY achievement_name")) {
            query.setInt(1, userId);
            try (var rows = query.executeQuery()) {
                while (rows.next()) achievements.put(rows.getString(1), new long[] {rows.getInt(2), 0});
            }
        }
        try (var query = connection.prepareStatement("SELECT d.achievement_name, SUM(GREATEST(0,q.amount)) "
                + "FROM users_achievements_queue q JOIN (SELECT DISTINCT achievement_id,achievement_name "
                + "FROM users_avatar_achievement_levels) d ON d.achievement_id=q.achievement_id "
                + "WHERE q.user_id = ? GROUP BY d.achievement_name")) {
            query.setInt(1, userId);
            try (var rows = query.executeQuery()) {
                while (rows.next())
                    achievements.computeIfAbsent(rows.getString(1), ignored -> new long[2])[1] = rows.getLong(2);
            }
        }
        long hosting;
        try (var query = connection.prepareStatement(
                "SELECT COALESCE(SUM(weighted_millis),0) FROM users_avatar_hosting_checkpoints WHERE owner_id = ?")) {
            query.setInt(1, userId);
            try (var rows = query.executeQuery()) {
                rows.next();
                hosting = rows.getLong(1);
            }
        }
        for (String table : new String[] {
            "users_avatar_badge_counts", "users_avatar_furni_counts", "users_avatar_achievement_progress"
        }) {
            try (var clear = connection.prepareStatement("DELETE FROM " + table + " WHERE user_id = ?")) {
                clear.setInt(1, userId);
                clear.executeUpdate();
            }
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO users_avatar_badge_counts (user_id,badge_code,copies) VALUES (?,?,?)")) {
            for (var badge : badges.entrySet()) {
                insert.setInt(1, userId);
                insert.setString(2, badge.getKey());
                insert.setInt(3, badge.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO users_avatar_furni_counts (user_id,item_id,copies) VALUES (?,?,?)")) {
            for (var item : furni.entrySet()) {
                insert.setInt(1, userId);
                insert.setInt(2, item.getKey());
                insert.setInt(3, item.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (var insert = connection.prepareStatement("INSERT INTO users_avatar_achievement_progress "
                + "(user_id,achievement_name,progress,pending_progress) VALUES (?,?,?,?)")) {
            for (var achievement : achievements.entrySet()) {
                insert.setInt(1, userId);
                insert.setString(2, achievement.getKey());
                insert.setLong(3, achievement.getValue()[0]);
                insert.setLong(4, achievement.getValue()[1]);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (var update = connection.prepareStatement(
                "UPDATE users_avatar_levels "
                        + "SET hosting_weighted_millis = hosting_baseline_millis + ?, last_reconciled_at = CURRENT_TIMESTAMP WHERE user_id = ?")) {
            update.setLong(1, hosting);
            update.setInt(2, userId);
            update.executeUpdate();
        }
    }
}

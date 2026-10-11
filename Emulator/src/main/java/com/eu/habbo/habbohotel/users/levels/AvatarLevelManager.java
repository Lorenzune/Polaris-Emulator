package com.eu.habbo.habbohotel.users.levels;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.earnings.EarningsCenterManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.outgoing.earnings.EarningsCenterComposer;
import com.eu.habbo.messages.outgoing.users.AvatarLevelComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Durable high-water marks prevent level loss and re-awarding points after selling/rebuying furni. */
public class AvatarLevelManager extends Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AvatarLevelManager.class);
    private final Object[] locks = new Object[256];
    private final ConcurrentHashMap<Integer, AvatarLevelSnapshot> snapshots = new ConcurrentHashMap<>();
    public AvatarLevelManager() {
        super(1);
        Arrays.setAll(locks, ignored -> new Object());
    }

    /** Persist exactly the enabled/archived runtime definitions, including native SWF ids. */
    public synchronized void initializeDefinitions() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Acquire user locks before rebuilding projection rows. Use nonlocking
                // source reads so an in-flight grant waiting for these locks cannot deadlock a reload.
                java.util.List<Integer> userIds = new java.util.ArrayList<>();
                try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT id FROM users")) {
                    while (rows.next()) userIds.add(rows.getInt(1));
                }
                try (var seed = connection.prepareStatement("INSERT IGNORE INTO users_avatar_levels (user_id) VALUES (?)")) {
                    for (int id : userIds) { seed.setInt(1, id); seed.addBatch(); }
                    seed.executeBatch();
                }
                try (var lock = connection.createStatement()) { lock.executeUpdate("UPDATE users_avatar_levels SET user_id = user_id ORDER BY user_id"); }
                var definitions = Emulator.getGameEnvironment().getAchievementManager().getAchievements();
                java.util.List<Achievement> definitionCopy;
                synchronized (definitions) { definitionCopy = new java.util.ArrayList<>(definitions.values()); }
                java.util.Map<Integer, String> names = new java.util.HashMap<>();
                try (var clear = connection.createStatement()) { clear.executeUpdate("DELETE FROM users_avatar_achievement_levels"); }
                try (var insert = connection.prepareStatement("INSERT INTO users_avatar_achievement_levels "
                        + "(achievement_name, achievement_id, level, progress_needed) VALUES (?, ?, ?, ?)")) {
                    for (Achievement achievement : definitionCopy) {
                        if (achievement.state <= 0) continue;
                        names.put(achievement.id, achievement.name);
                        for (var level : achievement.levels.values()) {
                            insert.setString(1, achievement.name); insert.setInt(2, achievement.id);
                            insert.setInt(3, level.level); insert.setInt(4, level.progress); insert.addBatch();
                        }
                    }
                    insert.executeBatch();
                }
                record Pending(int userId, String name, long amount) {}
                java.util.List<Pending> pending = new java.util.ArrayList<>();
                try (var query = connection.createStatement(); var rows = query.executeQuery(
                        "SELECT user_id, achievement_id, amount FROM users_achievements_queue")) {
                    while (rows.next()) {
                        String name = names.get(rows.getInt("achievement_id"));
                        if (name != null) pending.add(new Pending(rows.getInt("user_id"), name, Math.max(0, rows.getLong("amount"))));
                    }
                }
                try (var clear = connection.createStatement()) { clear.executeUpdate("UPDATE users_avatar_achievement_progress SET pending_progress = 0"); }
                try (var queue = connection.prepareStatement("INSERT INTO users_avatar_achievement_progress (user_id, achievement_name, pending_progress) "
                        + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE pending_progress = pending_progress + VALUES(pending_progress)")) {
                    for (var row : pending) { queue.setInt(1, row.userId()); queue.setString(2, row.name()); queue.setLong(3, row.amount()); queue.addBatch(); }
                    queue.executeBatch();
                }
                try (var config = connection.prepareStatement("UPDATE users_avatar_level_config SET ready = 1, camera_item_id = ? WHERE id = 1")) {
                    config.setInt(1, Emulator.getConfig().getInt("camera.item_id", 0)); config.executeUpdate();
                }
                try (var mark = connection.createStatement()) {
                    mark.executeUpdate("UPDATE users_avatar_levels SET dirty = 1, revision = revision + 1");
                }
                connection.commit();
            } catch (SQLException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("Could not initialize durable avatar progression", exception); }
    }

    @Override
    public void run() {
        if (this.disposed) return;
        try {
            // Dirty rows survive shutdown and include offline users. High-water marks
            // are captured in the originating transaction, including short-lived gains.
            java.util.List<Integer> users = new java.util.ArrayList<>();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    var query = connection.prepareStatement("SELECT user_id FROM users_avatar_levels WHERE dirty = 1 ORDER BY user_id LIMIT 200")) {
                try (var rows = query.executeQuery()) { while (rows.next()) users.add(rows.getInt(1)); }
            }
            var online = Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos();
            for (var entry : online.entrySet()) {
                Habbo habbo = entry.getValue();
                if (habbo != null && habbo.isOnline() && !snapshots.containsKey(entry.getKey()) && !users.contains(entry.getKey())) {
                    users.add(entry.getKey());
                }
            }
            for (int id : users) {
                try { refresh(id, Emulator.getGameEnvironment().getHabboManager().getHabbo(id)); }
                catch (SQLException exception) { LOGGER.error("Could not refresh avatar levels for {}", id, exception); }
            }
            snapshots.keySet().removeIf(id -> !online.containsKey(id));
        } catch (SQLException exception) {
            LOGGER.error("Could not read pending avatar updates", exception);
        } finally {
            // Slow DB calls must not accumulate overlapping workers behind user locks.
            if (!this.disposed) super.run();
        }
    }

    public int getLevel(int userId) {
        // Hosting must use the latest committed level, even before its notification tick.
        try {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    var query = connection.prepareStatement("SELECT level FROM users_avatar_levels WHERE user_id = ?")) {
                query.setInt(1, userId);
                try (var rows = query.executeQuery()) { if (rows.next()) return rows.getInt(1); }
            }
            return refresh(userId, null).level();
        } catch (SQLException exception) {
            LOGGER.error("Could not read avatar level for {}", userId, exception);
            AvatarLevelSnapshot previous = snapshots.get(userId);
            return previous != null ? previous.level() : 1;
        }
    }

    public AvatarLevelSnapshot refresh(int userId) throws SQLException {
        return refresh(userId, Emulator.getGameEnvironment().getHabboManager().getHabbo(userId));
    }

    public AvatarLevelSnapshot refresh(Habbo habbo) throws SQLException {
        return refresh(habbo.getHabboInfo().getId(), habbo);
    }

    private AvatarLevelSnapshot refresh(int userId, Habbo habbo) throws SQLException {
        synchronized (locks[Math.floorMod(userId, locks.length)]) {
            AvatarLevelSnapshot snapshot;
            int rewarded, pantheon;
            long revision;
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try {
                    AvatarLevelReconciler.reconcile(connection, userId);
                    recalculate(connection, userId);
                    // Materialize Pantheon from the durable high-water mark. Its earned levels
                    // can contribute new avatar points: converge without recursion or memory state.
                    for (int attempt = 0; attempt < 64; attempt++) {
                        int target;
                        try (var query = connection.prepareStatement("SELECT pantheon_progress FROM users_avatar_levels WHERE user_id = ? FOR UPDATE")) {
                            query.setInt(1, userId);
                            try (var row = query.executeQuery()) { row.next(); target = row.getInt(1); }
                        }
                        try (var update = connection.prepareStatement("INSERT INTO users_achievements (user_id, achievement_name, progress) "
                                + "VALUES (?, 'Profile_Pantheon_', ?) ON DUPLICATE KEY UPDATE progress = GREATEST(progress, VALUES(progress))")) {
                            update.setInt(1, userId); update.setInt(2, target); update.executeUpdate();
                        }
                        try (var query = connection.prepareStatement("SELECT pantheon_progress FROM users_avatar_levels WHERE user_id = ?")) {
                            query.setInt(1, userId);
                            try (var row = query.executeQuery()) { row.next(); if (row.getInt(1) <= target) break; }
                        }
                        if (attempt == 63) throw new SQLException("Pantheon progression did not converge");
                    }
                    try (var query = connection.prepareStatement("SELECT * FROM users_avatar_levels WHERE user_id = ?")) {
                        query.setInt(1, userId);
                        try (var row = query.executeQuery()) {
                            row.next();
                            snapshot = new AvatarLevelSnapshot(userId, row.getInt("level"), row.getInt("highest_score"),
                                    new AvatarLevelScore(row.getInt("raw_achievements"), row.getInt("raw_badges"),
                                            AvatarLevels.hostingPoints(row.getLong("hosting_weighted_millis")), row.getInt("raw_furni")));
                            rewarded = row.getInt("rewarded_level"); pantheon = row.getInt("pantheon_progress"); revision = row.getLong("revision");
                        }
                    }
                    connection.commit();
                } catch (SQLException exception) { connection.rollback(); throw exception; }
            }
            if (habbo != null && habbo.isOnline() && habbo.getClient() != null) {
                awardRewards(habbo, rewarded, snapshot.level());
                AchievementManager.publishPersistedProgress(habbo,
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("Profile_Pantheon_"), pantheon);
            }
            AvatarLevelSnapshot previous = snapshots.put(userId, snapshot);
            if (habbo != null && habbo.isOnline() && habbo.getClient() != null && !snapshot.equals(previous)) {
                habbo.getClient().sendResponse(new AvatarLevelComposer(snapshot));
            }
            // A grant racing with delivery changes revision and stays pending for the next tick.
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    var ack = connection.prepareStatement("UPDATE users_avatar_levels SET dirty = 0 WHERE user_id = ? AND revision = ?")) {
                ack.setInt(1, userId); ack.setLong(2, revision); ack.executeUpdate();
            }
            return snapshot;
        }
    }

    private static void recalculate(Connection connection, int userId) throws SQLException {
        try (var call = connection.prepareCall("{CALL avatar_levels_refresh(?)}")) { call.setInt(1, userId); call.execute(); }
    }

    static void awardRewards(Habbo habbo, int rewarded, int level) throws SQLException {
        for (int next = rewarded + 1; next <= level; next++) {
            int reward = AvatarLevels.reward(next);
            if (reward > 0) {
                EconomyOperation operation = new EconomyOperation(
                        "avatar-level:" + habbo.getHabboInfo().getId() + ":" + next,
                        habbo.getHabboInfo().getId(), null, "currency_grant", "avatar.level.reward",
                        0, reward, null, "level=" + next);
                // The ledger queues this award under Level Progress in Earnings.
                // Replaying an award must not publish its old audit balance to the live wallet.
                LedgerWalletMutation.coordinated(habbo, () -> EconomyLedger.execute(operation));
            }
            // Separate from the wallet transaction; replay uses the same ledger id
            // if a crash occurs between crediting Duckets and acknowledging this level.
            acknowledgeReward(habbo.getHabboInfo().getId(), next);
        }
        if (rewarded < level) {
            habbo.getClient().sendResponse(new EarningsCenterComposer(new EarningsCenterManager().getEntries(habbo)));
        }
    }

    /** Releasing a repaired premature reward and acknowledging it are one commit.
     * The original ledger id remains unchanged, so already claimed rewards are never repeated. */
    static void acknowledgeReward(int userId, int level) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var update = connection.prepareStatement(
                        "UPDATE users_avatar_levels SET rewarded_level = GREATEST(rewarded_level, ?) WHERE user_id = ?")) {
                    update.setInt(1, level); update.setInt(2, userId); update.executeUpdate();
                }
                try (var restore = connection.prepareStatement("INSERT IGNORE INTO users_earnings_pending "
                        + "(operation_id,user_id,category,currency_type,amount,reason,created_at) "
                        + "SELECT operation_id,user_id,category,currency_type,amount,reason,created_at "
                        + "FROM users_avatar_deferred_rewards WHERE user_id = ? AND level = ?")) {
                    restore.setInt(1, userId); restore.setInt(2, level); restore.executeUpdate();
                }
                try (var clear = connection.prepareStatement("DELETE FROM users_avatar_deferred_rewards WHERE user_id = ? AND level = ?")) {
                    clear.setInt(1, userId); clear.setInt(2, level); clear.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) { connection.rollback(); throw exception; }
        }
    }

    /** Cumulative session checkpoints make retries safe even if COMMIT succeeded but its reply was lost. */
    public boolean recordHosting(int ownerId, String sessionId, long cumulativeWeightedMillis) {
        if (ownerId <= 0 || cumulativeWeightedMillis <= 0) return true;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var insert = connection.prepareStatement("INSERT IGNORE INTO users_avatar_hosting_checkpoints (session_id, owner_id) VALUES (?, ?)")) {
                    insert.setString(1, sessionId); insert.setInt(2, ownerId); insert.executeUpdate();
                }
                long saved;
                try (var query = connection.prepareStatement("SELECT weighted_millis, owner_id FROM users_avatar_hosting_checkpoints WHERE session_id = ? FOR UPDATE")) {
                    query.setString(1, sessionId);
                    try (var row = query.executeQuery()) {
                        row.next(); saved = row.getLong(1);
                        if (row.getInt(2) != ownerId) throw new SQLException("Hosting checkpoint owner mismatch");
                    }
                }
                if (cumulativeWeightedMillis > saved) {
                    try (var update = connection.prepareStatement("INSERT INTO users_avatar_levels (user_id, hosting_weighted_millis) VALUES (?, ?) "
                            + "ON DUPLICATE KEY UPDATE hosting_weighted_millis = hosting_weighted_millis + VALUES(hosting_weighted_millis)")) {
                        update.setInt(1, ownerId); update.setLong(2, cumulativeWeightedMillis - saved); update.executeUpdate();
                    }
                    try (var update = connection.prepareStatement("UPDATE users_avatar_hosting_checkpoints SET weighted_millis = ? WHERE session_id = ?")) {
                        update.setLong(1, cumulativeWeightedMillis); update.setString(2, sessionId); update.executeUpdate();
                    }
                    recalculate(connection, ownerId);
                }
                connection.commit(); return true;
            } catch (SQLException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { LOGGER.error("Could not record avatar hosting for {}", ownerId, exception); return false; }
    }
}

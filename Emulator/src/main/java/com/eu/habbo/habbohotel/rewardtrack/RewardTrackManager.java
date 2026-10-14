package com.eu.habbo.habbohotel.rewardtrack;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RewardTrackManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RewardTrackManager.class);

    public JsonObject getState(int userId, String requestedCode) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            Track track = loadTrack(connection, requestedCode);
            if (track == null) return null;

            Map<Integer, Integer> progress = loadProgress(connection, userId);
            Set<Integer> claims = loadClaims(connection, userId);
            boolean premium = hasPremium(connection, userId, track.id);

            JsonArray tasks = loadTasks(connection, track.id, progress);
            JsonArray prizes = loadPrizes(connection, track.id, claims, premium, pointsFromTasks(tasks));

            JsonObject payload = new JsonObject();
            JsonObject trackJson = new JsonObject();
            trackJson.addProperty("id", track.id);
            trackJson.addProperty("code", track.code);
            trackJson.addProperty("title", track.title);
            trackJson.addProperty("description", track.description);
            trackJson.addProperty("instructions", track.instructions);
            trackJson.addProperty("theme", track.theme);
            trackJson.addProperty("premium", premium);
            trackJson.addProperty("premiumEnabled", track.premiumEnabled);
            trackJson.addProperty("premiumCostCredits", track.premiumCostCredits);
            trackJson.addProperty("premiumCostDiamonds", track.premiumCostDiamonds);
            trackJson.addProperty("points", pointsFromTasks(tasks));
            trackJson.addProperty("completedTaskCount", completedTaskCount(tasks));
            trackJson.addProperty("totalTaskCount", totalTaskCount(tasks));
            trackJson.addProperty("rewardsCollected", claims.size());
            trackJson.addProperty("totalRewards", prizes.size());
            trackJson.add("tasks", tasks);
            trackJson.add("prizes", prizes);
            payload.add("track", trackJson);

            return payload;
        }
    }

    public JsonObject claimPrize(int userId, int prizeId) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Prize prize = loadPrize(connection, prizeId);
                if (prize == null) {
                    connection.rollback();
                    return error("Prize not found.");
                }

                JsonObject state = getState(userId, prize.trackCode);
                JsonObject track = state.getAsJsonObject("track");
                int points = track.get("points").getAsInt();
                boolean premium = track.get("premium").getAsBoolean();

                if (prize.requiredPoints > points) {
                    connection.rollback();
                    return error("Not enough reward track points.");
                }

                if (prize.premium && !premium) {
                    connection.rollback();
                    return error("Premium reward track is not unlocked.");
                }

                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT IGNORE INTO user_reward_track_claims (user_id, prize_id, claimed_at) VALUES (?, ?, ?)")) {
                    insert.setInt(1, userId);
                    insert.setInt(2, prize.id);
                    insert.setInt(3, Emulator.getIntUnixTimestamp());
                    if (insert.executeUpdate() == 0) {
                        connection.rollback();
                        return error("Reward already claimed.");
                    }
                }

                award(connection, userId, prize);
                connection.commit();
                return getState(userId, prize.trackCode);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public JsonObject unlockPremium(int userId, int trackId) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            Track track = loadTrackById(connection, trackId);
            if (track == null || !track.premiumEnabled) return error("Premium track is not available.");
            if (hasPremium(connection, userId, track.id)) return getState(userId, track.code);

            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (habbo == null) return error("You must be online to unlock premium rewards.");

            if (track.premiumCostCredits > 0
                    && !habbo.tryTakeCredits(
                            track.premiumCostCredits,
                            "reward_track.premium",
                            "reward-track-premium:credits:" + userId + ":" + track.id,
                            userId)) {
                return error("Not enough credits.");
            }

            if (track.premiumCostDiamonds > 0
                    && !habbo.tryTakePoints(
                            5,
                            track.premiumCostDiamonds,
                            "reward_track.premium",
                            "reward-track-premium:diamonds:" + userId + ":" + track.id,
                            userId)) {
                if (track.premiumCostCredits > 0) {
                    habbo.giveCredits(
                            track.premiumCostCredits,
                            "reward_track.premium.refund",
                            "reward-track-premium:credits-refund:" + userId + ":" + track.id,
                            userId);
                }
                return error("Not enough diamonds.");
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT IGNORE INTO user_reward_track_premium (user_id, track_id, purchased_at) VALUES (?, ?, ?)")) {
                insert.setInt(1, userId);
                insert.setInt(2, trackId);
                insert.setInt(3, Emulator.getIntUnixTimestamp());
                if (insert.executeUpdate() == 0) {
                    if (track.premiumCostCredits > 0) {
                        habbo.giveCredits(
                                track.premiumCostCredits,
                                "reward_track.premium.refund",
                                "reward-track-premium:credits-duplicate-refund:" + userId + ":" + track.id,
                                userId);
                    }
                    if (track.premiumCostDiamonds > 0) {
                        habbo.givePoints(
                                5,
                                track.premiumCostDiamonds,
                                "reward_track.premium.refund",
                                "reward-track-premium:diamonds-duplicate-refund:" + userId + ":" + track.id,
                                userId);
                    }
                }
            }

            return getState(userId, track.code);
        }
    }

    public int addProgress(int userId, String actionType, int amount) {
        if (userId <= 0 || actionType == null || actionType.isBlank() || amount <= 0) return 0;

        int updated;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO user_reward_track_progress (user_id, task_id, progress, updated_at) "
                                + "SELECT ?, t.id, ?, ? FROM reward_track_tasks t "
                                + "INNER JOIN reward_tracks rt ON rt.id = t.track_id AND rt.enabled = 1 "
                                + "WHERE t.action_type = ? AND t.enabled = 1 "
                                + "ON DUPLICATE KEY UPDATE progress = progress + VALUES(progress), updated_at = VALUES(updated_at)")) {
            statement.setInt(1, userId);
            statement.setInt(2, amount);
            statement.setInt(3, Emulator.getIntUnixTimestamp());
            statement.setString(4, actionType);
            updated = statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("RewardTrackManager -> Could not add progress for user {} action {}", userId, actionType, e);
            return 0;
        }
        // Daily tasks and achievement publication acquire their own connections.
        // Preserve delivery even when this action matches no reward-track task.
        Emulator.getGameEnvironment().getDailyTaskManager().addProgress(userId, actionType, amount);
        syncCompletionAchievement(userId);
        LOGGER.debug(
                "RewardTrackManager -> Progress action={} user={} amount={} rows={}",
                actionType,
                userId,
                amount,
                updated);
        return updated;
    }

    /** Applies progress to the exact track/task configured by a wired action. */
    public int applyWiredProgress(
            int userId, String trackCode, String taskCode, int amount, boolean addToExistingScore) {
        if (userId <= 0 || !isValidWiredId(trackCode) || !isValidWiredId(taskCode) || amount <= 0) return 0;

        int normalizedAmount = Math.min(Integer.MAX_VALUE, amount);
        int updated;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO user_reward_track_progress (user_id, task_id, progress, updated_at) "
                                + "SELECT ?, t.id, ?, ? FROM reward_track_tasks t "
                                + "INNER JOIN reward_tracks rt ON rt.id = t.track_id AND rt.enabled = 1 "
                                + "WHERE rt.code = ? AND t.action_type = ? AND t.enabled = 1 "
                                + "ON DUPLICATE KEY UPDATE progress = LEAST(2147483647, "
                                + (addToExistingScore ? "progress + VALUES(progress)" : "VALUES(progress)")
                                + "), updated_at = VALUES(updated_at)")) {
            statement.setInt(1, userId);
            statement.setInt(2, normalizedAmount);
            statement.setInt(3, Emulator.getIntUnixTimestamp());
            statement.setString(4, trackCode.trim());
            statement.setString(5, taskCode.trim());

            updated = statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn(
                    "RewardTrackManager -> Could not apply wired progress track={} task={} user={}",
                    trackCode,
                    taskCode,
                    userId,
                    e);
            return 0;
        }
        if (updated > 0) syncCompletionAchievement(userId);
        LOGGER.debug(
                "RewardTrackManager -> Wired progress track={} task={} user={} amount={} add={} rows={}",
                trackCode,
                taskCode,
                userId,
                normalizedAmount,
                addToExistingScore,
                updated);
        return updated;
    }

    /** Reset progress for one track while preserving already claimed prizes. */
    public int resetWiredProgress(int userId, String trackCode) {
        if (userId <= 0 || !isValidWiredText(trackCode)) return 0;

        int deleted;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE p FROM user_reward_track_progress p "
                        + "INNER JOIN reward_track_tasks t ON t.id = p.task_id "
                        + "INNER JOIN reward_tracks rt ON rt.id = t.track_id "
                        + "WHERE p.user_id = ? AND rt.code = ?")) {
            statement.setInt(1, userId);
            statement.setString(2, trackCode.trim());

            deleted = statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("RewardTrackManager -> Could not reset wired progress track={} user={}", trackCode, userId, e);
            return 0;
        }
        if (deleted > 0) syncCompletionAchievement(userId);
        LOGGER.debug("RewardTrackManager -> Wired progress reset track={} user={} rows={}", trackCode, userId, deleted);
        return deleted;
    }

    private static boolean isValidWiredId(String value) {
        return value != null && value.length() <= 100 && value.matches("[a-zA-Z0-9_]+");
    }

    private static boolean isValidWiredText(String value) {
        return value != null && value.length() <= 100 && !value.matches(".*[\\t\\r\\n].*");
    }

    private void syncCompletionAchievement(int userId) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (habbo == null) return;
        Integer completed = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM reward_tracks rt WHERE rt.enabled = 1 AND NOT EXISTS ("
                                + "SELECT 1 FROM reward_track_tasks t LEFT JOIN user_reward_track_progress p "
                                + "ON p.task_id = t.id AND p.user_id = ? WHERE t.track_id = rt.id AND t.enabled = 1 "
                                + "AND COALESCE(p.progress, 0) < (SELECT COALESCE(MAX(target), 1) FROM reward_track_task_levels l WHERE l.task_id = t.id))")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) completed = set.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.debug("RewardTrackManager -> Could not sync completion achievement for user {}", userId, e);
        }
        if (completed != null) AchievementInteractions.sync(habbo, "RewardTracksCompleted", completed);
    }

    public JsonObject getEditorState() throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            Track track = loadTrackForEditor(connection, "introduction");
            if (track == null) return null;

            JsonObject payload = new JsonObject();
            JsonObject trackJson = new JsonObject();
            trackJson.addProperty("id", track.id);
            trackJson.addProperty("code", track.code);
            trackJson.addProperty("title", track.title);
            trackJson.addProperty("description", track.description);
            trackJson.addProperty("instructions", track.instructions);
            trackJson.addProperty("theme", track.theme);
            trackJson.addProperty("enabled", track.enabled);
            trackJson.addProperty("premiumEnabled", track.premiumEnabled);
            trackJson.addProperty("premiumCostCredits", track.premiumCostCredits);
            trackJson.addProperty("premiumCostDiamonds", track.premiumCostDiamonds);
            trackJson.add("tasks", loadEditorTasks(connection, track.id));
            trackJson.add("prizes", loadEditorPrizes(connection, track.id));
            payload.add("track", trackJson);
            payload.add("actions", editorActions());
            payload.add("rewardTypes", editorRewardTypes());
            return payload;
        }
    }

    public JsonObject saveTrack(JsonObject body) throws SQLException {
        int id = intValue(body, "id", 1);
        String title = stringValue(body, "title", "Introduction Track");
        String description = stringValue(body, "description", "Learn all about Habbo!");
        String instructions = stringValue(body, "instructions", "Complete tasks to earn points and unlock rewards");
        String theme = stringValue(body, "theme", "introduction");
        boolean enabled = boolValue(body, "enabled", true);
        boolean premiumEnabled = boolValue(body, "premiumEnabled", true);
        int premiumCostCredits = Math.max(0, intValue(body, "premiumCostCredits", 0));
        int premiumCostDiamonds = Math.max(0, intValue(body, "premiumCostDiamonds", 0));

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE reward_tracks SET title = ?, description = ?, instructions = ?, theme = ?, enabled = ?, premium_enabled = ?, premium_cost_credits = ?, premium_cost_diamonds = ? WHERE id = ? LIMIT 1")) {
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setString(3, instructions);
            statement.setString(4, theme);
            statement.setBoolean(5, enabled);
            statement.setBoolean(6, premiumEnabled);
            statement.setInt(7, premiumCostCredits);
            statement.setInt(8, premiumCostDiamonds);
            statement.setInt(9, id);
            statement.executeUpdate();
        }

        return getEditorState();
    }

    public JsonObject saveTask(JsonObject body) throws SQLException {
        int id = intValue(body, "id", 0);
        int trackId = intValue(body, "trackId", 1);
        String actionType = stringValue(body, "actionType", "chat_with_someone");
        String title = stringValue(body, "title", "New task");
        String description = stringValue(body, "description", "");
        String tip = stringValue(body, "tip", "");
        String tipAction = stringValue(body, "tipAction", "");
        String tipButton = stringValue(body, "tipButton", "");
        int target = Math.max(1, intValue(body, "target", 1));
        int points = Math.max(0, intValue(body, "points", 10));
        int sortOrder = Math.max(0, intValue(body, "sortOrder", id));
        boolean enabled = boolValue(body, "enabled", true);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                int taskId = id;
                if (taskId > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE reward_track_tasks SET track_id = ?, action_type = ?, title = ?, description = ?, tip = ?, tip_action = ?, tip_button = ?, sort_order = ?, enabled = ? WHERE id = ? LIMIT 1")) {
                        statement.setInt(1, trackId);
                        statement.setString(2, actionType);
                        statement.setString(3, title);
                        statement.setString(4, description);
                        statement.setString(5, tip);
                        statement.setString(6, tipAction);
                        statement.setString(7, tipButton);
                        statement.setInt(8, sortOrder);
                        statement.setBoolean(9, enabled);
                        statement.setInt(10, taskId);
                        statement.executeUpdate();
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO reward_track_tasks (track_id, action_type, title, description, tip, tip_action, tip_button, sort_order, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            java.sql.Statement.RETURN_GENERATED_KEYS)) {
                        statement.setInt(1, trackId);
                        statement.setString(2, actionType);
                        statement.setString(3, title);
                        statement.setString(4, description);
                        statement.setString(5, tip);
                        statement.setString(6, tipAction);
                        statement.setString(7, tipButton);
                        statement.setInt(8, sortOrder);
                        statement.setBoolean(9, enabled);
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (keys.next()) taskId = keys.getInt(1);
                        }
                    }
                }

                saveTaskLevels(connection, taskId, body, target, points);

                connection.commit();
                return getEditorState();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public JsonObject deleteTask(int taskId) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM reward_track_tasks WHERE id = ? LIMIT 1")) {
            statement.setInt(1, taskId);
            statement.executeUpdate();
        }

        return getEditorState();
    }

    public JsonObject savePrize(JsonObject body) throws SQLException {
        int id = intValue(body, "id", 0);
        int trackId = intValue(body, "trackId", 1);
        int requiredPoints = Math.max(0, intValue(body, "requiredPoints", 0));
        boolean premium = boolValue(body, "premium", false);
        String rewardType = stringValue(body, "rewardType", "badge");
        String rewardCode = stringValue(body, "rewardCode", "");
        int rewardAmount = Math.max(1, intValue(body, "rewardAmount", 1));
        String actionType = stringValue(body, "actionType", "use_habbicon");
        int sortOrder = Math.max(0, intValue(body, "sortOrder", id));
        boolean enabled = boolValue(body, "enabled", true);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            if (id > 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE reward_track_prizes SET track_id = ?, required_points = ?, premium = ?, reward_type = ?, reward_code = ?, reward_amount = ?, action_type = ?, sort_order = ?, enabled = ? WHERE id = ? LIMIT 1")) {
                    statement.setInt(1, trackId);
                    statement.setInt(2, requiredPoints);
                    statement.setBoolean(3, premium);
                    statement.setString(4, rewardType);
                    statement.setString(5, rewardCode);
                    statement.setInt(6, rewardAmount);
                    statement.setString(7, actionType);
                    statement.setInt(8, sortOrder);
                    statement.setBoolean(9, enabled);
                    statement.setInt(10, id);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO reward_track_prizes (track_id, required_points, premium, reward_type, reward_code, reward_amount, action_type, sort_order, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setInt(1, trackId);
                    statement.setInt(2, requiredPoints);
                    statement.setBoolean(3, premium);
                    statement.setString(4, rewardType);
                    statement.setString(5, rewardCode);
                    statement.setInt(6, rewardAmount);
                    statement.setString(7, actionType);
                    statement.setInt(8, sortOrder);
                    statement.setBoolean(9, enabled);
                    statement.executeUpdate();
                }
            }
        }

        return getEditorState();
    }

    public JsonObject deletePrize(int prizeId) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM reward_track_prizes WHERE id = ? LIMIT 1")) {
            statement.setInt(1, prizeId);
            statement.executeUpdate();
        }

        return getEditorState();
    }

    private Track loadTrack(Connection connection, String requestedCode) throws SQLException {
        if (requestedCode != null && !requestedCode.isBlank()) {
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT * FROM reward_tracks WHERE code = ? AND enabled = 1 LIMIT 1")) {
                statement.setString(1, requestedCode);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) return mapTrack(set);
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM reward_tracks WHERE enabled = 1 ORDER BY sort_order ASC, id ASC LIMIT 1")) {
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? mapTrack(set) : null;
            }
        }
    }

    private Track loadTrackById(Connection connection, int trackId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT * FROM reward_tracks WHERE id = ? AND enabled = 1 LIMIT 1")) {
            statement.setInt(1, trackId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? mapTrack(set) : null;
            }
        }
    }

    private Track loadTrackForEditor(Connection connection, String requestedCode) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT * FROM reward_tracks WHERE code = ? LIMIT 1")) {
            statement.setString(1, requestedCode);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? mapTrack(set) : null;
            }
        }
    }

    private JsonArray loadTasks(Connection connection, int trackId, Map<Integer, Integer> progress)
            throws SQLException {
        JsonArray tasks = new JsonArray();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM reward_track_tasks WHERE track_id = ? AND enabled = 1 ORDER BY sort_order ASC, id ASC")) {
            statement.setInt(1, trackId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int taskId = set.getInt("id");
                    int currentProgress = progress.getOrDefault(taskId, 0);
                    JsonArray levels = loadLevels(connection, taskId, currentProgress);
                    JsonObject task = new JsonObject();
                    task.addProperty("id", taskId);
                    task.addProperty("actionType", set.getString("action_type"));
                    task.addProperty("title", set.getString("title"));
                    task.addProperty("description", set.getString("description"));
                    task.addProperty("tip", set.getString("tip"));
                    task.addProperty("tipAction", set.getString("tip_action"));
                    task.addProperty("tipButton", set.getString("tip_button"));
                    task.addProperty("progress", currentProgress);
                    task.addProperty("target", firstLevelTarget(levels));
                    task.addProperty("points", firstLevelPoints(levels));
                    task.addProperty("complete", allLevelsComplete(levels));
                    task.add("levels", levels);
                    tasks.add(task);
                }
            }
        }

        return tasks;
    }

    private JsonArray loadLevels(Connection connection, int taskId, int progress) throws SQLException {
        JsonArray levels = new JsonArray();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM reward_track_task_levels WHERE task_id = ? ORDER BY level ASC")) {
            statement.setInt(1, taskId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int target = set.getInt("target");
                    JsonObject level = new JsonObject();
                    level.addProperty("level", set.getInt("level"));
                    level.addProperty("target", target);
                    level.addProperty("points", set.getInt("reward_points"));
                    level.addProperty("progress", Math.min(progress, target));
                    level.addProperty("complete", progress >= target);
                    levels.add(level);
                }
            }
        }

        return levels;
    }

    private JsonArray loadPrizes(
            Connection connection, int trackId, Set<Integer> claims, boolean premiumUnlocked, int points)
            throws SQLException {
        JsonArray prizes = new JsonArray();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM reward_track_prizes WHERE track_id = ? AND enabled = 1 ORDER BY premium ASC, sort_order ASC, id ASC")) {
            statement.setInt(1, trackId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int id = set.getInt("id");
                    int requiredPoints = set.getInt("required_points");
                    boolean premium = set.getBoolean("premium");
                    JsonObject prize = new JsonObject();
                    prize.addProperty("id", id);
                    prize.addProperty("requiredPoints", requiredPoints);
                    prize.addProperty("premium", premium);
                    prize.addProperty("rewardType", set.getString("reward_type"));
                    prize.addProperty("rewardCode", set.getString("reward_code"));
                    prize.addProperty("rewardAmount", set.getInt("reward_amount"));
                    prize.addProperty("actionType", set.getString("action_type"));
                    prize.addProperty("claimed", claims.contains(id));
                    prize.addProperty("available", points >= requiredPoints && (!premium || premiumUnlocked));
                    prizes.add(prize);
                }
            }
        }

        return prizes;
    }

    private Prize loadPrize(Connection connection, int prizeId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT p.*, t.code AS track_code FROM reward_track_prizes p "
                        + "INNER JOIN reward_tracks t ON t.id = p.track_id "
                        + "WHERE p.id = ? AND p.enabled = 1 AND t.enabled = 1 LIMIT 1")) {
            statement.setInt(1, prizeId);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return null;

                Prize prize = new Prize();
                prize.id = set.getInt("id");
                prize.trackCode = set.getString("track_code");
                prize.requiredPoints = set.getInt("required_points");
                prize.premium = set.getBoolean("premium");
                prize.rewardType = set.getString("reward_type");
                prize.rewardCode = set.getString("reward_code");
                prize.rewardAmount = set.getInt("reward_amount");
                return prize;
            }
        }
    }

    private JsonArray loadEditorTasks(Connection connection, int trackId) throws SQLException {
        JsonArray tasks = new JsonArray();

        try (PreparedStatement statement =
                connection.prepareStatement("SELECT t.*, l.target, l.reward_points FROM reward_track_tasks t "
                        + "LEFT JOIN reward_track_task_levels l ON l.task_id = t.id AND l.level = 1 "
                        + "WHERE t.track_id = ? ORDER BY t.sort_order ASC, t.id ASC")) {
            statement.setInt(1, trackId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    JsonObject task = new JsonObject();
                    task.addProperty("id", set.getInt("id"));
                    task.addProperty("trackId", set.getInt("track_id"));
                    task.addProperty("actionType", set.getString("action_type"));
                    task.addProperty("title", set.getString("title"));
                    task.addProperty("description", set.getString("description"));
                    task.addProperty("tip", set.getString("tip"));
                    task.addProperty("tipAction", set.getString("tip_action"));
                    task.addProperty("tipButton", set.getString("tip_button"));
                    task.addProperty("target", Math.max(1, set.getInt("target")));
                    task.addProperty("points", Math.max(0, set.getInt("reward_points")));
                    task.addProperty("sortOrder", set.getInt("sort_order"));
                    task.addProperty("enabled", set.getBoolean("enabled"));
                    task.add("levels", loadEditorLevels(connection, set.getInt("id")));
                    tasks.add(task);
                }
            }
        }

        return tasks;
    }

    private JsonArray loadEditorLevels(Connection connection, int taskId) throws SQLException {
        JsonArray levels = new JsonArray();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT level, target, reward_points FROM reward_track_task_levels WHERE task_id = ? ORDER BY level ASC")) {
            statement.setInt(1, taskId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    JsonObject level = new JsonObject();
                    level.addProperty("level", set.getInt("level"));
                    level.addProperty("target", Math.max(1, set.getInt("target")));
                    level.addProperty("points", Math.max(0, set.getInt("reward_points")));
                    levels.add(level);
                }
            }
        }

        return levels;
    }

    private void saveTaskLevels(
            Connection connection, int taskId, JsonObject body, int fallbackTarget, int fallbackPoints)
            throws SQLException {
        JsonArray levels =
                body != null && body.has("levels") && body.get("levels").isJsonArray()
                        ? body.getAsJsonArray("levels")
                        : new JsonArray();

        try (PreparedStatement delete =
                connection.prepareStatement("DELETE FROM reward_track_task_levels WHERE task_id = ?")) {
            delete.setInt(1, taskId);
            delete.executeUpdate();
        }

        if (levels.size() == 0) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("level", 1);
            fallback.addProperty("target", fallbackTarget);
            fallback.addProperty("points", fallbackPoints);
            levels.add(fallback);
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO reward_track_task_levels (task_id, level, target, reward_points) VALUES (?, ?, ?, ?)")) {
            int levelIndex = 1;
            for (JsonElement element : levels) {
                if (element == null || !element.isJsonObject()) continue;

                JsonObject level = element.getAsJsonObject();
                insert.setInt(1, taskId);
                insert.setInt(2, levelIndex++);
                insert.setInt(3, Math.max(1, intValue(level, "target", fallbackTarget)));
                insert.setInt(4, Math.max(0, intValue(level, "points", fallbackPoints)));
                insert.addBatch();
            }

            insert.executeBatch();
        }
    }

    private JsonArray loadEditorPrizes(Connection connection, int trackId) throws SQLException {
        JsonArray prizes = new JsonArray();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM reward_track_prizes WHERE track_id = ? ORDER BY premium ASC, sort_order ASC, id ASC")) {
            statement.setInt(1, trackId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    JsonObject prize = new JsonObject();
                    prize.addProperty("id", set.getInt("id"));
                    prize.addProperty("trackId", set.getInt("track_id"));
                    prize.addProperty("requiredPoints", set.getInt("required_points"));
                    prize.addProperty("premium", set.getBoolean("premium"));
                    prize.addProperty("rewardType", set.getString("reward_type"));
                    prize.addProperty("rewardCode", set.getString("reward_code"));
                    prize.addProperty("rewardAmount", set.getInt("reward_amount"));
                    prize.addProperty("actionType", set.getString("action_type"));
                    prize.addProperty("sortOrder", set.getInt("sort_order"));
                    prize.addProperty("enabled", set.getBoolean("enabled"));
                    prizes.add(prize);
                }
            }
        }

        return prizes;
    }

    private Map<Integer, Integer> loadProgress(Connection connection, int userId) throws SQLException {
        Map<Integer, Integer> progress = new HashMap<>();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT task_id, progress FROM user_reward_track_progress WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    progress.put(set.getInt("task_id"), set.getInt("progress"));
                }
            }
        }

        return progress;
    }

    private Set<Integer> loadClaims(Connection connection, int userId) throws SQLException {
        Set<Integer> claims = new HashSet<>();

        try (PreparedStatement statement =
                connection.prepareStatement("SELECT prize_id FROM user_reward_track_claims WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    claims.add(set.getInt("prize_id"));
                }
            }
        }

        return claims;
    }

    private boolean hasPremium(Connection connection, int userId, int trackId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM user_reward_track_premium WHERE user_id = ? AND track_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, trackId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    private void award(Connection connection, int userId, Prize prize) throws SQLException {
        if (prize.rewardAmount <= 0 || prize.rewardType == null) return;

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        String type = prize.rewardType.toLowerCase();

        if ("badge".equals(type)) {
            if (online != null) {
                online.addBadge(prize.rewardCode, "Reward Track");
                return;
            }

            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM users_badges WHERE user_id = ? AND badge_code = ? LIMIT 1")) {
                check.setInt(1, userId);
                check.setString(2, prize.rewardCode);
                try (ResultSet set = check.executeQuery()) {
                    if (set.next()) return;
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO users_badges (user_id, slot_id, badge_code) VALUES (?, 0, ?)")) {
                insert.setInt(1, userId);
                insert.setString(2, prize.rewardCode);
                insert.executeUpdate();
            }
            return;
        }

        if ("credits".equals(type)) {
            String operationId = "reward-track-prize:credits:" + userId + ":" + prize.id;
            if (online != null) {
                online.giveCredits(prize.rewardAmount, "reward_track", operationId, userId);
                return;
            }

            EconomyLedger.apply(
                    connection,
                    new EconomyOperation(
                            operationId,
                            userId,
                            userId,
                            "credit_grant",
                            "reward_track",
                            EconomyLedger.CREDITS,
                            prize.rewardAmount,
                            null,
                            "reward_track_prize:" + prize.id));
            return;
        }

        if ("currency".equals(type) || "points".equals(type)) {
            int currencyType = safeInt(prize.rewardCode, 0);
            String operationId = "reward-track-prize:currency:" + userId + ":" + prize.id + ":" + currencyType;
            if (online != null) {
                online.givePoints(currencyType, prize.rewardAmount, "reward_track", operationId, userId);
                return;
            }

            EconomyLedger.apply(
                    connection,
                    new EconomyOperation(
                            operationId,
                            userId,
                            userId,
                            "currency_grant",
                            "reward_track",
                            currencyType,
                            prize.rewardAmount,
                            null,
                            "reward_track_prize:" + prize.id));
        }
    }

    private static int pointsFromTasks(JsonArray tasks) {
        int points = 0;
        for (int index = 0; index < tasks.size(); index++) {
            JsonArray levels = tasks.get(index).getAsJsonObject().getAsJsonArray("levels");
            for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                JsonObject level = levels.get(levelIndex).getAsJsonObject();
                if (level.get("complete").getAsBoolean())
                    points += level.get("points").getAsInt();
            }
        }
        return points;
    }

    private static int completedTaskCount(JsonArray tasks) {
        int count = 0;
        for (int index = 0; index < tasks.size(); index++) {
            JsonArray levels = tasks.get(index).getAsJsonObject().getAsJsonArray("levels");
            for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                if (levels.get(levelIndex).getAsJsonObject().get("complete").getAsBoolean()) count++;
            }
        }
        return count;
    }

    private static int totalTaskCount(JsonArray tasks) {
        int count = 0;
        for (int index = 0; index < tasks.size(); index++) {
            count += tasks.get(index).getAsJsonObject().getAsJsonArray("levels").size();
        }
        return count;
    }

    private static int firstLevelTarget(JsonArray levels) {
        return levels.size() > 0 ? levels.get(0).getAsJsonObject().get("target").getAsInt() : 1;
    }

    private static int firstLevelPoints(JsonArray levels) {
        return levels.size() > 0 ? levels.get(0).getAsJsonObject().get("points").getAsInt() : 10;
    }

    private static boolean allLevelsComplete(JsonArray levels) {
        if (levels.size() == 0) return false;

        for (int index = 0; index < levels.size(); index++) {
            if (!levels.get(index).getAsJsonObject().get("complete").getAsBoolean()) return false;
        }

        return true;
    }

    private static Track mapTrack(ResultSet set) throws SQLException {
        Track track = new Track();
        track.id = set.getInt("id");
        track.code = set.getString("code");
        track.title = set.getString("title");
        track.description = set.getString("description");
        track.instructions = set.getString("instructions");
        track.theme = set.getString("theme");
        track.enabled = set.getBoolean("enabled");
        track.premiumEnabled = set.getBoolean("premium_enabled");
        track.premiumCostCredits = set.getInt("premium_cost_credits");
        track.premiumCostDiamonds = set.getInt("premium_cost_diamonds");
        return track;
    }

    private static JsonArray editorActions() {
        JsonArray actions = new JsonArray();
        addAction(actions, "enter_other_users_room", "Visit another user's room", true);
        addAction(actions, "chat_with_someone", "Chat in a room with users", true);
        addAction(actions, "roller_disco", "Skate on roller tiles", true);
        addAction(actions, "find_hotspot", "Use hotspot furni", true);
        addAction(actions, "find_movie_screen", "Find movie screen", true);
        addAction(actions, "dance", "Dance in a room", true);
        addAction(actions, "jump", "Jump in a room", true);
        addAction(actions, "sign_10", "Show sign 10", true);
        addAction(actions, "dance_party_host", "Host a dance party", true);
        addAction(actions, "wave", "Wave in a room", true);
        addAction(actions, "use_habbicon", "Use a Habbicon", true);
        addAction(actions, "give_respect", "Give respect", true);
        addAction(actions, "request_friend", "Send a friend request", true);
        addAction(actions, "follow_friend", "Follow a friend", true);
        addAction(actions, "buy_from_catalogue", "Buy from catalogue", true);
        addAction(actions, "place_item", "Place furni", true);
        addAction(actions, "move_item", "Move furni", true);
        addAction(actions, "rotate_item", "Rotate furni", true);
        addAction(actions, "switch_item_state", "Use furni", true);
        addAction(actions, "wear_badge", "Wear a badge", true);
        addAction(actions, "change_motto", "Change motto", true);
        addAction(actions, "change_figure", "Change look", true);
        addAction(actions, "love_effect", "Wear love effect", true);
        addAction(actions, "skate_effect", "Wear skate effect", true);
        addAction(actions, "daily_1734011773612", "Daily: use barbecue", true);
        addAction(actions, "daily_1734079966714", "Daily: use boat", true);
        addAction(actions, "daily_1734080389144", "Daily: get carrot", true);
        addAction(actions, "daily_1734083261885", "Daily: use castle tower", true);
        addAction(actions, "daily_1734083605369", "Daily: use DJ table", true);
        addAction(actions, "daily_1734084263725", "Daily: use shark fin", true);
        addAction(actions, "daily_1734084662772", "Daily: get happy hour drink", true);
        addAction(actions, "daily_1734084926818", "Daily: walk jungle furni", true);
        addAction(actions, "daily_1734085631955", "Daily: use movie screen", true);
        addAction(actions, "daily_1734085847884", "Daily: use chill bed", true);
        addAction(actions, "daily_1734090080825", "Daily: get orange", true);
        addAction(actions, "daily_1734091081760", "Daily: use parasol", true);
        addAction(actions, "daily_1735032138894", "Daily: use picnic basket", true);
        addAction(actions, "daily_1735032866235", "Daily: use rail", true);
        addAction(actions, "daily_1735033170628", "Daily: walk ramp", true);
        addAction(actions, "daily_1736418045621", "Daily: sit on chair", true);
        addAction(actions, "daily_1736418692846", "Daily: use shell", true);
        addAction(actions, "daily_1736419490512", "Daily: use shower", true);
        addAction(actions, "daily_1738669228963", "Daily: use tower base", true);
        addAction(actions, "daily_1738669470836", "Daily: use waterfall", true);
        addAction(actions, "daily_1738670028343", "Daily: use football", true);
        addAction(actions, "daily_1738671194164", "Daily: walk skate tile", true);
        addAction(actions, "daily_1738672844085", "Daily: walk sidewalk", true);
        addAction(actions, "daily_1741863086174", "Daily: use lever", true);
        addAction(actions, "daily_1742193348839", "Daily: get coffee", true);
        addAction(actions, "daily_1742194750225", "Daily: get smoothie", true);
        addAction(actions, "daily_1742195901294", "Daily: get ice cream", true);
        addAction(actions, "daily_1742199338599", "Daily: get orange juice", true);
        addAction(actions, "daily_1742369952117", "Daily: use teleport", true);
        addAction(actions, "daily_1742371344744", "Daily: sit occupied chair", true);
        addAction(actions, "daily_1742379817871", "Daily: sleep", true);
        return actions;
    }

    private static void addAction(JsonArray actions, String value, String label, boolean tracked) {
        JsonObject action = new JsonObject();
        action.addProperty("value", value);
        action.addProperty("label", label);
        action.addProperty("tracked", tracked);
        actions.add(action);
    }

    private static JsonArray editorRewardTypes() {
        JsonArray types = new JsonArray();
        types.add("badge");
        types.add("credits");
        types.add("currency");
        return types;
    }

    private static JsonObject error(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }

    private static int safeInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String stringValue(JsonObject body, String key, String fallback) {
        if (body == null || !body.has(key) || body.get(key).isJsonNull()) return fallback;

        String value = body.get(key).getAsString();
        return value == null ? fallback : value.trim();
    }

    private static int intValue(JsonObject body, String key, int fallback) {
        try {
            if (body == null || !body.has(key) || body.get(key).isJsonNull()) return fallback;
            return body.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean boolValue(JsonObject body, String key, boolean fallback) {
        try {
            if (body == null || !body.has(key) || body.get(key).isJsonNull()) return fallback;
            return body.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static final class Track {
        int id;
        String code;
        String title;
        String description;
        String instructions;
        String theme;
        boolean enabled;
        boolean premiumEnabled;
        int premiumCostCredits;
        int premiumCostDiamonds;
    }

    private static final class Prize {
        int id;
        String trackCode;
        int requiredPoints;
        boolean premium;
        String rewardType;
        String rewardCode;
        int rewardAmount;
    }
}

package com.eu.habbo.habbohotel.dailytasks;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DailyTaskManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyTaskManager.class);
    private static final int DAILY_TASK_LIMIT = 3;
    private static final Random ASSIGNMENT_RANDOM = new SecureRandom();

    public record WiredProgress(boolean active, int value, int createdAt, int updatedAt) {}

    public WiredProgress getWiredProgress(int userId, int taskId) {
        if (userId <= 0 || taskId <= 0) return new WiredProgress(false, 0, 0, 0);
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT p.progress, p.accepted_at, p.updated_at FROM user_daily_task_progress p INNER JOIN daily_tasks t ON t.id = p.task_id AND t.enabled = 1 WHERE p.user_id = ? AND p.task_id = ? AND p.task_date = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, taskId);
            statement.setDate(3, Date.valueOf(LocalDate.now()));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? new WiredProgress(
                                true, rows.getInt("progress"), rows.getInt("accepted_at"), rows.getInt("updated_at"))
                        : new WiredProgress(false, 0, 0, 0);
            }
        } catch (SQLException error) {
            LOGGER.warn("Could not read daily task {} for user {}", taskId, userId, error);
            return new WiredProgress(false, 0, 0, 0);
        }
    }

    public JsonObject getState(int userId) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            JsonObject payload = new JsonObject();
            payload.addProperty("today", LocalDate.now().toString());
            payload.addProperty(
                    "nextRefreshAt",
                    LocalDate.now()
                            .plusDays(1)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli());
            payload.add("current", taskJson(loadCurrent(connection, userId)));
            payload.add("easy", taskJson(loadOffer(connection, userId, "easy")));
            payload.add("hard", taskJson(loadOffer(connection, userId, "hard")));
            payload.add("tasks", loadTasks(connection, userId));
            payload.add("unclaimed", loadUnclaimed(connection, userId));
            notifyWiredProgress(userId);
            return payload;
        }
    }

    public JsonObject accept(int userId, String difficulty) throws SQLException {
        String normalizedDifficulty = "hard".equalsIgnoreCase(difficulty) ? "hard" : "easy";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Task current = loadCurrent(connection, userId);
                if (current != null && current.completedAt > 0) {
                    connection.commit();
                    return getState(userId);
                }

                Task offer = loadOffer(connection, userId, normalizedDifficulty);
                if (offer == null) {
                    connection.rollback();
                    return error("No daily task is available.");
                }

                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM user_daily_task_progress WHERE user_id = ? AND task_date = ? AND completed_at = 0")) {
                    delete.setInt(1, userId);
                    delete.setDate(2, Date.valueOf(LocalDate.now()));
                    delete.executeUpdate();
                }

                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO user_daily_task_progress (user_id, task_id, task_date, progress, accepted_at, updated_at) VALUES (?, ?, ?, 0, ?, ?)")) {
                    int now = Emulator.getIntUnixTimestamp();
                    insert.setInt(1, userId);
                    insert.setInt(2, offer.id);
                    insert.setDate(3, Date.valueOf(LocalDate.now()));
                    insert.setInt(4, now);
                    insert.setInt(5, now);
                    insert.executeUpdate();
                }

                connection.commit();
                return getState(userId);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public JsonObject cancel(int userId) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM user_daily_task_progress WHERE user_id = ? AND task_date = ? AND completed_at = 0")) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(LocalDate.now()));
            statement.executeUpdate();
        }

        return getState(userId);
    }

    public int addProgress(int userId, String actionType, int amount) {
        if (userId <= 0 || actionType == null || actionType.isBlank() || amount <= 0) return 0;

        int updated;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            updated = addProgress(connection, userId, actionType, amount);
        } catch (SQLException e) {
            LOGGER.warn("DailyTaskManager -> Could not add progress for user {} action {}", userId, actionType, e);
            return 0;
        }
        // Notifications and achievements can acquire their own connections.
        // Release the committed write connection before invoking them.
        if (updated > 0) syncAchievements(userId);
        return updated;
    }

    private int addProgress(Connection connection, int userId, String actionType, int amount) throws SQLException {
        connection.setAutoCommit(false);
        try {
            int updatedTasks = addProgressToMatchingTasks(connection, userId, actionType, amount);
            if (updatedTasks > 0) {
                connection.commit();
                LOGGER.debug(
                        "DailyTaskManager -> Progress action={} user={} amount={} updatedTasks={}",
                        actionType,
                        userId,
                        amount,
                        updatedTasks);
                return updatedTasks;
            }

            Task current = loadCurrentForUpdate(connection, userId);
            List<Integer> assignedTaskIds = loadAssignedTaskIds(connection, userId);
            if (current == null
                    || current.completedAt > 0
                    || !actionType.equals(current.actionType)
                    || !assignedTaskIds.contains(current.id)) {
                connection.commit();
                return 0;
            }

            int progress = Math.min(current.target, current.progress + amount);
            int now = Emulator.getIntUnixTimestamp();
            boolean completed = current.progress < current.target && progress >= current.target;

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE user_daily_task_progress SET progress = ?, updated_at = ?, completed_at = ?, reward_claimed_at = ? WHERE user_id = ? AND task_id = ? AND task_date = ?")) {
                update.setInt(1, progress);
                update.setInt(2, now);
                update.setInt(3, completed ? now : current.completedAt);
                update.setInt(4, current.rewardClaimedAt);
                update.setInt(5, userId);
                update.setInt(6, current.id);
                update.setDate(7, Date.valueOf(LocalDate.now()));
                update.executeUpdate();
            }

            connection.commit();
            LOGGER.debug(
                    "DailyTaskManager -> Progress action={} user={} amount={} completed={}",
                    actionType,
                    userId,
                    amount,
                    completed);
            return 1;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void notifyWiredProgress(int userId) {
        var environment = Emulator.getGameEnvironment();
        if (environment == null) return;
        Habbo habbo = environment.getHabboManager().getHabbo(userId);
        if (habbo != null && habbo.getHabboInfo().getCurrentRoom() != null)
            habbo.getHabboInfo().getCurrentRoom().getUserVariableManager().refreshDailyTaskVariables(userId);
    }

    private void syncAchievements(int userId) {
        notifyWiredProgress(userId);
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (habbo == null) return;
        var dataSource = Emulator.getDatabase().getDataSource();
        try {
            Integer completed = null;
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT COUNT(*) FROM user_daily_task_progress WHERE user_id = ? AND completed_at > 0")) {
                statement.setInt(1, userId);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) completed = set.getInt(1);
                }
            }
            if (completed != null) AchievementInteractions.sync(habbo, "DailyTasksCompleted", completed);

            int streak = 0;
            LocalDate day = LocalDate.now();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT DISTINCT task_date FROM user_daily_task_progress WHERE user_id = ? AND task_date <= ? AND completed_at > 0 ORDER BY task_date DESC")) {
                statement.setInt(1, userId);
                statement.setDate(2, Date.valueOf(day));
                statement.setFetchSize(128);
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        if (!set.getDate(1).toLocalDate().equals(day)) break;
                        streak++;
                        day = day.minusDays(1);
                    }
                }
            }
            AchievementInteractions.sync(habbo, "DailyTaskStreakDays", streak);
        } catch (SQLException e) {
            LOGGER.debug("DailyTaskManager -> Could not sync achievements for user {}", userId, e);
        }
    }

    private int addProgressToMatchingTasks(Connection connection, int userId, String actionType, int amount)
            throws SQLException {
        int now = Emulator.getIntUnixTimestamp();
        List<Integer> assignedTaskIds = loadAssignedTaskIds(connection, userId);
        if (assignedTaskIds.isEmpty()) return 0;
        String assignedPlaceholders = placeholders(assignedTaskIds.size());

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO user_daily_task_progress (user_id, task_id, task_date, progress, accepted_at, updated_at) "
                        + "SELECT ?, id, ?, 0, ?, ? FROM daily_tasks WHERE enabled = 1 AND action_type = ? "
                        + "AND id IN ("
                        + assignedPlaceholders + ") " + "ON DUPLICATE KEY UPDATE updated_at = updated_at")) {
            insert.setInt(1, userId);
            insert.setDate(2, Date.valueOf(LocalDate.now()));
            insert.setInt(3, now);
            insert.setInt(4, now);
            insert.setString(5, actionType);
            setIds(insert, 6, assignedTaskIds);
            insert.executeUpdate();
        }

        int updated = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT t.*, p.task_date, p.progress, p.accepted_at, p.completed_at, p.reward_claimed_at "
                        + "FROM user_daily_task_progress p "
                        + "INNER JOIN daily_tasks t ON t.id = p.task_id "
                        + "WHERE p.user_id = ? AND p.task_date = ? AND t.enabled = 1 AND t.action_type = ? "
                        + "AND t.id IN ("
                        + assignedPlaceholders + ") FOR UPDATE")) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(LocalDate.now()));
            statement.setString(3, actionType);
            setIds(statement, 4, assignedTaskIds);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Task task = mapTask(set, true);
                    if (task.completedAt > 0) continue;

                    int progress = Math.min(task.target, task.progress + amount);
                    boolean completed = task.progress < task.target && progress >= task.target;

                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE user_daily_task_progress SET progress = ?, updated_at = ?, completed_at = ?, reward_claimed_at = ? "
                                    + "WHERE user_id = ? AND task_id = ? AND task_date = ?")) {
                        update.setInt(1, progress);
                        update.setInt(2, now);
                        update.setInt(3, completed ? now : task.completedAt);
                        update.setInt(4, task.rewardClaimedAt);
                        update.setInt(5, userId);
                        update.setInt(6, task.id);
                        update.setDate(7, Date.valueOf(LocalDate.now()));
                        updated += update.executeUpdate();
                    }
                }
            }
        }

        if (updated == 0) {
            LOGGER.debug(
                    "DailyTaskManager -> Ignored action={} user={} because it is not assigned today",
                    actionType,
                    userId);
        }

        return updated;
    }

    private Task loadCurrent(Connection connection, int userId) throws SQLException {
        return loadCurrent(connection, userId, false);
    }

    private Task loadCurrentForUpdate(Connection connection, int userId) throws SQLException {
        return loadCurrent(connection, userId, true);
    }

    private Task loadCurrent(Connection connection, int userId, boolean lock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT t.*, p.task_date, p.progress, p.accepted_at, p.completed_at, p.reward_claimed_at "
                        + "FROM user_daily_task_progress p "
                        + "INNER JOIN daily_tasks t ON t.id = p.task_id "
                        + "WHERE p.user_id = ? AND p.task_date = ? "
                        + "ORDER BY p.accepted_at DESC, p.task_id ASC LIMIT 1 "
                        + (lock ? "FOR UPDATE" : ""))) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(LocalDate.now()));

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? mapTask(set, true) : null;
            }
        }
    }

    private Task loadOffer(Connection connection, int userId, String difficulty) throws SQLException {
        int offset =
                Math.abs((LocalDate.now().getDayOfYear() + userId) % Math.max(1, countOffers(connection, difficulty)));

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT t.*, NULL AS task_date, 0 AS progress, 0 AS accepted_at, 0 AS completed_at, 0 AS reward_claimed_at "
                        + "FROM daily_tasks t WHERE t.enabled = 1 AND t.difficulty = ? ORDER BY t.sort_order, t.id LIMIT 1 OFFSET ?")) {
            statement.setString(1, difficulty);
            statement.setInt(2, offset);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? mapTask(set, false) : null;
            }
        }
    }

    private JsonArray loadTasks(Connection connection, int userId) throws SQLException {
        JsonArray tasks = new JsonArray();
        List<Integer> assignedTaskIds = loadAssignedTaskIds(connection, userId);
        if (assignedTaskIds.isEmpty()) return tasks;

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT t.*, p.task_date, COALESCE(p.progress, 0) AS progress, COALESCE(p.accepted_at, 0) AS accepted_at, "
                        + "COALESCE(p.completed_at, 0) AS completed_at, COALESCE(p.reward_claimed_at, 0) AS reward_claimed_at "
                        + "FROM daily_tasks t "
                        + "LEFT JOIN user_daily_task_progress p ON p.task_id = t.id AND p.user_id = ? AND p.task_date = ? "
                        + "WHERE t.enabled = 1 AND t.id IN ("
                        + placeholders(assignedTaskIds.size()) + ") " + "ORDER BY FIELD(t.id, "
                        + placeholders(assignedTaskIds.size()) + ")")) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(LocalDate.now()));
            int index = setIds(statement, 3, assignedTaskIds);
            setIds(statement, index, assignedTaskIds);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) tasks.add(taskJson(mapTask(set, true)));
            }
        }

        return tasks;
    }

    private JsonArray loadUnclaimed(Connection connection, int userId) throws SQLException {
        JsonArray tasks = new JsonArray();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT t.*, p.task_date, p.progress, p.accepted_at, p.completed_at, p.reward_claimed_at "
                        + "FROM user_daily_task_progress p "
                        + "INNER JOIN daily_tasks t ON t.id = p.task_id "
                        + "WHERE p.user_id = ? AND p.completed_at > 0 AND p.reward_claimed_at = 0 "
                        + "ORDER BY p.task_date DESC, p.completed_at DESC LIMIT 20")) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) tasks.add(taskJson(mapTask(set, true)));
            }
        }

        return tasks;
    }

    private List<Integer> loadAssignedTaskIds(Connection connection, int userId) throws SQLException {
        LocalDate today = LocalDate.now();
        List<Integer> assignedIds = findAssignedTaskIds(connection, userId, today);
        if (!assignedIds.isEmpty()) return assignedIds;

        List<Integer> ids = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM daily_tasks WHERE enabled = 1 ORDER BY sort_order, id");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) ids.add(set.getInt("id"));
        }

        if (ids.size() > DAILY_TASK_LIMIT) {
            Collections.shuffle(ids, ASSIGNMENT_RANDOM);
            ids = new ArrayList<>(ids.subList(0, DAILY_TASK_LIMIT));
        }

        int now = Emulator.getIntUnixTimestamp();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT IGNORE INTO user_daily_task_assignments (user_id, task_date, task_id, slot, assigned_at) VALUES (?, ?, ?, ?, ?)")) {
            int slot = 0;
            for (int id : ids) {
                insert.setInt(1, userId);
                insert.setDate(2, Date.valueOf(today));
                insert.setInt(3, id);
                insert.setInt(4, slot++);
                insert.setInt(5, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        assignedIds = findAssignedTaskIds(connection, userId, today);
        return assignedIds.isEmpty() ? ids : assignedIds;
    }

    private List<Integer> findAssignedTaskIds(Connection connection, int userId, LocalDate today) throws SQLException {
        List<Integer> ids = new ArrayList<>();

        try (PreparedStatement statement =
                connection.prepareStatement("SELECT a.task_id FROM user_daily_task_assignments a "
                        + "INNER JOIN daily_tasks t ON t.id = a.task_id AND t.enabled = 1 "
                        + "WHERE a.user_id = ? AND a.task_date = ? ORDER BY a.slot ASC")) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(today));

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) ids.add(set.getInt("task_id"));
            }
        }

        return ids;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(Math.max(1, count), "?"));
    }

    private static int setIds(PreparedStatement statement, int startIndex, List<Integer> ids) throws SQLException {
        int index = startIndex;
        for (int id : ids) {
            statement.setInt(index++, id);
        }

        return index;
    }

    public JsonObject claim(int userId, int taskId, String taskDate) throws SQLException {
        LocalDate date = taskDate == null || taskDate.isBlank() ? LocalDate.now() : LocalDate.parse(taskDate);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Task task = null;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT t.*, p.task_date, p.progress, p.accepted_at, p.completed_at, p.reward_claimed_at "
                                + "FROM user_daily_task_progress p "
                                + "INNER JOIN daily_tasks t ON t.id = p.task_id "
                                + "WHERE p.user_id = ? AND p.task_id = ? AND p.task_date = ? FOR UPDATE")) {
                    statement.setInt(1, userId);
                    statement.setInt(2, taskId);
                    statement.setDate(3, Date.valueOf(date));

                    try (ResultSet set = statement.executeQuery()) {
                        if (set.next()) task = mapTask(set, true);
                    }
                }

                if (task == null || task.completedAt <= 0) {
                    connection.rollback();
                    return error("Daily task is not complete.");
                }

                if (task.rewardClaimedAt > 0) {
                    connection.commit();
                    return getState(userId);
                }

                award(connection, userId, task);

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE user_daily_task_progress SET reward_claimed_at = ?, updated_at = ? WHERE user_id = ? AND task_id = ? AND task_date = ?")) {
                    int now = Emulator.getIntUnixTimestamp();
                    update.setInt(1, now);
                    update.setInt(2, now);
                    update.setInt(3, userId);
                    update.setInt(4, taskId);
                    update.setDate(5, Date.valueOf(date));
                    update.executeUpdate();
                }

                connection.commit();
                return getState(userId);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private int countOffers(Connection connection, String difficulty) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT COUNT(*) FROM daily_tasks WHERE enabled = 1 AND difficulty = ?")) {
            statement.setString(1, difficulty);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        }
    }

    private void award(Connection connection, int userId, Task task) throws SQLException {
        if (task.rewardType == null) return;

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        String type = task.rewardType.toLowerCase();
        String taskDate = task.taskDate == null || task.taskDate.isBlank()
                ? LocalDate.now().toString()
                : task.taskDate;

        if ("badge".equals(type)) {
            if (task.rewardCode == null || task.rewardCode.isBlank()) return;

            if (online != null) {
                online.addBadge(task.rewardCode, "Daily Task");
                return;
            }

            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM users_badges WHERE user_id = ? AND badge_code = ? LIMIT 1")) {
                check.setInt(1, userId);
                check.setString(2, task.rewardCode);
                try (ResultSet set = check.executeQuery()) {
                    if (set.next()) return;
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO users_badges (user_id, slot_id, badge_code) VALUES (?, 0, ?)")) {
                insert.setInt(1, userId);
                insert.setString(2, task.rewardCode);
                insert.executeUpdate();
            }
            return;
        }

        if ("credits".equals(type)) {
            if (task.rewardAmount <= 0) return;

            String operationId = "daily-task:credits:" + userId + ":" + task.id + ":" + taskDate;
            if (online != null) {
                online.giveCredits(task.rewardAmount, "daily_task", operationId, userId);
                return;
            }

            EconomyLedger.apply(
                    connection,
                    new EconomyOperation(
                            operationId,
                            userId,
                            userId,
                            "credit_grant",
                            "daily_task",
                            EconomyLedger.CREDITS,
                            task.rewardAmount,
                            null,
                            "daily_task:" + task.id));
            return;
        }

        if ("currency".equals(type) || "points".equals(type)) {
            if (task.rewardAmount <= 0) return;

            int currencyType = safeInt(task.rewardCode, 0);
            String operationId = "daily-task:currency:" + userId + ":" + task.id + ":" + taskDate + ":" + currencyType;
            if (online != null) {
                online.givePoints(currencyType, task.rewardAmount, "daily_task", operationId, userId);
                return;
            }

            EconomyLedger.apply(
                    connection,
                    new EconomyOperation(
                            operationId,
                            userId,
                            userId,
                            "currency_grant",
                            "daily_task",
                            currencyType,
                            task.rewardAmount,
                            null,
                            "daily_task:" + task.id));
        }
    }

    private static JsonObject taskJson(Task task) {
        if (task == null) return null;

        JsonObject json = new JsonObject();
        json.addProperty("id", task.id);
        json.addProperty("code", task.code);
        json.addProperty("difficulty", task.difficulty);
        json.addProperty("actionType", task.actionType);
        json.addProperty("title", task.title);
        json.addProperty("description", task.description);
        json.addProperty("progress", task.progress);
        json.addProperty("target", task.target);
        json.addProperty("complete", task.progress >= task.target);
        json.addProperty("claimed", task.rewardClaimedAt > 0);
        json.addProperty("claimable", task.progress >= task.target && task.rewardClaimedAt == 0);
        json.addProperty("taskDate", task.taskDate);
        json.addProperty("rewardType", task.rewardType);
        json.addProperty("rewardCode", task.rewardCode);
        json.addProperty("rewardAmount", task.rewardAmount);
        json.addProperty("goLink", task.goLink);
        return json;
    }

    private static Task mapTask(ResultSet set, boolean includeProgress) throws SQLException {
        Task task = new Task();
        task.id = set.getInt("id");
        task.code = set.getString("code");
        task.difficulty = set.getString("difficulty");
        task.actionType = set.getString("action_type");
        task.title = set.getString("title");
        task.description = set.getString("description");
        task.target = Math.max(1, set.getInt("target"));
        task.rewardType = set.getString("reward_type");
        task.rewardCode = set.getString("reward_code");
        task.rewardAmount = set.getInt("reward_amount");
        task.goLink = set.getString("go_link");
        task.progress = includeProgress ? Math.max(0, set.getInt("progress")) : 0;
        task.acceptedAt = set.getInt("accepted_at");
        task.completedAt = set.getInt("completed_at");
        task.rewardClaimedAt = set.getInt("reward_claimed_at");
        Date taskDate = set.getDate("task_date");
        task.taskDate = taskDate != null
                ? taskDate.toLocalDate().toString()
                : LocalDate.now().toString();
        return task;
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

    private static final class Task {
        int id;
        String code;
        String difficulty;
        String actionType;
        String title;
        String description;
        int target;
        String rewardType;
        String rewardCode;
        int rewardAmount;
        String goLink;
        int progress;
        int acceptedAt;
        int completedAt;
        int rewardClaimedAt;
        String taskDate;
    }
}

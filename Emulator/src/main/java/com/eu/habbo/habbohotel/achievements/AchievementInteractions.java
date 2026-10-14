package com.eu.habbo.habbohotel.achievements;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small, null-safe bridge used by gameplay interactions.  Achievement names
 * are data-driven (gamedata/achievements.json), therefore an interaction must
 * never fail just because a seasonal achievement is not loaded.
 */
public final class AchievementInteractions {
    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementInteractions.class);

    private AchievementInteractions() {}

    public static void progress(Habbo habbo, String name) {
        progress(habbo, name, 1);
    }

    public static void progress(Habbo habbo, String name, int amount) {
        if (habbo == null || name == null || name.isBlank() || amount <= 0) return;
        Achievement achievement = achievement(name);
        if (achievement != null) AchievementManager.progressAchievement(habbo, achievement, amount);
    }

    /** Movement/pet cycles publish the same increment from a persistence worker. */
    public static void progressDeferred(Habbo habbo, String name) {
        if (habbo == null || name == null || name.isBlank()) return;
        Achievement achievement = achievement(name);
        AchievementManager.flushProgressAchievement(habbo.getHabboInfo().getId(), achievement, 1);
    }

    public static void progressUser(int userId, String name) {
        progressUser(userId, name, 1);
    }

    public static void progressUser(int userId, String name, int amount) {
        if (userId <= 0 || name == null || name.isBlank() || amount <= 0) return;
        Achievement achievement = achievement(name);
        if (achievement != null) AchievementManager.progressAchievement(userId, achievement, amount);
    }

    /** Bring a cumulative achievement to an observed counter without double counting. */
    public static void sync(Habbo habbo, String name, int value) {
        if (habbo == null || name == null || value <= 0) return;
        Achievement achievement = achievement(name);
        if (achievement == null) return;
        int current = habbo.getHabboStats().getAchievementProgress(achievement);
        if (current < 0) current = 0;
        if (value > current) AchievementManager.progressAchievement(habbo, achievement, value - current);
    }

    /** Counts a user action only once for a stable key, such as one chat style. */
    public static void progressUnique(Habbo habbo, String name, String key) {
        if (habbo == null || name == null || name.isBlank() || key == null || key.isBlank()) return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("INSERT IGNORE INTO users_achievement_unique_events "
                                + "(user_id, achievement_name, event_key) VALUES (?, ?, ?)")) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setString(2, name);
            statement.setString(3, key);
            if (statement.executeUpdate() > 0) progress(habbo, name);
        } catch (SQLException exception) {
            LOGGER.error("Could not store unique achievement event {}:{}", name, key, exception);
        }
    }

    private static Achievement achievement(String name) {
        return Emulator.getGameEnvironment().getAchievementManager().getAchievement(name);
    }
}

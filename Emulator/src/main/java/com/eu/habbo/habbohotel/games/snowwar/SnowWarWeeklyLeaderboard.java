package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persistent weekly SnowStorm scoreboard and SWF achievement awards. */
final class SnowWarWeeklyLeaderboard {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarWeeklyLeaderboard.class);

    private SnowWarWeeklyLeaderboard() {}

    static void recordGame(Collection<SnowWarGamePlayer> players) {
        if (players == null || players.isEmpty()) return;

        LocalDate currentWeek = currentWeekStart();
        List<Award> awards = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                collectAndMarkClosedWeeks(connection, currentWeek, awards);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO snowwar_weekly_scores (user_id, week_start, score) VALUES (?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE score = snowwar_weekly_scores.score + VALUES(score)")) {
                    Date week = Date.valueOf(currentWeek);
                    for (SnowWarGamePlayer player : players) {
                        int score = Math.max(0, player.getAttributes().getScore().get());
                        if (score <= 0) continue;
                        statement.setInt(1, player.getUserId());
                        statement.setDate(2, week);
                        statement.setInt(3, score);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }

            for (Award award : awards) {
                AchievementInteractions.progressUser(award.userId, award.achievementName);
            }
        } catch (SQLException exception) {
            LOGGER.warn("Could not persist SnowStorm weekly leaderboard", exception);
        }
    }

    private static void collectAndMarkClosedWeeks(
            Connection connection, LocalDate currentWeek, List<Award> awards) throws SQLException {
        try (PreparedStatement weeks = connection.prepareStatement(
                "SELECT DISTINCT week_start FROM snowwar_weekly_scores WHERE week_start < ? ORDER BY week_start")) {
            weeks.setDate(1, Date.valueOf(currentWeek));
            try (ResultSet weekSet = weeks.executeQuery()) {
                while (weekSet.next()) {
                    LocalDate week = weekSet.getDate(1).toLocalDate();
                    collectAwardsForWeek(connection, week, awards);
                }
            }
        }
    }

    private static void collectAwardsForWeek(
            Connection connection, LocalDate week, List<Award> awards) throws SQLException {
        List<Integer> ranking = new ArrayList<>();
        try (PreparedStatement standings = connection.prepareStatement(
                "SELECT user_id FROM snowwar_weekly_scores WHERE week_start = ? ORDER BY score DESC, user_id ASC")) {
            standings.setDate(1, Date.valueOf(week));
            try (ResultSet result = standings.executeQuery()) {
                while (result.next()) ranking.add(result.getInt(1));
            }
        }

        if (ranking.isEmpty()) return;
        for (int index = 0; index < Math.min(10, ranking.size()); index++) {
            int userId = ranking.get(index);
            if (insertAward(connection, userId, week, "top_ten")) {
                awards.add(new Award(userId, "SnowWarWeeklyTopTen"));
            }
        }
        if (insertAward(connection, ranking.get(0), week, "best")) {
            awards.add(new Award(ranking.get(0), "SnowWarWeeklyBest"));
        }
    }

    private static boolean insertAward(Connection connection, int userId, LocalDate week, String awardType)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO snowwar_weekly_awards (user_id, week_start, award_type) VALUES (?, ?, ?)")) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(week));
            statement.setString(3, awardType);
            return statement.executeUpdate() == 1;
        }
    }

    private static LocalDate currentWeekStart() {
        String timezone = Emulator.getConfig().getValue("hotel.timezone", ZoneId.systemDefault().getId());
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (Exception ignored) {
            zone = ZoneId.systemDefault();
        }
        return LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private record Award(int userId, String achievementName) {}
}

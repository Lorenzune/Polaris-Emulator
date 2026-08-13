package com.eu.habbo.habbohotel.earnings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.settings.HotelSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Assigns the non-cumulative daily login gift to the earnings center. */
public final class DailyGiftManager {
    static final String CREDITS_SETTING = "daily_gift.credits";
    static final String DUCKETS_SETTING = "daily_gift.duckets";
    static final String DIAMONDS_SETTING = "daily_gift.diamonds";
    static final String GIFT_REASON = "daily_gift.login";
    private static final int MAX_DAILY_AMOUNT = 1_000_000;
    private static final Logger LOGGER = LoggerFactory.getLogger(DailyGiftManager.class);
    private static final String LOCK_USER = "SELECT id FROM users WHERE id = ? FOR UPDATE";
    private static final String HAS_ASSIGNMENT = """
            SELECT 1 FROM users_daily_gift_assignments
            WHERE user_id = ? AND gift_date = ?
            LIMIT 1
            """;
    private static final String INSERT_ASSIGNMENT = """
            INSERT INTO users_daily_gift_assignments
                (user_id, gift_date, credits, duckets, diamonds)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String DELETE_UNCLAIMED_GIFT = """
            DELETE FROM users_earnings_pending
            WHERE user_id = ? AND category = ? AND reason = ?
            """;

    private DailyGiftManager() {}

    public static boolean assignOnLogin(Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getId() <= 0) return false;

        int userId = habbo.getHabboInfo().getId();
        LocalDate giftDate = LocalDate.now();
        try {
            return assign(userId, giftDate);
        } catch (SQLException | RuntimeException exception) {
            // A daily gift must never prevent the authenticated user from entering the hotel.
            LOGGER.error("Daily login gift assignment failed userId={} giftDate={}", userId, giftDate, exception);
            return false;
        }
    }

    static boolean assign(int userId, LocalDate giftDate) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockUser(connection, userId);
                if (hasAssignment(connection, userId, giftDate)) {
                    connection.rollback();
                    return false;
                }

                DailyGift gift = loadGift(connection);
                if (!gift.hasRewards()) {
                    // Expire an older unclaimed login gift even while today's gift is configured as zero.
                    deletePreviousGift(connection, userId);
                    connection.commit();
                    return false;
                }

                recordAssignment(connection, userId, giftDate, gift);
                deletePreviousGift(connection, userId);
                EconomyLedger.applyBatch(connection, operations(userId, giftDate, gift));
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    static DailyGift loadGift(Connection connection) throws SQLException {
        return new DailyGift(
                HotelSettings.getNonNegativeInt(connection, CREDITS_SETTING, 0, MAX_DAILY_AMOUNT),
                HotelSettings.getNonNegativeInt(connection, DUCKETS_SETTING, 0, MAX_DAILY_AMOUNT),
                HotelSettings.getNonNegativeInt(connection, DIAMONDS_SETTING, 0, MAX_DAILY_AMOUNT));
    }

    static List<EconomyOperation> operations(int userId, LocalDate giftDate, DailyGift gift) {
        List<EconomyOperation> operations = new ArrayList<>(3);
        addOperation(operations, userId, giftDate, EconomyLedger.CREDITS, gift.credits());
        addOperation(operations, userId, giftDate, PendingCurrencyEarnings.DUCKETS, gift.duckets());
        addOperation(operations, userId, giftDate, PendingCurrencyEarnings.DIAMONDS, gift.diamonds());
        return List.copyOf(operations);
    }

    private static void addOperation(
            List<EconomyOperation> operations, int userId, LocalDate giftDate, int currencyType, int amount) {
        if (amount <= 0) return;
        operations.add(new EconomyOperation(
                "daily-gift:" + userId + ":" + giftDate + ":" + currencyType,
                userId,
                userId,
                "daily_gift",
                GIFT_REASON,
                currencyType,
                amount,
                null,
                giftDate.toString()));
    }

    private static void lockUser(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_USER)) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Unknown daily gift user " + userId);
            }
        }
    }

    private static boolean hasAssignment(Connection connection, int userId, LocalDate giftDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(HAS_ASSIGNMENT)) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(giftDate));
            return statement.executeQuery().next();
        }
    }

    private static void recordAssignment(
            Connection connection, int userId, LocalDate giftDate, DailyGift gift) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ASSIGNMENT)) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(giftDate));
            statement.setInt(3, gift.credits());
            statement.setInt(4, gift.duckets());
            statement.setInt(5, gift.diamonds());
            if (statement.executeUpdate() != 1) throw new SQLException("Daily gift assignment was not recorded");
        }
    }

    private static void deletePreviousGift(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_UNCLAIMED_GIFT)) {
            statement.setInt(1, userId);
            statement.setString(2, EarningsCategory.DAILY_GIFT.getKey());
            statement.setString(3, GIFT_REASON);
            statement.executeUpdate();
        }
    }

    record DailyGift(int credits, int duckets, int diamonds) {
        boolean hasRewards() {
            return credits > 0 || duckets > 0 || diamonds > 0;
        }
    }
}

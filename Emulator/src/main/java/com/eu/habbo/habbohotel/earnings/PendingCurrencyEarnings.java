package com.eu.habbo.habbohotel.earnings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.economy.EconomyAuditEntry;
import com.eu.habbo.habbohotel.economy.EconomyAuditLogger;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stores positive credits, duckets and diamonds until the user claims them. */
public final class PendingCurrencyEarnings {
    public static final int DUCKETS = 0;
    public static final int DIAMONDS = 5;
    public static final String CLAIM_OPERATION = "earnings_claim";
    public static final String CLAIM_REASON_PREFIX = "earnings.claim.";

    private static final String INSERT_PENDING = """
            INSERT INTO users_earnings_pending
                (operation_id, user_id, category, currency_type, amount, reason)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String READ_TOTALS = """
            SELECT currency_type, SUM(amount) AS amount
            FROM users_earnings_pending
            WHERE user_id = ? AND category = ?
            GROUP BY currency_type
            ORDER BY currency_type
            """;
    private static final String LOCK_USER = "SELECT credits FROM users WHERE id = ? FOR UPDATE";
    private static final String LOCK_ROWS = """
            SELECT id, currency_type, amount
            FROM users_earnings_pending
            WHERE user_id = ? AND category = ?
            ORDER BY id
            FOR UPDATE
            """;
    private static final String DELETE_ROWS = """
            DELETE FROM users_earnings_pending
            WHERE user_id = ? AND category = ?
            """;

    private PendingCurrencyEarnings() {}

    public static boolean shouldCapture(EconomyOperation operation) {
        if (operation == null || operation.delta() <= 0) return false;
        if (operation.currencyType() != EconomyLedger.CREDITS
                && operation.currencyType() != DUCKETS
                && operation.currencyType() != DIAMONDS) {
            return false;
        }
        return !CLAIM_OPERATION.equalsIgnoreCase(operation.operation())
                && !operation.reason().toLowerCase(Locale.ROOT).startsWith(CLAIM_REASON_PREFIX);
    }

    public static EarningsCategory categoryFor(EconomyOperation operation) {
        String source = (operation.operation() + " " + operation.reason() + " " + operation.context())
                .toLowerCase(Locale.ROOT);

        if (containsAny(source, "achievement", "traguard")) return EarningsCategory.ACHIEVEMENTS;
        if (containsAny(source, "marketplace", "market_place", "mercatino")) return EarningsCategory.MARKETPLACE;
        if (containsAny(source, "hc_payday", "payday", "habbo_club")) return EarningsCategory.HC_PAYDAY;
        if (containsAny(source, "level", "reward_track", "rewardtrack", "talent")) return EarningsCategory.LEVEL_PROGRESS;
        if (containsAny(source, "daily", "calendar", "advent", "gift")) return EarningsCategory.DAILY_GIFT;
        if (containsAny(source, "donation", "donate", "transfer", "trade", "staff.command", "system.habbo_manager")) {
            return EarningsCategory.DONATIONS;
        }
        if (containsAny(source, "mystery", "surprise", "bonus_box", "crackable", "chest")) return EarningsCategory.MYSTERY_BOXES;
        if (containsAny(source, "game", "wired", "snowstorm", "battleball", "freeze", "football", "wheel")) {
            return EarningsCategory.GAMES;
        }
        return EarningsCategory.BONUS_BAG;
    }

    public static void capture(Connection connection, EconomyOperation operation, int balanceBefore)
            throws SQLException {
        EarningsCategory category = categoryFor(operation);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_PENDING)) {
            statement.setString(1, operation.operationId());
            statement.setInt(2, operation.userId());
            statement.setString(3, category.getKey());
            statement.setInt(4, operation.currencyType());
            statement.setInt(5, operation.delta());
            statement.setString(6, operation.reason());
            statement.executeUpdate();
        }

        // The immutable audit remains the idempotency source. A queued grant
        // deliberately leaves the spendable balance unchanged.
        EconomyAuditLogger.record(connection, EconomyAuditEntry.from(operation, balanceBefore, balanceBefore));
    }

    public static List<EarningsReward> rewards(int userId, EarningsCategory category) throws SQLException {
        if (userId <= 0 || category == null) return List.of();

        List<EarningsReward> rewards = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(READ_TOTALS)) {
            statement.setInt(1, userId);
            statement.setString(2, category.getKey());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int amount = checkedAmount(result.getLong("amount"));
                    EarningsReward reward = toReward(result.getInt("currency_type"), amount);
                    if (reward != null) rewards.add(reward);
                }
            }
        }
        return List.copyOf(rewards);
    }

    public static boolean claim(Habbo habbo, EarningsCategory category) throws SQLException {
        if (habbo == null || habbo.getHabboInfo() == null || category == null) return false;

        int userId = habbo.getHabboInfo().getId();
        List<ClaimedCurrency> claimed = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockUser(connection, userId);
                PendingRows rows = lockRows(connection, userId, category);
                if (rows.count() == 0) {
                    connection.rollback();
                    return false;
                }

                for (Map.Entry<Integer, Long> total : rows.totals().entrySet()) {
                    int currencyType = total.getKey();
                    int amount = checkedAmount(total.getValue());
                    EconomyMutationResult mutation = EconomyLedger.apply(
                            connection,
                            new EconomyOperation(
                                    claimOperationId(userId, category, currencyType, rows.maxIds().get(currencyType)),
                                    userId,
                                    userId,
                                    CLAIM_OPERATION,
                                    CLAIM_REASON_PREFIX + category.getKey(),
                                    currencyType,
                                    amount,
                                    null,
                                    category.getKey()));
                    claimed.add(new ClaimedCurrency(currencyType, amount, mutation.balanceAfter()));
                }

                try (PreparedStatement statement = connection.prepareStatement(DELETE_ROWS)) {
                    statement.setInt(1, userId);
                    statement.setString(2, category.getKey());
                    if (statement.executeUpdate() != rows.count()) {
                        throw new SQLException("Pending earnings changed while claiming " + category.getKey());
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }

        for (ClaimedCurrency currency : claimed) {
            LedgerWalletMutation.applyCommitted(habbo, currency.currencyType(), currency.balanceAfter());
            sendBalance(habbo, currency);
        }
        return true;
    }

    private static void lockUser(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_USER)) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Unknown earnings user " + userId);
            }
        }
    }

    private static PendingRows lockRows(Connection connection, int userId, EarningsCategory category)
            throws SQLException {
        Map<Integer, Long> totals = new LinkedHashMap<>();
        Map<Integer, Long> maxIds = new LinkedHashMap<>();
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(LOCK_ROWS)) {
            statement.setInt(1, userId);
            statement.setString(2, category.getKey());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int currencyType = result.getInt("currency_type");
                    totals.merge(currencyType, (long) result.getInt("amount"), Math::addExact);
                    maxIds.put(currencyType, result.getLong("id"));
                    count++;
                }
            }
        }
        return new PendingRows(totals, maxIds, count);
    }

    private static EarningsReward toReward(int currencyType, int amount) {
        return switch (currencyType) {
            case EconomyLedger.CREDITS -> new EarningsReward(EarningsReward.TYPE_CREDITS, amount, 0);
            case DUCKETS -> new EarningsReward(EarningsReward.TYPE_PIXELS, amount, 0);
            case DIAMONDS -> new EarningsReward(EarningsReward.TYPE_POINTS, amount, DIAMONDS);
            default -> null;
        };
    }

    private static int checkedAmount(long amount) throws SQLException {
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            throw new SQLException("Pending earnings amount is outside the wallet range: " + amount);
        }
        return (int) amount;
    }

    private static String claimOperationId(
            int userId, EarningsCategory category, int currencyType, long lastPendingId) {
        return "earnings:" + userId + ":" + category.getKey() + ":" + currencyType + ":" + lastPendingId;
    }

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) return true;
        }
        return false;
    }

    private static void sendBalance(Habbo habbo, ClaimedCurrency currency) {
        if (habbo.getClient() == null) return;
        if (currency.currencyType() == EconomyLedger.CREDITS) {
            habbo.getClient().sendResponse(new UserCreditsComposer(habbo));
        } else if (currency.currencyType() == DUCKETS) {
            habbo.getClient().sendResponse(new UserCurrencyComposer(habbo));
        } else {
            habbo.getClient().sendResponse(new UserPointsComposer(
                    currency.balanceAfter(), currency.amount(), currency.currencyType()));
        }
    }

    private record PendingRows(Map<Integer, Long> totals, Map<Integer, Long> maxIds, int count) {}

    private record ClaimedCurrency(int currencyType, int amount, int balanceAfter) {}
}

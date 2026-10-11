package com.eu.habbo.habbohotel.users;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Keeps the legacy plugin wallet methods durable without saving stale profile snapshots. */
final class LegacyWalletMutation {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyWalletMutation.class);

    record Change(int type, int value, boolean absolute, boolean clamp) {}

    private LegacyWalletMutation() {}

    static boolean apply(HabboInfo info, HikariDataSource dataSource, Change... changes) {
        synchronized (info.ledgerMutationLock()) {
            boolean committed = false;
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                Map<Integer, Integer> balances = new LinkedHashMap<>();
                try {
                    for (Change change : changes) {
                        int before = readLockedBalance(connection, info.getId(), change.type());
                        int after = change.absolute()
                                ? WalletBalanceMath.requireValidBalance(change.value())
                                : change.clamp()
                                        ? WalletBalanceMath.clampedBalance(before, change.value())
                                        : WalletBalanceMath.checkedBalance(before, change.value());
                        if (after != before) {
                            after = EconomyLedger.applyBalanceAdjustment(
                                            connection,
                                            new EconomyOperation(
                                                    EconomyOperationId.create("legacy-wallet:" + info.getId()),
                                                    info.getId(),
                                                    null,
                                                    "legacy_wallet",
                                                    "plugin.wallet.mutation",
                                                    change.type(),
                                                    after - before,
                                                    null,
                                                    "legacy-api"))
                                    .balanceAfter();
                        }
                        balances.put(change.type(), after);
                    }
                    connection.commit();
                    committed = true;
                } catch (SQLException | RuntimeException failure) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollback) {
                        failure.addSuppressed(rollback);
                    }
                    throw failure;
                }
                // Publish only after all currency changes committed, under the same ledger lock.
                for (var balance : balances.entrySet()) {
                    if (balance.getKey() == EconomyLedger.CREDITS) info.applyPersistedCredits(balance.getValue());
                    else info.applyPersistedCurrencyAmount(balance.getKey(), balance.getValue());
                }
                return true;
            } catch (SQLException | IllegalArgumentException failure) {
                if (committed) {
                    LOGGER.warn(
                            "Legacy wallet connection cleanup failed after commit for user {}", info.getId(), failure);
                    return true;
                }
                LOGGER.warn("Rejected legacy wallet mutation for user {}", info.getId(), failure);
                return false;
            }
        }
    }

    private static int readLockedBalance(Connection connection, int userId, int type) throws SQLException {
        if (type < EconomyLedger.CREDITS) throw new IllegalArgumentException("Invalid currency type");
        int credits;
        // The user row serializes every currency, including a currency row that does not exist yet.
        try (var statement = connection.prepareStatement("SELECT credits FROM users WHERE id = ? FOR UPDATE")) {
            statement.setInt(1, userId);
            try (var row = statement.executeQuery()) {
                if (!row.next()) throw new SQLException("Wallet owner does not exist");
                credits = row.getInt("credits");
            }
        }
        if (type == EconomyLedger.CREDITS) return credits;
        try (var statement = connection.prepareStatement(
                "SELECT amount FROM users_currency WHERE user_id = ? AND type = ? FOR UPDATE")) {
            statement.setInt(1, userId);
            statement.setInt(2, type);
            try (var row = statement.executeQuery()) {
                return row.next() ? row.getInt("amount") : 0;
            }
        }
    }
}

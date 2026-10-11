package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class LegacyWalletMutationTest {
    static final class Fixture implements AutoCloseable {
        final HabboInfo info = new HabboInfo(42, 100);
        final Connection connection = mock(Connection.class);
        final MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        final MockedStatic<EconomyLedger> ledger = mockStatic(EconomyLedger.class);

        Fixture(int durableCredits, int durablePoints) throws Exception {
            Database database = mock(Database.class);
            HikariDataSource source = mock(HikariDataSource.class);
            when(database.getDataSource()).thenReturn(source);
            when(source.getConnection()).thenReturn(connection);
            emulator.when(Emulator::getDatabase).thenReturn(database);
            PreparedStatement credits = mock(PreparedStatement.class);
            ResultSet creditRows = mock(ResultSet.class);
            when(creditRows.next()).thenReturn(true);
            when(creditRows.getInt("credits")).thenReturn(durableCredits);
            when(credits.executeQuery()).thenReturn(creditRows);
            PreparedStatement points = mock(PreparedStatement.class);
            ResultSet pointRows = mock(ResultSet.class);
            when(pointRows.next()).thenReturn(true);
            when(pointRows.getInt("amount")).thenReturn(durablePoints);
            when(points.executeQuery()).thenReturn(pointRows);
            when(connection.prepareStatement(startsWith("SELECT credits"))).thenReturn(credits);
            when(connection.prepareStatement(startsWith("SELECT amount"))).thenReturn(points);
            ledger.when(() -> EconomyLedger.applyBalanceAdjustment(eq(connection), any(EconomyOperation.class)))
                    .thenAnswer(call -> {
                        EconomyOperation operation = call.getArgument(1);
                        int before = operation.currencyType() == -1 ? durableCredits : durablePoints;
                        return new EconomyMutationResult(before, before + operation.delta(), true);
                    });
        }

        public void close() {
            ledger.close();
            emulator.close();
        }
    }

    @Test
    void closeFailureAfterCommitMustNotReportARejectedDebit() throws Exception {
        try (Fixture f = new Fixture(250, 80)) {
            doThrow(new SQLException("close failed")).when(f.connection).close();
            assertTrue(f.info.tryAddCredits(-20));
            assertEquals(230, f.info.getCredits());
            verify(f.connection).commit();
            verify(f.connection, never()).rollback();
        }
    }

    @Test
    void usesDurableBalanceInsteadOfStaleProfileAndPublishesAfterCommit() throws Exception {
        try (Fixture f = new Fixture(250, 80)) {
            doAnswer(call -> {
                        assertEquals(100, f.info.getCredits());
                        return null;
                    })
                    .when(f.connection)
                    .commit();
            assertTrue(f.info.tryAddCredits(-20));
            assertEquals(230, f.info.getCredits());
            verify(f.connection).commit();
            verify(f.connection, never()).rollback();
        }
    }

    @Test
    void rejectsOverspendingWithoutChangingMemoryOrCommitting() throws Exception {
        try (Fixture f = new Fixture(10, 80)) {
            assertFalse(f.info.tryAddCredits(-11));
            assertEquals(100, f.info.getCredits());
            verify(f.connection).rollback();
            verify(f.connection, never()).commit();
            f.ledger.verifyNoInteractions();
        }
    }

    @Test
    void failedSecondCurrencyRollsBackBothDebitsAndKeepsMemory() throws Exception {
        try (Fixture f = new Fixture(250, 2)) {
            f.info.applyPersistedCurrencyAmount(5, 80);
            assertFalse(f.info.tryDebitCatalogPayment(20, 5, 3));
            assertEquals(100, f.info.getCredits());
            assertEquals(80, f.info.getCurrencyAmount(5));
            verify(f.connection).rollback();
            verify(f.connection, never()).commit();
        }
    }

    @Test
    void failedCommitDoesNotPublishAndLegacyClampStillWorks() throws Exception {
        try (Fixture f = new Fixture(10, 80)) {
            doThrow(new SQLException("commit failed")).when(f.connection).commit();
            assertFalse(f.info.tryAddCredits(1));
            assertEquals(100, f.info.getCredits());
            verify(f.connection).rollback();
        }
        try (Fixture f = new Fixture(10, 80)) {
            f.info.addCredits(-50);
            assertEquals(0, f.info.getCredits());
            verify(f.connection).commit();
        }
    }
}

package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemporaryRankStoreTest {
    @Test
    void durationIsBoundedAndExplicit() {
        assertEquals(1_800_000L, TemporaryRankStore.durationMillis("30m"));
        assertEquals(7_200_000L, TemporaryRankStore.durationMillis("2h"));
        assertEquals(604_800_000L, TemporaryRankStore.durationMillis("7d"));
        for (String invalid : new String[] {"0m", "-1h", "31d", "999999h", "1", "1s", "1.5h", "1h OR 1=1"})
            assertThrows(IllegalArgumentException.class, () -> TemporaryRankStore.durationMillis(invalid));
    }

    @Test
    void extensionKeepsOriginalPermanentRank() throws Exception {
        Fixture fixture = new Fixture(3, 1, 3, 10_000);
        var state = TemporaryRankStore.grant(fixture.connection, 42, 4, 60_000, 99, "reason", 5_000, rank -> rank < 5);
        assertEquals(new TemporaryRankStore.State(4, 1, 65_000), state);
        verify(fixture.statement("INSERT INTO")).setInt(2, 1);
        verify(fixture.connection).commit();
    }

    @Test
    void expiryRestoresOnlyTheStillAssignedRank() throws Exception {
        Fixture fixture = new Fixture(3, 1, 3, 5_000);
        assertEquals(
                new TemporaryRankStore.State(1, null, 0), TemporaryRankStore.refresh(fixture.connection, 42, 5_000));
        verify(fixture.statement("UPDATE users")).setInt(1, 1);
        verify(fixture.statement("DELETE FROM")).executeUpdate();
    }

    @Test
    void aDeletedOriginalRankIsAlsoReplacedInTheDatabase() throws Exception {
        Fixture fixture = new Fixture(4, 3, 4, 5_000);
        assertEquals(
                new TemporaryRankStore.State(1, null, 0),
                TemporaryRankStore.refresh(fixture.connection, 42, 5_000, rank -> rank == 3 ? 1 : rank));
        verify(fixture.statement("UPDATE users")).setInt(1, 1);
    }

    @Test
    void externalRankChangeWinsOverOldExpiry() throws Exception {
        Fixture fixture = new Fixture(4, 1, 3, 5_000);
        assertEquals(
                new TemporaryRankStore.State(4, null, 0), TemporaryRankStore.refresh(fixture.connection, 42, 6_000));
        assertNull(fixture.statement("UPDATE users"));
        verify(fixture.statement("DELETE FROM")).executeUpdate();
    }

    @Test
    void hierarchyChecksTheHiddenReturnRankAndRollsBack() throws Exception {
        Fixture fixture = new Fixture(2, 5, 2, 10_000);
        assertThrows(
                SecurityException.class,
                () -> TemporaryRankStore.grant(fixture.connection, 42, 3, 60_000, 99, "", 5_000, rank -> rank < 4));
        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
        assertNull(fixture.statement("INSERT INTO"));
    }

    @Test
    void permanentChangeCancelsEvenASameRankLeaseAtomically() throws Exception {
        Fixture fixture = new Fixture(3, 1, 3, 10_000);
        TemporaryRankStore.permanent(fixture.connection, 42, 3);
        verify(fixture.statement("UPDATE users")).setInt(1, 3);
        verify(fixture.statement("DELETE FROM")).executeUpdate();
        verify(fixture.connection).commit();
    }

    @Test
    void failedGrantRollsBackBothRankAndLease() throws Exception {
        Fixture fixture = new Fixture(1, null, 0, 0);
        when(fixture.connection.prepareStatement(org.mockito.ArgumentMatchers.startsWith("UPDATE users")))
                .thenThrow(new SQLException("unavailable"));
        assertThrows(
                SQLException.class,
                () -> TemporaryRankStore.grant(fixture.connection, 42, 3, 60_000, 99, "", 5_000, rank -> true));
        verify(fixture.connection).rollback();
        verify(fixture.connection, never()).commit();
        verify(fixture.connection).setAutoCommit(true);
    }

    private static final class Fixture {
        final Connection connection = mock(Connection.class);
        final Map<String, PreparedStatement> statements = new HashMap<>();

        Fixture(int current, Integer previous, int assigned, long expires) throws Exception {
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0);
                PreparedStatement statement = mock(PreparedStatement.class);
                statements.put(sql, statement);
                if (sql.startsWith("SELECT")) {
                    ResultSet rows = mock(ResultSet.class);
                    boolean lease = sql.contains("user_temporary_ranks");
                    when(rows.next()).thenReturn(!lease || previous != null, false);
                    when(rows.getInt(1)).thenReturn(lease ? (previous == null ? 0 : previous) : current);
                    when(rows.getInt(2)).thenReturn(assigned);
                    when(rows.getLong(3)).thenReturn(expires);
                    when(statement.executeQuery()).thenReturn(rows);
                }
                when(statement.executeUpdate()).thenReturn(1);
                return statement;
            });
        }

        PreparedStatement statement(String prefix) {
            return statements.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
    }
}

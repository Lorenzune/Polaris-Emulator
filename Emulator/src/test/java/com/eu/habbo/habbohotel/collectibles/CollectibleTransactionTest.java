package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CollectibleTransactionTest {
    @Test
    void categoryClaimLocksAndChecksOwnershipBeforeGrantingTheReward() throws Exception {
        var manager = new CollectibleManager();
        var user = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(user.getHabboInfo().getId()).thenReturn(7);
        var database = mock(Database.class, RETURNS_DEEP_STUBS);
        var c = mock(Connection.class);
        when(database.getDataSource().getConnection()).thenReturn(c);
        var reward = new CollectibleReward("P", "C", "currency", 0, -1, 1, 0, 200, List.of());
        when(c.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            var s = mock(PreparedStatement.class);
            var rs = mock(ResultSet.class);
            when(s.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            if (sql.startsWith("SELECT UNIX_TIMESTAMP")) when(rs.getLong(1)).thenReturn(100L);
            if (sql.startsWith("SELECT r.configuration")) {
                when(rs.getString(1)).thenReturn(new Gson().toJson(reward));
                when(rs.getBoolean(2)).thenReturn(true);
            }
            if (sql.startsWith("SELECT title,item_ids")) {
                when(rs.getString(1)).thenReturn("C");
                when(rs.getString(2)).thenReturn("[42,43]");
            }
            if (sql.startsWith("SELECT item_id")) when(rs.getInt(1)).thenReturn(42); // Item 43 is missing.
            return s;
        });
        var method = CollectibleManager.class.getDeclaredMethod("claimLocked", Habbo.class, List.class);
        method.setAccessible(true);
        try (var emulator = mockStatic(Emulator.class);
                var ledger = mockStatic(EconomyLedger.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            var error = assertThrows(InvocationTargetException.class, () -> method.invoke(manager, user, List.of(1)));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
            verify(c)
                    .prepareStatement(
                            "SELECT item_id FROM items WHERE user_id=? AND first_owner_id=? AND ownership_transferred=0 FOR UPDATE");
            verify(c).rollback();
            verify(c, never()).commit();
            ledger.verifyNoInteractions();
            verify(c, never()).prepareStatement(startsWith("INSERT INTO collectible_claims"));
        }
    }

    @Test
    void claimRejectsARewardForADifferentLevelBeforeDelivery() throws Exception {
        var manager = new CollectibleManager();
        var user = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(user.getHabboInfo().getId()).thenReturn(7);
        var database = mock(Database.class, RETURNS_DEEP_STUBS);
        var c = mock(Connection.class);
        when(database.getDataSource().getConnection()).thenReturn(c);
        var reward = new CollectibleReward("P", "C", "currency", 0, -1, 1, 0, 200, List.of(), 2);
        when(c.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            var s = mock(PreparedStatement.class);
            var rs = mock(ResultSet.class);
            when(s.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            if (sql.startsWith("SELECT xp")) when(rs.getLong(1)).thenReturn(100L); // Level 1.
            if (sql.startsWith("SELECT UNIX_TIMESTAMP")) when(rs.getLong(1)).thenReturn(100L);
            if (sql.startsWith("SELECT r.configuration")) {
                when(rs.getString(1)).thenReturn(new Gson().toJson(reward));
                when(rs.getBoolean(2)).thenReturn(true);
            }
            return s;
        });
        var method = CollectibleManager.class.getDeclaredMethod("claimLocked", Habbo.class, List.class);
        method.setAccessible(true);
        try (var emulator = mockStatic(Emulator.class);
                var ledger = mockStatic(EconomyLedger.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            var error = assertThrows(InvocationTargetException.class, () -> method.invoke(manager, user, List.of(1)));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
            verify(c).rollback();
            verify(c, never()).commit();
            ledger.verifyNoInteractions();
            verify(c, never()).prepareStatement(startsWith("INSERT INTO collectible_claims"));
        }
    }

    @ParameterizedTest
    @CsvSource({"item,false", "currency,false", "item,true", "currency,true"})
    void deliveryClaimAndXpCommitOrRollbackTogether(String kind, boolean fail) throws Exception {
        var manager = new CollectibleManager();
        var user = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(user.getHabboInfo().getId()).thenReturn(7);
        var database = mock(Database.class, RETURNS_DEEP_STUBS);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var base = mock(Item.class);
        when(base.getId()).thenReturn(42);
        when(environment.getItemManager().getItem(42)).thenReturn(base);
        var c = mock(Connection.class);
        when(database.getDataSource().getConnection()).thenReturn(c);
        var profile = mock(PreparedStatement.class);
        var claim = mock(PreparedStatement.class);
        if (fail) when(profile.executeUpdate()).thenThrow(new SQLException("Simulated XP persistence failure"));
        var reward = new CollectibleReward("P", "C", kind, 42, -1, 1, 100, 200, List.of());
        when(c.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.startsWith("INSERT INTO collectible_profiles")) return profile;
            if (sql.startsWith("INSERT INTO collectible_claims")) return claim;
            var s = mock(PreparedStatement.class);
            var rs = mock(ResultSet.class);
            when(s.executeQuery()).thenReturn(rs);
            when(rs.next())
                    .thenReturn(
                            sql.startsWith("SELECT UNIX_TIMESTAMP")
                                    || sql.startsWith("SELECT id FROM users")
                                    || sql.startsWith("SELECT r.configuration"),
                            false);
            if (sql.startsWith("SELECT UNIX_TIMESTAMP")) when(rs.getLong(1)).thenReturn(100L);
            if (sql.startsWith("SELECT r.configuration")) {
                when(rs.getString(1)).thenReturn(new Gson().toJson(reward));
                when(rs.getBoolean(2)).thenReturn(true);
            }
            return s;
        });
        var item = mock(PreparedStatement.class);
        var keys = mock(ResultSet.class);
        when(c.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(item);
        when(item.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getInt(1)).thenReturn(90);
        var method = CollectibleManager.class.getDeclaredMethod("claimLocked", Habbo.class, List.class);
        method.setAccessible(true);
        try (var emulator = mockStatic(Emulator.class);
                var ledger = mockStatic(EconomyLedger.class);
                var wallet = mockStatic(com.eu.habbo.habbohotel.users.LedgerWalletMutation.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            ledger.when(() -> EconomyLedger.apply(eq(c), any(EconomyOperation.class)))
                    .thenReturn(new EconomyMutationResult(0, 1, true));
            if (fail) {
                var error =
                        assertThrows(InvocationTargetException.class, () -> method.invoke(manager, user, List.of(1)));
                assertInstanceOf(SQLException.class, error.getCause());
                verify(c).rollback();
                verify(c, never()).commit();
                verify(user.getInventory().getItemsComponent(), never()).addItem(any());
            } else {
                method.invoke(manager, user, List.of(1));
                verify(c).commit();
                verify(c, never()).rollback();
            }
            verify(claim).executeUpdate();
            verify(profile).setLong(2, 100L);
            if (kind.equals("currency")) ledger.verify(() -> EconomyLedger.apply(eq(c), any(EconomyOperation.class)));
            else verify(item).executeUpdate();
        }
    }

    @Test
    void ordinaryUsersCannotCreateOrDisableRewards() {
        var manager = new CollectibleManager();
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertThrows(SecurityException.class, () -> manager.create(7, new com.google.gson.JsonObject()));
            assertThrows(SecurityException.class, () -> manager.disable(7, 1));
            emulator.verify(Emulator::getDatabase, never());
        }
    }
}

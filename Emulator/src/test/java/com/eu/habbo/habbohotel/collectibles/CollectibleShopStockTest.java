package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CollectibleShopStockTest {
    @ParameterizedTest
    @CsvSource({
        "24,false,0,furni,false",
        "25,false,0,furni,false",
        "24,true,0,furni,false",
        "24,false,20,furni,false",
        "24,false,0,badge,false",
        "24,true,0,badge,false",
        "24,false,0,badge,true"
    })
    void lastUnitAndPaymentCommitTogetherAndSoldOutCannotCharge(
            int sold, boolean paymentFails, long availableAt, String category, boolean ownedBadge) throws Exception {
        var manager = spy(new CollectibleManager());
        doReturn(new JsonObject()).when(manager).state(7);
        var user = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(user.getHabboInfo().getId()).thenReturn(7);
        var database = mock(Database.class, RETURNS_DEEP_STUBS);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getItemManager().getItem(42)).thenReturn(mock(Item.class));
        var c = mock(Connection.class);
        when(database.getDataSource().getConnection()).thenReturn(c);
        var stock = mock(PreparedStatement.class);
        when(stock.executeUpdate()).thenReturn(1);
        when(c.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.startsWith("UPDATE collectible_shop")) return stock;
            var statement = mock(PreparedStatement.class);
            var rs = mock(ResultSet.class);
            when(statement.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            if (sql.startsWith("SELECT available_at")) when(rs.getLong(1)).thenReturn(availableAt);
            if (sql.startsWith("SELECT item_id,price")) {
                when(rs.getInt(1)).thenReturn(42);
                when(rs.getInt(2)).thenReturn(100);
                when(rs.getInt(3)).thenReturn(105);
                when(rs.getInt(4)).thenReturn(25);
                when(rs.getInt(5)).thenReturn(sold);
                when(rs.getString(6)).thenReturn(category);
                when(rs.getString(7)).thenReturn("DUCKET01");
            }
            if (sql.startsWith("SELECT id FROM users_badges")) when(rs.next()).thenReturn(ownedBadge, false);
            return statement;
        });
        var item = mock(PreparedStatement.class);
        var keys = mock(ResultSet.class);
        when(c.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(item);
        when(item.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getInt(1)).thenReturn(90);
        var method = CollectibleManager.class.getDeclaredMethod("buyShopLocked", Habbo.class, int.class);
        method.setAccessible(true);
        try (var emulator = mockStatic(Emulator.class);
                var ledger = mockStatic(EconomyLedger.class);
                var wallet = mockStatic(LedgerWalletMutation.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            if (paymentFails)
                ledger.when(() -> EconomyLedger.apply(eq(c), any()))
                        .thenThrow(new IllegalArgumentException("Insufficient balance"));
            else
                ledger.when(() -> EconomyLedger.apply(eq(c), any()))
                        .thenReturn(new EconomyMutationResult(100, 0, true));
            if (sold == 25 || paymentFails || availableAt > 0 || ownedBadge) {
                var error = assertThrows(InvocationTargetException.class, () -> method.invoke(manager, user, 1));
                assertInstanceOf(IllegalArgumentException.class, error.getCause());
                verify(c).rollback();
                verify(c, never()).commit();
                verify(item, never()).executeUpdate();
                if (sold == 25 || availableAt > 0 || ownedBadge) {
                    ledger.verifyNoInteractions();
                    verify(stock, never()).executeUpdate();
                }
            } else {
                method.invoke(manager, user, 1);
                verify(stock).executeUpdate();
                if ("badge".equals(category)) {
                    verify(item, never()).executeUpdate();
                    verify(c).prepareStatement("INSERT INTO users_badges(user_id,badge_code,slot_id) VALUES(?,?,0)");
                } else verify(item, times(1)).executeUpdate();
                verify(c).commit();
                verify(c, never()).rollback();
            }
            if (availableAt == 0)
                verify(c)
                        .prepareStatement(
                                contains(
                                        "quantity,sold,category,badge_code FROM collectible_shop WHERE id=? AND enabled=1 AND deleted=0 FOR UPDATE"));
        }
    }
}

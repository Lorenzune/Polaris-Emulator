package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class RoomItemBatchLoadTest {
    @Test
    void loadsItemsAndOwnersWithThreeQueriesOnTheSuppliedConnection() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(17);
        when(room.getRoomSpecialTypes()).thenReturn(mock(RoomSpecialTypes.class));
        RoomItemManager manager = new RoomItemManager(room);
        Connection connection = mock(Connection.class);
        ResultSet builders = mock(ResultSet.class), owners = mock(ResultSet.class), items = mock(ResultSet.class);
        when(owners.next()).thenReturn(true, false);
        when(owners.getInt(1)).thenReturn(7);
        when(owners.getString(2)).thenReturn("Alice");
        when(items.next()).thenReturn(true, false);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(statement.executeQuery())
                    .thenReturn(
                            sql.startsWith("SELECT builders")
                                    ? builders
                                    : sql.startsWith("SELECT DISTINCT") ? owners : items);
            return statement;
        });
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(20);
        when(item.getUserId()).thenReturn(7);
        ItemManager loader = mock(ItemManager.class);
        when(loader.loadHabboItem(items)).thenReturn(item);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getItemManager()).thenReturn(loader);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            manager.loadItems(connection);
            assertEquals(1, manager.itemCount());
            assertSame(item, manager.getHabboItem(20));
            assertEquals("Alice", manager.getFurniOwnerName(7));
            assertEquals(1, manager.getUserFurniCount(7));
            verify(connection, times(3)).prepareStatement(anyString());
            emulator.verify(Emulator::getDatabase, times(0));
        }
    }
}

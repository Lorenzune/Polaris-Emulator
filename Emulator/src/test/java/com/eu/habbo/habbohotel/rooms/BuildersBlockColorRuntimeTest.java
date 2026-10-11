package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildersBlockColorRuntimeTest {
    @Test
    void modernVariableWritesAreBoundedAndDeferSqlOffTheWiredThread() throws Exception {
        Room room = mock(Room.class);
        HabboItem item = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(room.getId()).thenReturn(75);
        when(item.getId()).thenReturn(7501);
        when(item.getRoomId()).thenReturn(75);
        when(item.getBaseItem()).thenReturn(base);
        when(base.getName()).thenReturn("bc_block_0_recolorable");
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getInteractionType())
                .thenReturn(new com.eu.habbo.habbohotel.items.ItemInteraction(
                        "default", com.eu.habbo.habbohotel.items.interactions.InteractionDefault.class));
        when(room.getHabboItem(7501)).thenReturn(item);
        when(room.getHabbos()).thenReturn(java.util.Collections.emptyList());
        Connection connection = mock(Connection.class);
        java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch scheduled = new java.util.concurrent.CountDownLatch(1);
        RoomWiredRuntime runtime = new RoomWiredRuntime(room, () -> connection, task -> {
            queued.incrementAndGet();
            scheduled.countDown();
        });
        org.junit.jupiter.api.Assertions.assertFalse(runtime.writeBuildersBlockColor(item, BuildersClubPalette.RGB, 0));
        when(item.isBuildersClubItem()).thenReturn(true);
        assertTrue(runtime.writeBuildersBlockColor(item, BuildersClubPalette.RGB, 0x2196f3));
        assertTrue(runtime.writeBuildersBlockColor(item, BuildersClubPalette.RED, 0));
        assertEquals(0x0096f3, runtime.buildersBlockColor(item));
        org.junit.jupiter.api.Assertions.assertFalse(
                runtime.writeBuildersBlockColor(item, BuildersClubPalette.RED, 256));
        for (int i = 0; i < 98; i++)
            assertTrue(runtime.writeBuildersBlockColor(item, BuildersClubPalette.RGB, 0x0096f3));
        org.junit.jupiter.api.Assertions.assertFalse(
                runtime.writeBuildersBlockColor(item, BuildersClubPalette.RGB, 0xff00aa));
        assertEquals(0x0096f3, runtime.buildersBlockColor(item));
        assertTrue(scheduled.await(2, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(1, queued.get());
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void failedOrUncertainBatchRestoresTheEntirePreviousRoomColor() throws Exception {
        for (int count : new int[] {0, java.sql.Statement.SUCCESS_NO_INFO}) {
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(72);
            HabboItem item = mock(HabboItem.class);
            Item base = mock(Item.class);
            when(base.getType()).thenReturn(FurnitureType.FLOOR);
            when(item.getBaseItem()).thenReturn(base);
            when(item.getId()).thenReturn(7201);
            when(item.getRoomId()).thenReturn(72);
            when(room.getHabboItem(7201)).thenReturn(item);
            Connection connection = mock(Connection.class);
            PreparedStatement update = mock(PreparedStatement.class);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(anyString())).thenReturn(update);
            when(update.executeBatch()).thenReturn(new int[] {count});
            RoomWiredRuntime runtime = new RoomWiredRuntime(room, () -> connection, Runnable::run);
            assertThrows(
                    SQLException.class,
                    () -> runtime.setBuildersBlockColor(
                            List.of(item), BuildersClubRecolorService.colorConfiguration(0xff00aa)));
            assertTrue(runtime.customColorSnapshot().isEmpty());
            verify(connection).rollback();
            verify(connection, never()).commit();
            verify(item, never()).setBaseItem(base);
        }
    }

    @Test
    void successfulColorCommitsBeforePublicationAndKeepsTheBaseItem() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(73);
        HabboItem item = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getId()).thenReturn(7301);
        when(item.getRoomId()).thenReturn(73);
        when(room.getHabboItem(7301)).thenReturn(item);
        when(room.getHabbos()).thenReturn(java.util.Collections.emptyList());
        Connection connection = mock(Connection.class);
        PreparedStatement update = mock(PreparedStatement.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(update);
        when(update.executeBatch()).thenReturn(new int[] {1});
        RoomWiredRuntime runtime = new RoomWiredRuntime(room, () -> connection, Runnable::run);
        String color = BuildersClubRecolorService.colorConfiguration(0x001122);
        assertTrue(runtime.setBuildersBlockColor(List.of(item), color));
        assertEquals(color, runtime.customColorSnapshot().getFirst().customColor());
        verify(connection).commit();
        verify(update).setInt(5, 73);
        verify(item, never()).setBaseItem(base);
    }
}

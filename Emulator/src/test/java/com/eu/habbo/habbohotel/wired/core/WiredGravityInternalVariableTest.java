package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

class WiredGravityInternalVariableTest {

    @Test
    void gravityIsAvailableForTypedReferenceAndDestinationCapabilities() {
        assertTrue(WiredInternalVariableSupport.canUseFurniReference("@gravity"));
        assertTrue(WiredInternalVariableSupport.canUseFurniDestination("@gravity"));
    }

    @Test
    void readAndWriteDelegateToRoomOwnedSessionState() {
        Room room = mock(Room.class);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        HabboItem item = floorItem();
        when(runtime.setGravityEnabled(item, true)).thenReturn(true);
        when(runtime.isGravityEnabled(item)).thenReturn(true);

        assertTrue(WiredInternalVariableSupport.hasFurniValue(item, "@gravity"));
        assertTrue(WiredInternalVariableSupport.writeFurniValue(room, item, "@gravity", 1));
        assertEquals(1, WiredInternalVariableSupport.readFurniValue(room, item, "@gravity"));
    }

    @Test
    void wallFurnitureFailsClosed() {
        Room room = mock(Room.class);
        HabboItem wall = mock(HabboItem.class);
        Item wallBase = mock(Item.class);
        when(wall.getBaseItem()).thenReturn(wallBase);
        when(wallBase.getType()).thenReturn(FurnitureType.WALL);

        assertFalse(WiredInternalVariableSupport.hasFurniValue(wall, "@gravity"));
        assertFalse(WiredInternalVariableSupport.writeFurniValue(room, wall, "@gravity", 1));
    }

    @Test
    void furniReferenceReportsPhysicalHeightAndFloorOrWallType() {
        Room room = mock(Room.class);
        HabboItem floor = floorItem();
        when(floor.getBaseItem().getHeight()).thenReturn(0.75D);
        HabboItem wall = mock(HabboItem.class);
        Item wallBase = mock(Item.class);
        when(wall.getBaseItem()).thenReturn(wallBase);
        when(wallBase.getType()).thenReturn(FurnitureType.WALL);

        assertEquals(75, WiredInternalVariableSupport.readFurniValue(room, floor, "@height"));
        assertEquals(1, WiredInternalVariableSupport.readFurniValue(room, floor, "@type"));
        assertEquals(2, WiredInternalVariableSupport.readFurniValue(room, wall, "@type"));
    }

    private static HabboItem floorItem() {
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(FurnitureType.FLOOR);
        return item;
    }
}

package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WiredCalledStackContextTest {
    @Test
    void callingStackPreservesClickedFurnitureSignalPayloadAndContextVariables() throws Exception {
        var engineField = WiredManager.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        var indexField = WiredManager.class.getDeclaredField("stackIndex");
        indexField.setAccessible(true);
        Object previousEngine = engineField.get(null), previousIndex = indexField.get(null);
        var engine = mock(WiredEngine.class);
        try {
            engineField.set(null, engine);
            indexField.set(null, mock(RoomWiredStackIndex.class));
            var room = mock(Room.class);
            var unit = mock(RoomUnit.class);
            var clicked = mock(HabboItem.class);
            var forwarded = mock(HabboItem.class);
            var anchor = mock(InteractionWiredEffect.class);
            var tile = new RoomTile((short) 7, (short) 1, (short) 0, RoomTileState.OPEN, true);
            when(room.getItemsAt(tile)).thenReturn(Set.of(anchor));
            var event = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                    .actor(unit)
                    .sourceItem(clicked)
                    .signalSourceItems(List.of(forwarded))
                    .signalPayloadExplicit(true)
                    .signalFurniCount(1)
                    .signalChannel(88)
                    .build();
            var parent = new WiredContext(event, null, mock(WiredServices.class), new WiredState(100));
            parent.contextVariables().assignValue(9, 42, true);
            assertTrue(WiredManager.executeEffectsAtTiles(List.of(tile), unit, room, 2, parent));
            var captured = ArgumentCaptor.forClass(WiredEvent.class);
            verify(engine).executeCalledEffects(eq(anchor), anyList(), captured.capture());
            var child = new WiredContext(captured.getValue(), anchor, mock(WiredServices.class), new WiredState(100));
            assertEquals(List.of(clicked), WiredSourceUtil.resolveItemsRaw(child, 0, List.of()));
            assertEquals(List.of(forwarded), WiredSourceUtil.resolveItemsRaw(child, 201, List.of()));
            assertEquals(42, child.contextVariables().getValue(9));
            assertEquals(2, child.event().getCallStackDepth());
            child.contextVariables().assignValue(9, 99, true);
            assertEquals(42, parent.contextVariables().getValue(9));
        } finally {
            engineField.set(null, previousEngine);
            indexField.set(null, previousIndex);
        }
    }
}

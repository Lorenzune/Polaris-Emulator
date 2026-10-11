package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.rooms.WiredOpacityState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableMonitor;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredEffectSetVisibilityTest {
    @Test
    void targetKindsAreExclusiveAndConfigurationSurvivesReload() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(54);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        when(runtime.getVariableMonitor()).thenReturn(mock(WiredInternalVariableMonitor.class));
        RoomUnit actor = new RoomUnit();
        actor.setRoom(room);
        when(room.getRoomUnits()).thenReturn(Set.of(actor));
        HabboItem target = mock(HabboItem.class);
        when(target.getId()).thenReturn(5201);
        when(target.getBaseItem()).thenReturn(mock(Item.class));
        when(room.getHabboItem(5201)).thenReturn(target);
        WiredEffectSetVisibility effect = new WiredEffectSetVisibility(1, 2, mock(Item.class), "", 0, 0);
        ResultSet saved = mock(ResultSet.class);
        when(saved.getString("wired_data"))
                .thenReturn("{\"opacity\":30,\"furniSource\":100,\"userSource\":0,\"delay\":2,\"itemIds\":[5201]}");
        when(runtime.globalOpacity(target)).thenReturn(100);
        when(runtime.setGlobalOpacityForItems(room, List.of(target), 30))
                .thenReturn(List.of(new WiredOpacityState(5201, false, 30, false)));
        effect.loadWiredData(saved, room);
        assertEquals(2, effect.getDelay());
        effect.execute(new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(actor).build(),
                effect,
                mock(WiredServices.class),
                new WiredState(20)));
        verify(runtime).setGlobalOpacityForItems(room, List.of(target), 30);
        verify(runtime.getVariableMonitor()).changed(room, 1, null, target, "@opacity", 100, 30);
        assertEquals(100, actor.getWiredOpacity());
        clearInvocations(runtime);
        when(saved.getString("wired_data"))
                .thenReturn("{\"opacity\":40,\"target\":0,\"furniSource\":100,\"userSource\":0,\"itemIds\":[5201]}");
        effect.loadWiredData(saved, room);
        effect.execute(new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(actor).build(),
                effect,
                mock(WiredServices.class),
                new WiredState(20)));
        assertEquals(40, actor.getWiredOpacity());
        assertTrue(effect.getWiredData().contains("\"opacity\":40"));
        effect.onPickUp();
        assertEquals(0, effect.getDelay());
        assertTrue(effect.getWiredData().contains("\"opacity\":100"));
    }

    @Test
    void userOpacityIsRegisteredValidatedAndRoomScoped() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        when(room.getRoomUnits()).thenReturn(Set.of(unit));
        assertTrue(WiredInternalVariableSupport.canUseUserReference("@opacity"));
        assertTrue(WiredInternalVariableSupport.canUseUserDestination("@opacity"));
        assertEquals(100, WiredInternalVariableSupport.readUserValue(room, unit, "@opacity"));
        assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@opacity", 0));
        assertEquals(0, unit.getWiredOpacity());
        assertFalse(WiredInternalVariableSupport.writeUserValue(room, unit, "@opacity", 101));
        assertFalse(WiredInternalVariableSupport.writeUserValue(room, new RoomUnit(), "@opacity", 50));
        unit.setRoom(null);
        assertEquals(100, unit.getWiredOpacity());
    }
}

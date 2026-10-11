package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.rooms.WiredHighlightState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredEffectSetHighlightTest {
    @Test
    void reloadsConfigurationAndAppliesOnlyTheChosenTarget() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(54);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        HabboItem floor = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(floor.getId()).thenReturn(12);
        when(floor.getBaseItem()).thenReturn(base);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(room.getHabboItem(12)).thenReturn(floor);
        RoomUnit actor = new RoomUnit();
        actor.setId(0);
        when(room.getRoomUnits()).thenReturn(Set.of(actor));
        WiredEffectSetHighlight effect = new WiredEffectSetHighlight(10, 7, mock(Item.class), "", 0, 0);
        ResultSet saved = mock(ResultSet.class);
        when(saved.getString("wired_data"))
                .thenReturn(
                        "{\"style\":4,\"color\":16733440,\"target\":1,\"furniSource\":100,\"userSource\":0,\"itemIds\":[12]}");
        effect.loadWiredData(saved, room);
        WiredContext context = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(actor).build(),
                effect,
                mock(WiredServices.class),
                new WiredState(20));
        effect.execute(context);
        verify(runtime).applyHighlights(List.of(new WiredHighlightState(10, 12, 4, 16733440)));
        assertFalse(effect.requiresTriggeringUser());
        assertTrue(effect.getWiredData().contains("\"style\":4"));
        when(saved.getString("wired_data")).thenReturn("{\"style\":0,\"color\":0,\"target\":0,\"userSource\":0}");
        effect.loadWiredData(saved, room);
        effect.execute(context);
        verify(runtime).applyHighlights(List.of(new WiredHighlightState(100, 0, 0, 0)));
        assertTrue(effect.requiresTriggeringUser());
    }
}

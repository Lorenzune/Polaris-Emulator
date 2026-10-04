package com.eu.habbo.habbohotel.items.interactions.wired.effects;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.outgoing.wired.WiredClickSettingsComposer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WiredActionClickSettingsTest {
    @Test void sendsConfigurationToTargetAndRejectsMissingActor() {
        var action = new WiredActionClickSettings(1,2,mock(Item.class),"",0,0);
        action.saveData(new WiredSettings(new int[]{2,1},"",new int[0],0),null);
        var room = mock(Room.class); var unit = mock(RoomUnit.class); var habbo = mock(Habbo.class); var client = mock(GameClient.class);
        when(room.getId()).thenReturn(44); when(room.getHabbo(unit)).thenReturn(habbo); when(habbo.getClient()).thenReturn(client);
        assertTrue(action.execute(unit,room,new Object[0]));
        verify(client).sendResponse(any(WiredClickSettingsComposer.class));
        assertFalse(action.execute(null,room,new Object[0]));
    }
    @Test void deliversToSelectorUsersInsteadOfActor() {
        var action = new WiredActionClickSettings(1,2,mock(Item.class),"",0,0);
        action.saveData(new WiredSettings(new int[]{2,1,200},"",new int[0],0),null);
        var room = mock(Room.class); var actor = mock(RoomUnit.class); var selected = mock(RoomUnit.class);
        var habbo = mock(Habbo.class); var client = mock(GameClient.class);
        when(room.getHabbo(selected)).thenReturn(habbo); when(habbo.getClient()).thenReturn(client);
        var ctx = new com.eu.habbo.habbohotel.wired.core.WiredContext(
            com.eu.habbo.habbohotel.wired.core.WiredEvent.builder(com.eu.habbo.habbohotel.wired.core.WiredEvent.Type.CUSTOM, room).actor(actor).build(),
            null, mock(com.eu.habbo.habbohotel.wired.core.WiredServices.class), new com.eu.habbo.habbohotel.wired.core.WiredState(20));
        ctx.targets().setUsers(java.util.List.of(selected));
        action.execute(ctx);
        verify(client).sendResponse(any(WiredClickSettingsComposer.class));
        verify(room, never()).getHabbo(actor);
    }
    @Test void sourcePersistsAndSelectorRunsWithoutTriggeringUser() throws Exception {
        var action = new WiredActionClickSettings(1,2,mock(Item.class),"",0,0);
        action.saveData(new WiredSettings(new int[]{2,1,200},"",new int[0],0),null);
        assertFalse(action.requiresTriggeringUser());
        var set = mock(java.sql.ResultSet.class);
        when(set.getString("wired_data")).thenReturn(action.getWiredData());
        var restored = new WiredActionClickSettings(1,2,mock(Item.class),"",0,0);
        restored.loadWiredData(set,null);
        assertEquals(action.getWiredData(), restored.getWiredData());
        assertFalse(restored.requiresTriggeringUser());
        restored.saveData(new WiredSettings(new int[]{2,1},"",new int[0],0),null);
        assertTrue(restored.requiresTriggeringUser());
    }
    @Test void persistsBoundedOptionsAndReadsMalformedDataSafely() throws Exception {
        var action = new WiredActionClickSettings(1,2,mock(Item.class),"",0,0);
        action.saveData(new WiredSettings(new int[]{99,-1},"",new int[0],0),null);
        assertEquals(2,action.getUserClickSetting()); assertEquals(0,action.getFurniClickSetting());
        var set = mock(java.sql.ResultSet.class); when(set.getString("wired_data")).thenReturn(action.getWiredData());
        var other = new WiredActionClickSettings(1,2,mock(Item.class),"",0,0);
        other.loadWiredData(set,null); assertEquals(action.getWiredData(),other.getWiredData());
        when(set.getString("wired_data")).thenReturn("{broken");
        assertDoesNotThrow(() -> other.loadWiredData(set,null)); assertEquals(0,other.getUserClickSetting());
    }
}

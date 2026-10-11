package com.eu.habbo.messages.incoming.rooms.items;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.plugin.PluginManager;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class ClickFurniActivityTest {
    @Test
    void validClicksWakeTheUserButMissingFurnitureDoesNot() throws Exception {
        var client = mock(GameClient.class, RETURNS_DEEP_STUBS);
        var room = mock(Room.class);
        when(client.getHabbo().getHabboInfo().getCurrentRoom()).thenReturn(room);
        var item = mock(HabboItem.class);
        when(room.getHabboItem(7)).thenReturn(item);
        var plugins = mock(PluginManager.class);
        when(plugins.fireEvent(any())).thenAnswer(i -> i.getArgument(0));
        try (var emulator = mockStatic(Emulator.class);
                var wired = mockStatic(WiredManager.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            var event = new ClickFurniEvent();
            event.client = client;
            event.packet = new ClientMessage(0, Unpooled.buffer().writeInt(8).writeInt(0));
            event.handle();
            verify(room, never()).unIdle(any());
            event.packet = new ClientMessage(0, Unpooled.buffer().writeInt(7).writeInt(0));
            event.handle();
            verify(room, times(1)).unIdle(client.getHabbo());
            wired.verify(() ->
                    WiredManager.queueUserClicksFurni(room, client.getHabbo().getRoomUnit(), item));
        }
    }
}

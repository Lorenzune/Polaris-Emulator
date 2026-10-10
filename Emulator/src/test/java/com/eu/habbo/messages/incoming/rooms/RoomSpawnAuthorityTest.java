package com.eu.habbo.messages.incoming.rooms;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class RoomSpawnAuthorityTest {
    @Test
    void clientCoordinatesCannotSelectSpawnOrStartBackgroundLoading() throws Exception {
        run(false);
    }

    @Test
    void reconnectPreservesTheServerPositionDespiteClientCoordinates() throws Exception {
        run(true);
    }

    private void run(boolean reconnect) throws Exception {
        RequestRoomLoadEvent event = new RequestRoomLoadEvent();
        event.client = mock(GameClient.class);
        event.packet = mock(ClientMessage.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(event.client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(mock(HabboStats.class));
        when(habbo.getRoomUnit()).thenReturn(unit);
        when(event.packet.readInt()).thenReturn(17, 99, 99);
        when(event.packet.readString()).thenReturn("password");
        var buffer = Unpooled.buffer().writeInt(99).writeInt(99);
        when(event.packet.getBuffer()).thenReturn(buffer);
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        RoomTile serverPosition = new RoomTile((short) 4, (short) 5, (short) 0, RoomTileState.OPEN, true);
        if (reconnect) {
            Room room = mock(Room.class);
            RoomLayout layout = mock(RoomLayout.class);
            when(info.getCurrentRoom()).thenReturn(room);
            when(room.getId()).thenReturn(17);
            when(unit.getCurrentLocation()).thenReturn(serverPosition);
            when(rooms.getRoom(17)).thenReturn(room);
            when(room.getLayout()).thenReturn(layout);
            when(layout.getTile((short) 4, (short) 5)).thenReturn(serverPosition);
        }
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            event.handle();
            verify(rooms).enterRoom(habbo, 17, "password", false, reconnect ? serverPosition : null, reconnect);
            verify(rooms, never()).loadRoom(anyInt());
        } finally {
            buffer.release();
        }
    }
}

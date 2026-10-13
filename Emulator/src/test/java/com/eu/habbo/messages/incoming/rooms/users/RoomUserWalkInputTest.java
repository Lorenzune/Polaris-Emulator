package com.eu.habbo.messages.incoming.rooms.users;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.ClientMessage;
import org.junit.jupiter.api.Test;

class RoomUserWalkInputTest {
    @Test
    void firstAcceptedClickAfterFloodUsesItsOwnCoordinates() throws Exception {
        GameClient client = mock(GameClient.class, RETURNS_DEEP_STUBS);
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        RoomUnit unit = new RoomUnit();
        unit.setLocation(tile(0, 0));
        unit.setInRoom(true);
        when(client.getHabbo().getRoomUnit()).thenReturn(unit);
        when(client.getHabbo().getHabboInfo().getCurrentRoom()).thenReturn(room);
        RoomTile clicked = tile(4, 5);
        when(room.getLayout().getTile((short) 4, (short) 5)).thenReturn(clicked);
        // A previous burst must not replace the current click with an old target.
        unit.getCacheable().put("__walkFloodWindow", System.currentTimeMillis() - 2_000L);
        unit.getCacheable().put("__walkFloodCount", 16);
        unit.getCacheable().put("__walkLastX", 99);
        unit.getCacheable().put("__walkLastY", 99);

        RoomUserWalkEvent event = event(client, 4, 5);
        event.handle();

        verify(room.getCycleManager()).startUserWalk(client.getHabbo(), clicked);
        verify(room.getLayout(), never()).getTile((short) 99, (short) 99);
    }

    @Test
    void floodLimitStillDropsExcessClicksWithoutStartingMovement() throws Exception {
        GameClient client = mock(GameClient.class, RETURNS_DEEP_STUBS);
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        RoomUnit unit = new RoomUnit();
        when(client.getHabbo().getRoomUnit()).thenReturn(unit);
        when(client.getHabbo().getHabboInfo().getCurrentRoom()).thenReturn(room);
        unit.getCacheable().put("__walkFloodWindow", System.currentTimeMillis());
        unit.getCacheable().put("__walkFloodCount", 15);

        event(client, 4, 5).handle();

        verify(room.getCycleManager(), never()).startUserWalk(any(), any());
    }

    private static RoomUserWalkEvent event(GameClient client, int x, int y) {
        RoomUserWalkEvent event = new RoomUserWalkEvent();
        event.client = client;
        event.packet = mock(ClientMessage.class);
        when(event.packet.readInt()).thenReturn(x, y);
        return event;
    }

    private static RoomTile tile(int x, int y) {
        return new RoomTile((short) x, (short) y, (short) 0, RoomTileState.OPEN, true);
    }
}

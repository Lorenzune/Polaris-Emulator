package com.eu.habbo.messages.incoming.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.navigator.RequestCreateRoomEvent;
import com.eu.habbo.messages.incoming.rooms.users.RoomUserGiveRespectEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class RoomInteractionLimitsTest {

    @Test
    void creationEditingAndPersistenceUseSixtyCharacterRoomNames() throws Exception {
        assertEquals(60, privateInt(RequestCreateRoomEvent.class, "MAX_ROOM_NAME_LENGTH"));
        assertEquals(60, privateInt(RoomSettingsSaveEvent.class, "MAX_ROOM_NAME_LENGTH"));

        Room room = createRoom();
        room.setName("a".repeat(60));
        assertEquals(60, room.getName().length());

        room.setName("b".repeat(61));
        assertEquals(60, room.getName().length());
    }

    @Test
    void rapidRespectsAreNotDroppedByPacketRateLimiting() {
        assertEquals(0, new RoomUserGiveRespectEvent().getRatelimit());
    }

    private static int privateInt(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static Room createRoom() throws Exception {
        Constructor<Room> constructor = Room.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(41, 7);
    }
}

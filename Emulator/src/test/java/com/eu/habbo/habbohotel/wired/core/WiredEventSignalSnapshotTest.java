package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredEventSignalSnapshotTest {

    @Test
    void signalEventsReuseAlreadyImmutableForwardedSources() {
        Room room = mock(Room.class);
        RoomUnit forwardedUser = mock(RoomUnit.class);
        List<RoomUnit> forwardedUsers = List.copyOf(List.of(forwardedUser));

        WiredEvent event = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .signalSourceUsers(forwardedUsers)
                .build();

        assertSame(forwardedUsers, event.getSignalSourceUsers());
    }

    @Test
    void signalEventsKeepDefensiveSnapshotForMutableInputsAndDropNulls() {
        Room room = mock(Room.class);
        RoomUnit forwardedUser = mock(RoomUnit.class);
        List<RoomUnit> forwardedUsers = new ArrayList<>();
        forwardedUsers.add(forwardedUser);
        forwardedUsers.add(null);

        WiredEvent.Builder builder =
                WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room).signalSourceUsers(forwardedUsers);
        forwardedUsers.clear();

        assertEquals(List.of(forwardedUser), builder.build().getSignalSourceUsers());
    }
}

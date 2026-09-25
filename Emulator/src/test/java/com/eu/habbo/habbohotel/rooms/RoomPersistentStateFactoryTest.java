package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoomPersistentStateFactoryTest {

    @Test
    void roomSaveSnapshotDoesNotRetainMutableMoodlightObjects() {
        Room room = new Room(41, 7, new RoomDependencies(() -> {
            throw new UnsupportedOperationException("No database access expected");
        }));
        RoomMoodlightData liveData = new RoomMoodlightData(1, true, true, "#112233", 128);
        room.getMoodlightData().put(liveData.getId(), liveData);

        RoomPersistence.State snapshot = RoomPersistentStateFactory.capture(room);
        liveData.setColor("#AABBCC");
        liveData.setIntensity(20);

        RoomMoodlightData savedMoodlight = snapshot.moodlightData().stream()
                .filter(data -> data.getId() == liveData.getId())
                .findFirst()
                .orElseThrow();
        assertEquals("#112233", savedMoodlight.getColor());
        assertEquals(128, savedMoodlight.getIntensity());
    }
}

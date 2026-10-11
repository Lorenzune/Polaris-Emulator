package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomTimeUtilitiesPersistenceTest {
    @Test
    void roomCreationTimeSurvivesPersistence() throws Exception {
        var source = new RoomJdbcTestSupport.RecordingDataSource();
        try (var ignored = RoomJdbcTestSupport.install(source)) {
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(44);
            var manager = new RoomVariableManager(room);
            var type = Class.forName(RoomVariableManager.class.getName() + "$VariableAssignment");
            var constructor = type.getDeclaredConstructor(int.class, int.class, int.class);
            constructor.setAccessible(true);
            Object assignment = constructor.newInstance(3, 101, 202);
            var upsert = RoomVariableManager.class.getDeclaredMethod("upsertPersistentAssignment", int.class, type);
            upsert.setAccessible(true);
            upsert.invoke(manager, 9, assignment);
            assertEquals(
                    Map.of(1, 44, 2, 9, 3, 3, 4, 101, 5, 202),
                    source.calls().getFirst().parameters());
        }
    }
}

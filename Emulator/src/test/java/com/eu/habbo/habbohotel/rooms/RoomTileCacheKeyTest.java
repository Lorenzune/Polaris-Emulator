package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class RoomTileCacheKeyTest {
    @Test
    void equivalentCoordinatesRetrieveTheSameCacheEntry() {
        RoomTile original = new RoomTile((short) 12, (short) 34, (short) 0, RoomTileState.OPEN, true);
        RoomTile equivalent = new RoomTile((short) 12, (short) 34, (short) 9, RoomTileState.BLOCKED, false);
        var cache = new ConcurrentHashMap<RoomTile, String>();
        cache.put(original, "furni");

        assertEquals("furni", cache.get(equivalent));
        assertEquals("furni", cache.remove(equivalent));
        assertTrue(cache.isEmpty());
    }

    @Test
    void mutableTilePropertiesDoNotInvalidateItsCacheKey() {
        RoomTile tile = new RoomTile((short) -12, (short) 34, (short) 0, RoomTileState.OPEN, true);
        var cache = new ConcurrentHashMap<RoomTile, String>();
        cache.put(tile, "furni");
        tile.setStackHeight(3.5);
        tile.state = RoomTileState.SIT;

        assertEquals("furni", cache.get(tile.copy()));
        assertNull(cache.get(new RoomTile((short) -11, (short) 34, (short) 0, RoomTileState.OPEN, true)));
    }
}

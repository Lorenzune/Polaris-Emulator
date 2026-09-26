package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.jupiter.api.Test;

class RoomItemPassThroughHeightTest {

    @Test
    void passingThroughFurnitureKeepsCurrentHeightInsteadOfStackingOnIt() {
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile platformTile = new RoomTile((short) 4, (short) 7, (short) 0, RoomTileState.OPEN, true);
        when(layout.getHeightAtSquare(4, 7)).thenReturn((short) 0);

        double resultingHeight =
                RoomItemMovementService.getFurniturePassThroughHeight(0, layout, Collections.singleton(platformTile));

        assertEquals(0, resultingHeight);
    }

    @Test
    void passingThroughFurnitureStillRespectsHigherFloorBase() {
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile higherFloorTile = new RoomTile((short) 4, (short) 7, (short) 2, RoomTileState.OPEN, true);
        when(layout.getHeightAtSquare(4, 7)).thenReturn((short) 2);

        double resultingHeight = RoomItemMovementService.getFurniturePassThroughHeight(
                1, layout, Collections.singleton(higherFloorTile));

        assertEquals(2, resultingHeight);
    }
}

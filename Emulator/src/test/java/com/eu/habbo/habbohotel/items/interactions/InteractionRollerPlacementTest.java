package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.Test;

class InteractionRollerPlacementTest {

    @Test
    void rollerCanBePlacedOnAnEmptyFloorTile() {
        InteractionRoller roller = createRoller();

        assertTrue(roller.canStackAt(null, List.of(Pair.create(new RoomTile(), Set.of()))));
    }

    @Test
    void rollerCanBePlacedOnOneWalkableStackableFloorSurface() {
        InteractionRoller roller = createRoller();
        HabboItem floorSurface = mock(HabboItem.class);
        Item floorBaseItem = mock(Item.class);
        when(floorSurface.getBaseItem()).thenReturn(floorBaseItem);
        when(floorBaseItem.allowStack()).thenReturn(true);
        when(floorBaseItem.allowWalk()).thenReturn(true);

        assertTrue(roller.canStackAt(null, List.of(Pair.create(new RoomTile(), Set.of(floorSurface)))));
        assertTrue(roller.canStackAt(null, List.of(Pair.create(new RoomTile(), Set.of(roller, floorSurface)))));
    }

    @Test
    void rollerCannotBePlacedOnNonWalkableFurnitureOrFurnitureStacks() {
        InteractionRoller roller = createRoller();
        HabboItem support = mock(HabboItem.class);
        Item supportBaseItem = mock(Item.class);
        when(support.getBaseItem()).thenReturn(supportBaseItem);
        when(supportBaseItem.allowStack()).thenReturn(true);
        when(supportBaseItem.allowWalk()).thenReturn(false);

        HabboItem otherFurniture = mock(HabboItem.class);
        Item otherBaseItem = mock(Item.class);
        when(otherFurniture.getBaseItem()).thenReturn(otherBaseItem);
        when(otherBaseItem.allowStack()).thenReturn(true);
        when(otherBaseItem.allowWalk()).thenReturn(false);
        assertFalse(roller.canStackAt(null, List.of(Pair.create(new RoomTile(), Set.of(support)))));
        assertFalse(roller.canStackAt(null, List.of(Pair.create(new RoomTile(), Set.of(support, otherFurniture)))));
        assertFalse(roller.canStackAt(null, List.of(Pair.create(new RoomTile(), Set.of(createRoller())))));
    }

    private static InteractionRoller createRoller() {
        return new InteractionRoller(1, 1, mock(Item.class), "0", 0, 0);
    }
}

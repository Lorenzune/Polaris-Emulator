package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class MovingItemUnitCollisionTest {

    @Test
    void nonWalkableChairChecksUnitsAndDetectsCollision() {
        Room room = mock(Room.class);
        RoomItemIndex index = mock(RoomItemIndex.class);
        RoomItemManager facade = mock(RoomItemManager.class);
        RoomItemPlacementService placement = new RoomItemPlacementService(room, index, facade);
        RoomItemMovementService movementService = new RoomItemMovementService(room, facade, placement);

        HabboItem chair = mock(HabboItem.class);
        Item baseChair = mock(Item.class);
        when(chair.getBaseItem()).thenReturn(baseChair);
        when(chair.isWalkable()).thenReturn(false);
        when(baseChair.allowSit()).thenReturn(true);
        when(baseChair.allowLay()).thenReturn(false);
        when(baseChair.getWidth()).thenReturn(1);
        when(baseChair.getLength()).thenReturn(1);

        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile targetTile = new RoomTile((short) 3, (short) 3, (short) 0, RoomTileState.OPEN, true);
        when(layout.fitsOnMap(targetTile, 1, 1, 0)).thenReturn(true);
        when(layout.getTilesAt(targetTile, 1, 1, 0)).thenReturn(Collections.singleton(targetTile));

        // Target tile has a habbo
        when(room.hasHabbosAt(3, 3)).thenReturn(true);

        FurnitureMovementError error = movementService.furnitureFitsAtWithPhysics(
                targetTile, chair, 0, true, WiredMovementPhysics.NONE);

        assertEquals(FurnitureMovementError.TILE_HAS_HABBOS, error);
    }

    @Test
    void walkableTileWithoutExtraDetectsCollision() {
        Room room = mock(Room.class);
        RoomItemIndex index = mock(RoomItemIndex.class);
        RoomItemManager facade = mock(RoomItemManager.class);
        RoomItemPlacementService placement = new RoomItemPlacementService(room, index, facade);
        RoomItemMovementService movementService = new RoomItemMovementService(room, facade, placement);

        HabboItem rug = mock(HabboItem.class);
        Item baseRug = mock(Item.class);
        when(rug.getBaseItem()).thenReturn(baseRug);
        when(rug.isWalkable()).thenReturn(true);
        when(baseRug.allowSit()).thenReturn(false);
        when(baseRug.allowLay()).thenReturn(false);
        when(baseRug.getWidth()).thenReturn(1);
        when(baseRug.getLength()).thenReturn(1);

        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile targetTile = new RoomTile((short) 3, (short) 3, (short) 0, RoomTileState.OPEN, true);
        when(layout.fitsOnMap(targetTile, 1, 1, 0)).thenReturn(true);
        when(layout.getTilesAt(targetTile, 1, 1, 0)).thenReturn(Collections.singleton(targetTile));

        // Target tile has a habbo, and rug is moving with checkForUnits = true
        when(room.hasHabbosAt(3, 3)).thenReturn(true);

        FurnitureMovementError error = movementService.furnitureFitsAtWithPhysics(
                targetTile, rug, 0, true, WiredMovementPhysics.NONE);

        assertEquals(FurnitureMovementError.TILE_HAS_HABBOS, error);
    }

    @Test
    void magicTileWithoutExtraDetectsCollision() {
        Room room = mock(Room.class);
        RoomItemIndex index = mock(RoomItemIndex.class);
        RoomItemManager facade = mock(RoomItemManager.class);
        RoomItemPlacementService placement = new RoomItemPlacementService(room, index, facade);
        RoomItemMovementService movementService = new RoomItemMovementService(room, facade, placement);

        InteractionTileWalkMagic magicTile = mock(InteractionTileWalkMagic.class);
        Item baseMagic = mock(Item.class);
        when(magicTile.getBaseItem()).thenReturn(baseMagic);
        when(magicTile.isWalkable()).thenReturn(true);
        when(baseMagic.allowSit()).thenReturn(false);
        when(baseMagic.allowLay()).thenReturn(false);
        when(baseMagic.getWidth()).thenReturn(1);
        when(baseMagic.getLength()).thenReturn(1);

        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile targetTile = new RoomTile((short) 3, (short) 3, (short) 0, RoomTileState.OPEN, true);
        when(layout.fitsOnMap(targetTile, 1, 1, 0)).thenReturn(true);
        when(layout.getTilesAt(targetTile, 1, 1, 0)).thenReturn(Collections.singleton(targetTile));

        // Target tile has a habbo
        when(room.hasHabbosAt(3, 3)).thenReturn(true);

        FurnitureMovementError error = movementService.furnitureFitsAtWithPhysics(
                targetTile, magicTile, 0, true, WiredMovementPhysics.NONE);

        assertEquals(FurnitureMovementError.TILE_HAS_HABBOS, error);
    }

    @Test
    void moveThroughUsersPhysicsAllowsPassage() {
        Room room = mock(Room.class);
        RoomItemIndex index = mock(RoomItemIndex.class);
        RoomItemManager facade = mock(RoomItemManager.class);
        RoomItemPlacementService placement = new RoomItemPlacementService(room, index, facade);
        RoomItemMovementService movementService = new RoomItemMovementService(room, facade, placement);

        HabboItem chair = mock(HabboItem.class);
        Item baseChair = mock(Item.class);
        when(chair.getBaseItem()).thenReturn(baseChair);
        when(chair.isWalkable()).thenReturn(false);
        when(baseChair.allowSit()).thenReturn(true);
        when(baseChair.allowLay()).thenReturn(false);
        when(baseChair.getWidth()).thenReturn(1);
        when(baseChair.getLength()).thenReturn(1);
        when(chair.canStackAt(eq(room), any())).thenReturn(true);

        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile targetTile = new RoomTile((short) 3, (short) 3, (short) 0, RoomTileState.OPEN, true);
        when(layout.fitsOnMap(targetTile, 1, 1, 0)).thenReturn(true);
        when(layout.getTilesAt(targetTile, 1, 1, 0)).thenReturn(Collections.singleton(targetTile));

        RoomUnit userUnit = new RoomUnit();
        userUnit.setId(42);
        userUnit.setRoomUnitType(RoomUnitType.USER);
        userUnit.setLocation(targetTile);
        when(room.getRoomUnits(targetTile)).thenReturn(Collections.singleton(userUnit));

        Set<Integer> passThroughUserIds = new HashSet<>();
        passThroughUserIds.add(42);
        WiredMovementPhysics physics = new WiredMovementPhysics(
                false, Collections.emptySet(), passThroughUserIds, Collections.emptySet());

        FurnitureMovementError error = movementService.furnitureFitsAtWithPhysics(
                targetTile, chair, 0, true, physics);

        assertEquals(FurnitureMovementError.NONE, error);
    }

    @Test
    void wiredCollisionTriggerMatchesAnyCollidingItemWhenNoSelection() {
        com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerCollision trigger =
                new com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerCollision(
                        1, 1, mock(Item.class), "", 0, 0);

        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        unit.setId(10);
        HabboItem movingFurni = mock(HabboItem.class);
        when(movingFurni.getId()).thenReturn(101);

        com.eu.habbo.habbohotel.wired.core.WiredEvent event =
                com.eu.habbo.habbohotel.wired.migrate.WiredEvents.botCollision(room, unit, movingFurni);

        assertTrue(event.getSourceItem().isPresent());
        assertSame(movingFurni, event.getSourceItem().get());
        assertTrue(event.getActor().isPresent());
        assertSame(unit, event.getActor().get());
        assertTrue(trigger.matches(null, event));
    }

    @Test
    void wiredCollisionTriggerMatchesWhenUserSittingOnSelectedChair() {
        com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerCollision trigger =
                new com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerCollision(
                        1, 1, mock(Item.class), "", 0, 0);

        Room room = mock(Room.class);
        RoomTile chairTile = new RoomTile((short) 4, (short) 4, (short) 0, RoomTileState.OPEN, true);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 4, (short) 4)).thenReturn(chairTile);

        HabboItem chair = mock(HabboItem.class);
        when(chair.getId()).thenReturn(202);
        when(chair.getX()).thenReturn((short) 4);
        when(chair.getY()).thenReturn((short) 4);
        when(room.getTopItemAt((short) 4, (short) 4)).thenReturn(chair);
        when(room.getTallestChair(chairTile)).thenReturn(chair);
        when(room.getItemsAt((short) 4, (short) 4)).thenReturn(Collections.singleton(chair));

        RoomUnit unit = new RoomUnit();
        unit.setId(10);
        unit.setLocation(chairTile);

        HabboItem movingFurni = mock(HabboItem.class);
        when(movingFurni.getId()).thenReturn(101);

        // Configure trigger to have chair in its items selection
        com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings settings =
                mock(com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings.class);
        when(settings.getIntParams()).thenReturn(new int[]{com.eu.habbo.habbohotel.wired.core.WiredSourceUtil.SOURCE_SELECTED});
        when(settings.getFurniIds()).thenReturn(new int[]{202});
        com.eu.habbo.Emulator emulator = mock(com.eu.habbo.Emulator.class);
        com.eu.habbo.habbohotel.GameEnvironment gameEnvironment = mock(com.eu.habbo.habbohotel.GameEnvironment.class);
        com.eu.habbo.habbohotel.rooms.RoomManager roomManager = mock(com.eu.habbo.habbohotel.rooms.RoomManager.class);
        when(roomManager.getRoom(anyInt())).thenReturn(room);
        when(room.getHabboItem(202)).thenReturn(chair);

        // Use reflection to add chair to trigger items
        try {
            java.lang.reflect.Field itemsField = com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerCollision.class.getDeclaredField("items");
            itemsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<HabboItem> items = (Set<HabboItem>) itemsField.get(trigger);
            items.add(chair);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        com.eu.habbo.habbohotel.wired.core.WiredEvent event =
                com.eu.habbo.habbohotel.wired.migrate.WiredEvents.botCollision(room, unit, movingFurni);

        // Event has movingFurni as source item, but actor is sitting on selected chair -> matches!
        assertTrue(trigger.matches(null, event));
    }

    @Test
    void multiTileFurniCollisionTriggersOnlyImpactedAvatar() {
        Room room = mock(Room.class);
        RoomTile seat1Tile = new RoomTile((short) 4, (short) 4, (short) 0, RoomTileState.OPEN, true);
        RoomTile seat2Tile = new RoomTile((short) 4, (short) 5, (short) 0, RoomTileState.OPEN, true);

        RoomUnit unitOnSeat1 = new RoomUnit();
        unitOnSeat1.setId(10);
        unitOnSeat1.setLocation(seat1Tile);

        RoomUnit unitOnSeat2 = new RoomUnit();
        unitOnSeat2.setId(20);
        unitOnSeat2.setLocation(seat2Tile);

        when(room.getRoomUnits(seat1Tile)).thenReturn(Collections.singleton(unitOnSeat1));
        when(room.getRoomUnits(seat2Tile)).thenReturn(Collections.singleton(unitOnSeat2));

        HabboItem movingFurni = mock(HabboItem.class);
        when(movingFurni.getId()).thenReturn(999);

        // Moving furni collides specifically with seat 1
        Collection<RoomTile> occupiedByMovingFurni = Collections.singletonList(seat1Tile);

        // Verify that triggerCollisionsAtTiles only queries units on seat1Tile, not seat2Tile
        com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper.triggerCollisionsAtTiles(
                room, movingFurni, occupiedByMovingFurni);

        verify(room, times(1)).getRoomUnits(seat1Tile);
        verify(room, never()).getRoomUnits(seat2Tile);
    }
}

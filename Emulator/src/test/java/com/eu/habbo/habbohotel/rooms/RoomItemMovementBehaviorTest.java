package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoomItemMovementBehaviorTest {

    @ParameterizedTest
    @ValueSource(strings = {"height", "physics_height", "implicit", "physics_implicit"})
    void successfulInstantMoveRevokesBeforeCallbacksAndRefreshButKeepsNewCallbackAnimation(String mode) {
        try (var emulator = mockStatic(Emulator.class, RETURNS_DEEP_STUBS)) {
            var fixture = new PublicationFixture();
            var old = fixture.animation();
            var callback = new ArrayList<ServerMessage>();
            doAnswer(invocation -> {
                        assertMovementCount(old, 0);
                        callback.add(fixture.animation());
                        return null;
                    })
                    .when(fixture.item)
                    .onMove(any(), any(), any());
            doAnswer(invocation -> {
                        assertMovementCount(old, 0);
                        return null;
                    })
                    .when(fixture.room)
                    .sendComposer(any(ServerMessage.class));

            FurnitureMovementError result =
                    switch (mode) {
                        case "height" ->
                            fixture.service.moveFurniTo(fixture.item, fixture.target, 0, .02, null, true, false);
                        case "physics_height" ->
                            fixture.service.moveFurniToWithPhysics(
                                    fixture.item, fixture.target, 0, .02, null, true, false, fixture.physics, true);
                        case "implicit" ->
                            fixture.service.moveFurniTo(fixture.item, fixture.target, 0, null, true, false);
                        default ->
                            fixture.service.moveFurniToWithPhysics(
                                    fixture.item, fixture.target, 0, null, true, false, fixture.physics);
                    };
            assertEquals(FurnitureMovementError.NONE, result);
            verify(fixture.room).sendComposer(any(ServerMessage.class));
            assertEquals(1, callback.size());
            assertMovementCount(callback.getFirst(), 1);
        }
    }

    @Test
    void failedInstantMoveDoesNotRevokeAlreadyComposedAnimation() {
        try (var emulator = mockStatic(Emulator.class, RETURNS_DEEP_STUBS)) {
            var fixture = new PublicationFixture();
            var old = fixture.animation();
            when(fixture.placement.furnitureFitsAt(any(), any(), anyInt(), anyBoolean()))
                    .thenReturn(FurnitureMovementError.INVALID_MOVE);
            assertEquals(
                    FurnitureMovementError.INVALID_MOVE,
                    fixture.service.moveFurniTo(fixture.item, fixture.target, 0, .02, null, true, false));
            assertMovementCount(old, 1);
            verify(fixture.room, never()).sendComposer(any(ServerMessage.class));
        }
    }

    @Test
    void animatedMutationWithoutInstantRefreshDoesNotRevokeThePreviousPath() {
        try (var emulator = mockStatic(Emulator.class, RETURNS_DEEP_STUBS)) {
            var fixture = new PublicationFixture();
            var old = fixture.animation();
            assertEquals(
                    FurnitureMovementError.NONE,
                    fixture.service.moveFurniTo(fixture.item, fixture.target, 0, .02, null, false, false));
            assertMovementCount(old, 1);
            verify(fixture.room, never()).sendComposer(any(ServerMessage.class));
        }
    }

    private static void assertMovementCount(ServerMessage message, int expected) {
        var bytes = message.get();
        try {
            bytes.skipBytes(6);
            assertEquals(expected, bytes.readInt());
        } finally {
            bytes.release();
        }
    }

    private static final class PublicationFixture {
        final Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        final HabboItem item = mock(HabboItem.class, RETURNS_DEEP_STUBS);
        final RoomItemPlacementService placement = mock(RoomItemPlacementService.class);
        final RoomItemMovementService service =
                new RoomItemMovementService(room, mock(RoomItemManager.class), placement);
        final RoomTile target = new RoomTile((short) 1, (short) 3, (short) 0, RoomTileState.OPEN, true);
        final WiredMovementPhysics physics = new WiredMovementPhysics(true, Set.of(), Set.of(), Set.of());

        PublicationFixture() {
            when(item.getId()).thenReturn(17141);
            when(item.getBaseItem().getWidth()).thenReturn(1);
            when(item.getBaseItem().getLength()).thenReturn(1);
            when(room.getLayout().getTile(anyShort(), anyShort())).thenReturn(target);
            when(room.getLayout().getTilesAt(any(), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> new HashSet<>(Set.of(target)));
            when(placement.isStackPlacementBypassItem(item)).thenReturn(true);
            when(placement.furnitureFitsAt(any(), any(), anyInt(), anyBoolean()))
                    .thenReturn(FurnitureMovementError.NONE);
        }

        ServerMessage animation() {
            return new WiredMovementsComposer(
                            List.of(WiredMovementsComposer.furniMovement(item.getId(), 16, 13, 18, 15, .02, .02)))
                    .compose();
        }
    }

    @Test
    void explicitHeightMovementRejectsNullItemsAcrossPhysicsModes() {
        RoomItemManager manager = new RoomItemManager(mock(Room.class));
        RoomTile tile = new RoomTile();

        assertEquals(FurnitureMovementError.INVALID_MOVE, manager.moveFurniTo(null, tile, 0, 0.0, null, false, false));
        assertEquals(
                FurnitureMovementError.INVALID_MOVE,
                manager.moveFurniToWithPhysics(
                        null, tile, 0, 0.0, null, false, false, mock(WiredMovementPhysics.class)));
    }

    @Test
    void inactivePhysicsUsesTheNormalFitContract() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile tile = new RoomTile();
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);
        when(layout.fitsOnMap(tile, 1, 1, 0)).thenReturn(false);
        WiredMovementPhysics physics = mock(WiredMovementPhysics.class);
        when(physics.isActive()).thenReturn(false);
        RoomItemManager manager = new RoomItemManager(room);

        assertEquals(
                manager.furnitureFitsAt(tile, item, 0, true),
                manager.furnitureFitsAtWithPhysics(tile, item, 0, true, physics));
    }

    @Test
    void slideRejectsFurnitureThatCannotStackAtTheTarget() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile tile = new RoomTile();
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getWidth()).thenReturn(1);
        when(baseItem.getLength()).thenReturn(1);
        when(layout.getTilesAt(tile, 1, 1, 0)).thenReturn(Set.of(tile));
        when(item.canStackAt(any(), anyList())).thenReturn(false);

        assertEquals(FurnitureMovementError.CANT_STACK, new RoomItemManager(room).slideFurniTo(item, tile, 0));
    }
}

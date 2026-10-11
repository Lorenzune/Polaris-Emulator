package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMoveNoAnimation;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WiredMovementBatchTest {
    @Test
    void cancellationPreservesOtherFurnitureAndUserUpdates() {
        var independent = WiredMovementsComposer.furniMovement(810, 19, 18, 20, 19, 2.04, 2.04);
        var direction = WiredMovementsComposer.userDirectionUpdate(7, 2, 2);
        var movements = new ArrayList<>(List.of(
                WiredMovementsComposer.furniMovement(141, 18, 19, 20, 21, 0.02, 0.02),
                independent,
                direction,
                WiredMovementsComposer.furniMovement(141, 20, 21, 20, 21, 0.02, 0.04)));
        WiredMovementsComposer.discardFurnitureMovements(movements, 141);
        assertEquals(List.of(independent, direction), movements);
        var newMove = WiredMovementsComposer.furniMovement(141, 1, 3, 7, 14, 0.02, 0.02);
        movements.add(newMove);
        WiredMovementsComposer.discardFurnitureMovements(movements, 999);
        assertEquals(
                List.of(independent, direction, newMove),
                movements,
                "Later movements start a new path after the reset and must survive");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "extra",
                "variables",
                "failed_variables",
                "variables_then_move",
                "extra_other_thread",
                "variables_other_thread",
                "variables_other_thread_then_move",
                "extra_other_thread_nested"
            })
    void instantResetCancelsTheItemsEarlierCollectedAnimation(String resetPath) throws Exception {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        HabboItem piece = mock(HabboItem.class),
                animatedEffect = mock(HabboItem.class),
                resetEffect = mock(HabboItem.class);
        when(resetEffect.getX()).thenReturn((short) 5);
        when(room.getRoomSpecialTypes().getExtras(5, 0)).thenReturn(Set.of(mock(WiredExtraMoveNoAnimation.class)));
        double[] position = {18, 19, 0.02};
        when(piece.getId()).thenReturn(141);
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(piece.getBaseItem()).thenReturn(base);
        when(piece.getX()).thenAnswer(i -> (short) position[0]);
        when(piece.getY()).thenAnswer(i -> (short) position[1]);
        when(piece.getZ()).thenAnswer(i -> position[2]);
        when(room.getLayout().getTile(anyShort(), anyShort()))
                .thenAnswer(i -> new RoomTile(i.getArgument(0), i.getArgument(1), (short) 0, RoomTileState.OPEN, true));
        when(room.moveFurniTo(eq(piece), any(), eq(0), anyDouble(), isNull(), eq(false), eq(false)))
                .thenAnswer(i -> {
                    RoomTile target = i.getArgument(1);
                    position[0] = target.x;
                    position[1] = target.y;
                    position[2] = i.getArgument(3);
                    return FurnitureMovementError.NONE;
                });
        when(room.furnitureFitsAtWithPhysics(any(), eq(piece), eq(0), eq(false), any()))
                .thenReturn(FurnitureMovementError.NONE);
        when(room.moveFurniTo(eq(piece), any(), eq(0), anyDouble(), isNull(), eq(true), eq(false)))
                .thenAnswer(i -> {
                    WiredMoveCarryHelper.discardCollectedFurnitureMovements(piece.getId());
                    RoomTile target = i.getArgument(1);
                    position[0] = target.x;
                    position[1] = target.y;
                    position[2] = i.getArgument(3);
                    return FurnitureMovementError.NONE;
                });
        when(room.moveFurniToWithPhysics(
                        eq(piece), any(), eq(0), anyDouble(), isNull(), eq(true), eq(false), any(), eq(true)))
                .thenAnswer(i -> {
                    WiredMoveCarryHelper.discardCollectedFurnitureMovements(piece.getId());
                    RoomTile target = i.getArgument(1);
                    position[0] = target.x;
                    position[1] = target.y;
                    position[2] = i.getArgument(3);
                    return FurnitureMovementError.NONE;
                });
        RoomTile board = new RoomTile((short) 20, (short) 21, (short) 0, RoomTileState.OPEN, true);
        RoomTile deposit = new RoomTile((short) 1, (short) 3, (short) 0, RoomTileState.OPEN, true);
        WiredMoveCarryHelper.beginMovementCollection();
        try {
            if (resetPath.endsWith("nested")) WiredMoveCarryHelper.beginMovementCollection();
            assertEquals(
                    FurnitureMovementError.NONE,
                    WiredMoveCarryHelper.moveFurni(room, animatedEffect, piece, board, 0, 0.02, null, false, null));
            if (resetPath.equals("failed_variables")) {
                when(room.moveFurniTo(eq(piece), any(), eq(0), anyDouble(), isNull(), eq(true), eq(false)))
                        .thenReturn(FurnitureMovementError.INVALID_MOVE);
                assertFalse(WiredInternalVariableSupport.writeFurniValue(room, piece, "@position_x", 1));
                assertMovement(WiredMoveCarryHelper.finishMovementCollection(), 18, 19, 20, 21, 0.02, 0.02);
                return;
            }
            Runnable reset = () -> {
                if (resetPath.startsWith("variables")) {
                    assertTrue(WiredInternalVariableSupport.writeFurniValue(room, piece, "@position_x", 1));
                    assertTrue(WiredInternalVariableSupport.writeFurniValue(room, piece, "@position_y", 3));
                } else {
                    assertEquals(
                            FurnitureMovementError.NONE,
                            WiredMoveCarryHelper.moveFurni(
                                    room, resetEffect, piece, deposit, 0, 0.02, null, false, null));
                }
            };
            if (resetPath.contains("other_thread")) {
                try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                    executor.submit(reset).get(5, java.util.concurrent.TimeUnit.SECONDS);
                }
            } else reset.run();
            assertEquals(1, piece.getX());
            assertEquals(3, piece.getY());
            if (resetPath.endsWith("then_move")) {
                assertEquals(
                        FurnitureMovementError.NONE,
                        WiredMoveCarryHelper.moveFurni(room, animatedEffect, piece, board, 0, 0.02, null, false, null));
                assertMovement(WiredMoveCarryHelper.finishMovementCollection(), 1, 3, 20, 21, 0.02, 0.02);
                return;
            }
            if (resetPath.endsWith("nested")) assertNull(WiredMoveCarryHelper.finishMovementCollection());
            assertNull(
                    WiredMoveCarryHelper.finishMovementCollection(),
                    "A collected board animation must not be sent after the instant deposit reset");
        } finally {
            WiredMoveCarryHelper.clearThreadLocalsForCurrentThread();
        }
    }

    @Test
    void promotionCombinesHorizontalMoveAndFinalAltitudeWithoutInstantRefresh() {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        HabboItem skull = mock(HabboItem.class), effect = mock(HabboItem.class);
        double[] position = {1, 29, 9.2};
        when(skull.getId()).thenReturn(141);
        when(skull.getX()).thenAnswer(i -> (short) position[0]);
        when(skull.getY()).thenAnswer(i -> (short) position[1]);
        when(skull.getZ()).thenAnswer(i -> position[2]);
        when(room.getLayout().getTile(anyShort(), anyShort()))
                .thenAnswer(i -> new RoomTile(i.getArgument(0), i.getArgument(1), (short) 0, RoomTileState.OPEN, true));
        RoomTile destination = new RoomTile((short) 20, (short) 19, (short) 0, RoomTileState.OPEN, true);
        when(room.moveFurniTo(eq(skull), eq(destination), eq(0), anyDouble(), isNull(), eq(false), eq(false)))
                .thenAnswer(i -> {
                    position[0] = destination.x;
                    position[1] = destination.y;
                    position[2] = i.getArgument(3);
                    return FurnitureMovementError.NONE;
                });
        WiredMoveCarryHelper.beginMovementCollection();
        try {
            assertEquals(
                    FurnitureMovementError.NONE,
                    WiredMoveCarryHelper.moveFurni(room, effect, skull, destination, 0, 2.04, null, false, null));
            assertEquals(
                    FurnitureMovementError.NONE,
                    WiredMoveCarryHelper.moveFurni(room, effect, skull, destination, 0, 2.20, null, true, null));
            assertEquals(2.20, skull.getZ());
            verify(room, never())
                    .moveFurniTo(eq(skull), any(), anyInt(), anyDouble(), isNull(), eq(true), anyBoolean());
            assertMovement(WiredMoveCarryHelper.finishMovementCollection(), 1, 29, 20, 19, 9.2, 2.20);
        } finally {
            WiredMoveCarryHelper.clearThreadLocalsForCurrentThread();
        }
    }

    @Test
    void independentFurniRemainIndependentAndTheFirstOriginSurvivesThreeEffects() {
        ServerMessage message = new WiredMovementsComposer(List.of(
                        WiredMovementsComposer.furniMovement(141, 1, 29, 20, 19, 9.2, 2.04),
                        WiredMovementsComposer.furniMovement(810, 19, 18, 20, 19, 2.04, 2.04),
                        WiredMovementsComposer.furniMovement(141, 20, 19, 20, 19, 2.04, 2.22),
                        WiredMovementsComposer.furniMovement(141, 20, 19, 20, 19, 2.22, 2.20)))
                .compose();
        ByteBuf buffer = message.get();
        try {
            ByteBuf payload = buffer.duplicate();
            payload.skipBytes(6);
            assertEquals(2, payload.readInt());
            readMovement(payload, 141, 1, 29, 20, 19, 9.2, 2.20);
            readMovement(payload, 810, 19, 18, 20, 19, 2.04, 2.04);
            assertFalse(payload.isReadable());
        } finally {
            buffer.release();
        }
    }

    private static void assertMovement(ServerMessage message, int x, int y, int tx, int ty, double z, double tz) {
        assertNotNull(message);
        ByteBuf buffer = message.get();
        try {
            ByteBuf payload = buffer.duplicate();
            payload.skipBytes(6);
            assertEquals(1, payload.readInt());
            readMovement(payload, 141, x, y, tx, ty, z, tz);
            assertFalse(payload.isReadable());
        } finally {
            buffer.release();
        }
    }

    private static void readMovement(ByteBuf p, int id, int x, int y, int tx, int ty, double z, double tz) {
        assertEquals(1, p.readInt());
        assertEquals(x, p.readInt());
        assertEquals(y, p.readInt());
        assertEquals(tx, p.readInt());
        assertEquals(ty, p.readInt());
        assertEquals(
                z,
                Double.parseDouble(p.readCharSequence(p.readUnsignedShort(), StandardCharsets.UTF_8)
                        .toString()));
        assertEquals(
                tz,
                Double.parseDouble(p.readCharSequence(p.readUnsignedShort(), StandardCharsets.UTF_8)
                        .toString()));
        assertEquals(id, p.readInt());
        assertEquals(500, p.readInt());
        assertEquals(0, p.readInt());
        assertFalse(p.readBoolean());
        assertFalse(p.readBoolean());
    }
}

package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredMovementBatchTest {
    @Test
    void promotionCombinesHorizontalMoveAndFinalAltitudeWithoutInstantRefresh() {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        HabboItem skull = mock(HabboItem.class), effect = mock(HabboItem.class);
        double[] position = {1, 29, 9.2};
        when(skull.getId()).thenReturn(141);
        when(skull.getX()).thenAnswer(i -> (short) position[0]);
        when(skull.getY()).thenAnswer(i -> (short) position[1]);
        when(skull.getZ()).thenAnswer(i -> position[2]);
        when(room.getLayout().getTile(anyShort(), anyShort())).thenAnswer(i ->
                new RoomTile(i.getArgument(0), i.getArgument(1), (short) 0, RoomTileState.OPEN, true));
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
            assertEquals(FurnitureMovementError.NONE, WiredMoveCarryHelper.moveFurni(
                    room, effect, skull, destination, 0, 2.04, null, false, null));
            assertEquals(FurnitureMovementError.NONE, WiredMoveCarryHelper.moveFurni(
                    room, effect, skull, destination, 0, 2.20, null, true, null));
            assertEquals(2.20, skull.getZ());
            verify(room, never()).moveFurniTo(
                    eq(skull), any(), anyInt(), anyDouble(), isNull(), eq(true), anyBoolean());
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
                WiredMovementsComposer.furniMovement(141, 20, 19, 20, 19, 2.22, 2.20))).compose();
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
        assertEquals(z, Double.parseDouble(p.readCharSequence(p.readUnsignedShort(), StandardCharsets.UTF_8).toString()));
        assertEquals(tz, Double.parseDouble(p.readCharSequence(p.readUnsignedShort(), StandardCharsets.UTF_8).toString()));
        assertEquals(id, p.readInt());
        assertEquals(500, p.readInt());
        assertEquals(0, p.readInt());
        assertFalse(p.readBoolean());
        assertFalse(p.readBoolean());
    }
}

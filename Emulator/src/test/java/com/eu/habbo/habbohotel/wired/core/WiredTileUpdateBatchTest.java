package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class WiredTileUpdateBatchTest {
    private MockedStatic<Emulator> emulator;

    @BeforeEach
    void setUp() {
        emulator = mockStatic(Emulator.class);
        ConfigurationManager config = mock(ConfigurationManager.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
    }

    @AfterEach
    void tearDown() {
        WiredMoveCarryHelper.clearThreadLocalsForCurrentThread();
        emulator.close();
    }

    @Test
    void repeatedUpdatesSendOneFinalHeightInsteadOfEveryIntermediateHeight() {
        Room room = mock(Room.class);
        RoomTile tile = tile(1);
        WiredMoveCarryHelper.beginMovementCollection();
        for (int i = 0; i < 1500; i++) {
            tile.setStackHeight(i % 10);
            WiredMoveCarryHelper.sendOrCollectTileUpdates(room, List.of(tile));
        }
        verifyNoInteractions(room);
        assertNull(WiredMoveCarryHelper.finishMovementCollection());
        List<ServerMessage> messages = sent(room);
        assertEquals(1, messages.size());
        ByteBuf buffer = messages.getFirst().get();
        try {
            buffer.skipBytes(6);
            assertEquals(1, buffer.readUnsignedByte());
            buffer.skipBytes(2);
            assertEquals(9 * 256, buffer.readShort());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void largeBatchRespectsProtocolLimitWithoutLosingTiles() {
        Room room = mock(Room.class);
        WiredMoveCarryHelper.beginMovementCollection();
        for (int i = 0; i < 300; i++) {
            WiredMoveCarryHelper.sendOrCollectTileUpdates(room, List.of(tile(i)));
        }
        WiredMoveCarryHelper.finishMovementCollection();
        List<ServerMessage> messages = sent(room);
        assertEquals(3, messages.size());
        int index = 0;
        for (ServerMessage message : messages) {
            assertEquals(Outgoing.UpdateStackHeightComposer, message.getHeader());
            ByteBuf buffer = message.get();
            try {
                buffer.skipBytes(6);
                int count = buffer.readUnsignedByte();
                assertTrue(count > 0 && count <= 127);
                for (int i = 0; i < count; i++, index++) {
                    assertEquals(index % 100, buffer.readUnsignedByte());
                    assertEquals(index / 100, buffer.readUnsignedByte());
                    buffer.readShort();
                }
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
        assertEquals(300, index);
    }

    @Test
    void nestedScopesKeepRoomsSeparateAndOnlyFlushAtOuterEnd() {
        Room first = mock(Room.class);
        Room second = mock(Room.class);
        WiredMoveCarryHelper.beginMovementCollection();
        WiredMoveCarryHelper.sendOrCollectTileUpdates(first, List.of(tile(1)));
        WiredMoveCarryHelper.beginMovementCollection();
        WiredMoveCarryHelper.sendOrCollectTileUpdates(second, List.of(tile(1)));
        assertNull(WiredMoveCarryHelper.finishMovementCollection());
        verifyNoInteractions(first, second);
        WiredMoveCarryHelper.finishMovementCollection();
        assertEquals(1, sent(first).size());
        assertEquals(1, sent(second).size());
    }

    @Test
    void updatesOutsideABatchAreSentImmediately() {
        Room room = mock(Room.class);
        WiredMoveCarryHelper.sendOrCollectTileUpdates(room, List.of(tile(1)));
        assertEquals(1, sent(room).size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<ServerMessage> sent(Room room) {
        ArgumentCaptor<Collection<ServerMessage>> captor = ArgumentCaptor.forClass((Class) Collection.class);
        verify(room).sendComposers(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    private static RoomTile tile(int index) {
        return new RoomTile((short) (index % 100), (short) (index / 100), (short) 0, RoomTileState.OPEN, true);
    }
}

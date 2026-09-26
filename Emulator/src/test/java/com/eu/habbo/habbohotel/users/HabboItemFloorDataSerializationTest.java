package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HabboItemFloorDataSerializationTest {

    @Test
    void floorItemDataCarriesPhysicalHeightForNonWalkableFurniture() {
        Item baseItem = mock(Item.class);
        when(baseItem.getSpriteId()).thenReturn(42);
        when(baseItem.getHeight()).thenReturn(0.75D);
        when(baseItem.getName()).thenReturn("decorative_furni");
        when(baseItem.getInteractionType()).thenReturn(new ItemInteraction("default", TestItem.class));
        TestItem item = new TestItem(1001, baseItem);
        ServerMessage message = new ServerMessage(123);

        item.serializeFloorData(message);

        ByteBuf packet = message.get();
        try {
            packet.skipBytes(6); // frame length and message header
            packet.skipBytes(20); // id, sprite id, x, y and rotation
            readString(packet); // z position
            assertEquals("0.75", readString(packet));
        } finally {
            packet.release();
        }
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static final class TestItem extends HabboItem {
        private TestItem(int id, Item item) {
            super(id, 7, item, "0", 0, 0);
        }

        @Override
        public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
            return false;
        }

        @Override
        public boolean isWalkable() {
            return false;
        }

        @Override
        public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}
    }
}

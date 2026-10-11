package com.eu.habbo.messages.outgoing.rooms.users;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredUserOpacityComposerTest {
    @Test
    void serializesCountAndValuesWithoutExposingInvisibleAvatars() {
        RoomUnit unit = new RoomUnit();
        unit.setId(0);
        unit.setWiredOpacity(30);
        RoomUnit hidden = new RoomUnit();
        hidden.setId(8);
        hidden.setInvisible(true);
        var buffer = new WiredUserOpacityComposer(54, List.of(unit, hidden))
                .compose()
                .get();
        try {
            buffer.readInt();
            assertEquals(5116, buffer.readShort());
            assertEquals(54, buffer.readInt());
            assertEquals(1, buffer.readInt());
            assertEquals(0, buffer.readInt());
            assertEquals(30, buffer.readInt());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}

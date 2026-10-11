package com.eu.habbo.messages.outgoing.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.rooms.WiredHighlightState;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredHighlightComposerTest {
    @Test
    void writesRoomCountAndTargetFieldsInParserOrder() {
        var buffer = new WiredHighlightComposer(
                        54,
                        List.of(new WiredHighlightState(10, 12, 9, 0x3388ff), new WiredHighlightState(100, 0, 0, 0)))
                .compose()
                .get();
        try {
            buffer.readInt();
            assertEquals(5117, buffer.readShort());
            assertEquals(54, buffer.readInt());
            assertEquals(2, buffer.readInt());
            for (int value : new int[] {10, 12, 9, 0x3388ff, 100, 0, 0, 0}) assertEquals(value, buffer.readInt());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}

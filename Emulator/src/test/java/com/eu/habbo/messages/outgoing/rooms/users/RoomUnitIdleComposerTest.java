package com.eu.habbo.messages.outgoing.rooms.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.DanceType;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

class RoomUnitIdleComposerTest {

    @Test
    void hidesIdleEffectWhileUnitIsDancing() {
        RoomUnit unit = new RoomUnit();
        unit.setId(42);
        unit.setIdle();

        assertTrue(readIdleFlag(unit));

        unit.setDanceType(DanceType.HAB_HOP);

        assertFalse(readIdleFlag(unit));
    }

    private static boolean readIdleFlag(RoomUnit unit) {
        ByteBuf packet = new RoomUnitIdleComposer(unit).compose().get();
        try {
            return packet.getBoolean(10);
        } finally {
            packet.release();
        }
    }
}

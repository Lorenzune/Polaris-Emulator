package com.eu.habbo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PacketUtilsTest {

    @Test
    void formatPacketEscapesProtocolControlBytesAndPreservesText() {
        ByteBuf packet = Unpooled.buffer();
        packet.writeByte(0);
        packet.writeByte(1);
        packet.writeByte(30);
        packet.writeByte(31);
        packet.writeBytes("room".getBytes(StandardCharsets.US_ASCII));

        try {
            assertEquals("[0][1][30]\u001froom", PacketUtils.formatPacket(packet));
        } finally {
            packet.release();
        }
    }
}

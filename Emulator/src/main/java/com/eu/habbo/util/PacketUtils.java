package com.eu.habbo.util;

import io.netty.buffer.ByteBuf;
import java.nio.charset.Charset;

public class PacketUtils {

    public static String formatPacket(ByteBuf buffer) {
        String packet = buffer.toString(Charset.defaultCharset());
        StringBuilder result = new StringBuilder(packet.length());

        for (int index = 0; index < packet.length(); index++) {
            char value = packet.charAt(index);
            if (value <= 30) {
                result.append('[').append((int) value).append(']');
            } else if (value == Character.MAX_VALUE) {
                result.append("[-1]");
            } else {
                result.append(value);
            }
        }

        return result.toString();
    }
}

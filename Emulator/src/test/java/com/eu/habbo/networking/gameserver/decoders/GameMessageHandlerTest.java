package com.eu.habbo.networking.gameserver.decoders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import org.junit.jupiter.api.Test;

class GameMessageHandlerTest {

    @Test
    void recognizesNonTlsTrafficWrappedByTheDecoder() {
        Throwable error = new DecoderException(new NotSslRecordException("not an SSL/TLS record"));

        assertTrue(GameMessageHandler.hasCause(error, NotSslRecordException.class));
    }

    @Test
    void doesNotMislabelAnUnrelatedDecoderFailureAsNonTlsTraffic() {
        Throwable error = new DecoderException(new IllegalArgumentException("bad frame"));

        assertFalse(GameMessageHandler.hasCause(error, NotSslRecordException.class));
    }
}

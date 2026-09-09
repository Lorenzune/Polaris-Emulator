package com.eu.habbo.networking.gameserver.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.networking.gameserver.GameServerAttributes;
import com.eu.habbo.networking.gameserver.crypto.WsHandshakeHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class IdleTimeoutHandlerTest {

    @Test
    void gamePacketsWaitForWebSocketUpgradeAndCryptoHandshake() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast("wsProtocolHandler", new ChannelInboundHandlerAdapter());
        channel.pipeline().addLast(WsHandshakeHandler.HANDLER_NAME, new WsHandshakeHandler());
        channel.pipeline().addLast("idle", new IdleTimeoutHandler(30, 60));
        ChannelHandlerContext idleContext = channel.pipeline().context("idle");

        try {
            assertFalse(IdleTimeoutHandler.isGameTransportReady(idleContext));

            channel.attr(GameServerAttributes.WS_UPGRADED).set(Boolean.TRUE);
            assertFalse(IdleTimeoutHandler.isGameTransportReady(idleContext));

            channel.pipeline().remove(WsHandshakeHandler.HANDLER_NAME);
            assertTrue(IdleTimeoutHandler.isGameTransportReady(idleContext));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void legacyTcpTransportRemainsReadyWithoutWebSocketState() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            assertTrue(GameServerAttributes.isGameTransportReady(channel));
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}

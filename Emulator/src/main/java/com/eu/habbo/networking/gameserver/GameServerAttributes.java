package com.eu.habbo.networking.gameserver;

import com.eu.habbo.crypto.HabboRC4;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.networking.gameserver.crypto.WsHandshakeHandler;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class GameServerAttributes {

    public static final AttributeKey<GameClient> CLIENT = AttributeKey.valueOf("GameClient");
    public static final AttributeKey<HabboRC4> CRYPTO_CLIENT = AttributeKey.valueOf("CryptoClient");
    public static final AttributeKey<HabboRC4> CRYPTO_SERVER = AttributeKey.valueOf("CryptoServer");
    public static final AttributeKey<String> WS_IP = AttributeKey.valueOf("WebSocketIP");
    public static final AttributeKey<String> USER_AGENT = AttributeKey.valueOf("UserAgent");
    public static final AttributeKey<Boolean> WS_UPGRADED = AttributeKey.valueOf("WebSocketUpgraded");
    public static final AttributeKey<byte[]> WS_AES_KEY = AttributeKey.valueOf("WsAesKey");

    public static boolean isGameTransportReady(Channel channel) {
        if (channel == null || !channel.isOpen()) {
            return false;
        }

        // The legacy TCP listener has no WebSocket protocol handler and keeps
        // its established packet path. On the shared HTTPS/WSS listener, game
        // packets must wait until both the HTTP upgrade and optional AES
        // handshake have completed.
        if (channel.pipeline().context("wsProtocolHandler") == null) {
            return true;
        }

        return Boolean.TRUE.equals(channel.attr(WS_UPGRADED).get())
                && channel.pipeline().context(WsHandshakeHandler.HANDLER_NAME) == null;
    }
}

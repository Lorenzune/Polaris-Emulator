package com.eu.habbo.networking.gameserver.handlers;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslHandler;

/**
 * Removes the HTTP-only request handlers once a channel has upgraded to
 * WebSocket. They sit ahead of the WebSocket decoders and several are bound to
 * the blocking HTTP executor, so leaving them in place hops every inbound game
 * frame onto that executor for a no-op pass-through and lets a slow HTTP query
 * on a shared worker delay game traffic.
 */
public class WebSocketHttpCleanupHandler extends ChannelInboundHandlerAdapter {
    private static final String[] HTTP_ONLY_HANDLERS = {
        "wsHttpHandler",
        "nitroSecureAssetHandler",
        "nitroSecureApiHandler",
        "authHttpHandler",
        "badgeHttpHandler",
        "badgeLeaderboardHttpHandler",
        "emuStatsHttpHandler",
        "rewardTrackHttpHandler",
        "dailyTaskHttpHandler",
        "collectibleHttpHandler",
    };

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete handshake = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            String userAgent = handshake.requestHeaders().get(HttpHeaderNames.USER_AGENT);
            if (userAgent != null && !userAgent.isEmpty()) {
                ctx.channel().attr(GameServerAttributes.USER_AGENT).set(userAgent);
                GameClient client = ctx.channel().attr(GameServerAttributes.CLIENT).get();
                if (client != null) {
                    client.setUserAgent(userAgent);
                }
            }

            ChannelPipeline pipeline = ctx.pipeline();

            // A completed upgrade must never leave the outbound path in the
            // intermediate HTTP state. In that state WebSocketCodec creates a
            // BinaryWebSocketFrame, but the frame reaches HttpServerCodec and
            // SslHandler without first being serialized to a ByteBuf. Netty
            // then rejects the ping (and every other server packet) with an
            // UnsupportedMessageTypeException.
            ensureWebSocketEncoder(pipeline);

            for (String name : HTTP_ONLY_HANDLERS) {
                if (pipeline.get(name) != null) {
                    pipeline.remove(name);
                }
            }
            removeIfPresent(pipeline, "httpAggregator");
            removeIfPresent(pipeline, "httpCodec");

            ctx.channel().attr(GameServerAttributes.WS_UPGRADED).set(Boolean.TRUE);
            ctx.fireUserEventTriggered(evt);
            pipeline.remove(this);
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    private static void ensureWebSocketEncoder(ChannelPipeline pipeline) {
        if (pipeline.context(WebSocketFrameEncoder.class) != null) {
            return;
        }

        WebSocket13FrameEncoder encoder = new WebSocket13FrameEncoder(false);
        if (pipeline.context("httpCodec") != null) {
            pipeline.addBefore("httpCodec", "wsencoder", encoder);
            return;
        }

        ChannelHandlerContext sslContext = pipeline.context(SslHandler.class);
        if (sslContext != null) {
            pipeline.addAfter(sslContext.name(), "wsencoder", encoder);
            return;
        }

        pipeline.addFirst("wsencoder", encoder);
    }

    private static void removeIfPresent(ChannelPipeline pipeline, String name) {
        if (pipeline.context(name) != null) {
            pipeline.remove(name);
        }
    }
}

package com.eu.habbo.networking.gameserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.networking.gameserver.codec.WebSocketCodec;
import com.eu.habbo.networking.gameserver.handlers.WebSocketHttpCleanupHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.EventExecutor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebSocketHttpOffloadTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void blockingHttpHandlersLeaveTheSocketEventLoopAndKeepChannelOrdering() throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object previousConfig = configField.get(null);
        Path configFile = this.temporaryDirectory.resolve("config.ini");
        Files.writeString(
                configFile,
                "nitro.secure.master_key=test-key\n"
                        + "nitro.secure.assets.enabled=true\n"
                        + "crypto.ws.enabled=false\n");
        configField.set(null, new ConfigurationManager(configFile.toString()));

        EventLoopGroup eventLoops = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        NioSocketChannel channel = new NioSocketChannel();
        try {
            eventLoops.register(channel).syncUninterruptibly();
            channel.eventLoop()
                    .submit(() -> new WebSocketChannelInitializer().initChannel(channel))
                    .syncUninterruptibly();

            EventExecutor socketExecutor = channel.eventLoop();
            EventExecutor blockingExecutor =
                    awaitPresent(channel, "nitroSecureAssetHandler").executor();

            assertNotSame(socketExecutor, blockingExecutor);
            assertSame(
                    blockingExecutor, awaitPresent(channel, "badgeHttpHandler").executor());
            assertSame(
                    blockingExecutor,
                    awaitPresent(channel, "badgeLeaderboardHttpHandler").executor());
            assertSame(
                    blockingExecutor,
                    awaitPresent(channel, "emuStatsHttpHandler").executor());
            assertSame(socketExecutor, awaitPresent(channel, "wsHttpHandler").executor());
            assertSame(socketExecutor, awaitPresent(channel, "authHttpHandler").executor());
        } finally {
            channel.close().syncUninterruptibly();
            eventLoops.shutdownGracefully().syncUninterruptibly();
            BlockingHttpExecutionGroup.shutdown();
            configField.set(null, previousConfig);
        }
    }

    @Test
    void httpHandlersAreRemovedAfterTheWebSocketUpgrade() throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object previousConfig = configField.get(null);
        Path configFile = this.temporaryDirectory.resolve("config.ini");
        Files.writeString(
                configFile,
                "nitro.secure.master_key=test-key\n"
                        + "nitro.secure.assets.enabled=true\n"
                        + "crypto.ws.enabled=false\n");
        configField.set(null, new ConfigurationManager(configFile.toString()));

        EventLoopGroup eventLoops = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        NioSocketChannel channel = new NioSocketChannel();
        try {
            eventLoops.register(channel).syncUninterruptibly();
            channel.eventLoop()
                    .submit(() -> new WebSocketChannelInitializer().initChannel(channel))
                    .syncUninterruptibly();

            channel.eventLoop()
                    .submit(() -> channel.pipeline()
                            .fireUserEventTriggered(new WebSocketServerProtocolHandler.HandshakeComplete(
                                    "/", EmptyHttpHeaders.INSTANCE, null)))
                    .syncUninterruptibly();

            // Handlers bound to the blocking HTTP executor are unlinked on that
            // executor, so wait for the removals to settle.
            awaitRemoved(channel, "nitroSecureAssetHandler");
            awaitRemoved(channel, "nitroSecureApiHandler");
            awaitRemoved(channel, "authHttpHandler");
            awaitRemoved(channel, "badgeHttpHandler");
            awaitRemoved(channel, "badgeLeaderboardHttpHandler");
            awaitRemoved(channel, "emuStatsHttpHandler");
            assertNull(channel.pipeline().context("httpAggregator"));
            assertNull(channel.pipeline().context("httpCodec"));
            assertNotNull(channel.pipeline().context(WebSocketFrameEncoder.class));
            assertEquals(Boolean.TRUE, channel.attr(GameServerAttributes.WS_UPGRADED).get());
            assertNotNull(channel.pipeline().context("wsCodec"));
        } finally {
            channel.close().syncUninterruptibly();
            eventLoops.shutdownGracefully().syncUninterruptibly();
            BlockingHttpExecutionGroup.shutdown();
            configField.set(null, previousConfig);
        }
    }

    @Test
    void completedUpgradeRepairsAStaleHttpOutboundPipeline() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast("httpCodec", new HttpServerCodec());
        channel.pipeline().addLast("httpAggregator", new HttpObjectAggregator(500000));
        channel.pipeline().addLast("wsHttpCleanup", new WebSocketHttpCleanupHandler());
        channel.pipeline().addLast("wsCodec", new WebSocketCodec());

        try {
            channel.pipeline()
                    .fireUserEventTriggered(new WebSocketServerProtocolHandler.HandshakeComplete(
                            "/", EmptyHttpHeaders.INSTANCE, null));

            assertNull(channel.pipeline().context("httpCodec"));
            assertNull(channel.pipeline().context("httpAggregator"));
            assertNotNull(channel.pipeline().context(WebSocketFrameEncoder.class));
            assertEquals(Boolean.TRUE, channel.attr(GameServerAttributes.WS_UPGRADED).get());

            ByteBuf packet = Unpooled.buffer().writeInt(0x4455434B);
            channel.writeOutbound(packet);

            ByteBuf wireFrame = channel.readOutbound();
            assertNotNull(wireFrame);
            wireFrame.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void awaitRemoved(NioSocketChannel channel, String handlerName) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (channel.pipeline().context(handlerName) != null) {
            if (System.nanoTime() > deadline) {
                break;
            }
            Thread.sleep(5);
        }
        assertNull(channel.pipeline().context(handlerName));
    }

    private static ChannelHandlerContext awaitPresent(NioSocketChannel channel, String handlerName)
            throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        ChannelHandlerContext context;
        while ((context = channel.pipeline().context(handlerName)) == null && System.nanoTime() <= deadline) {
            Thread.sleep(5);
        }
        assertNotNull(context, () -> "Missing pipeline handler: " + handlerName);
        return context;
    }

    @Test
    void blockingHttpWorkerCountUsesTheConfiguredValue() {
        BlockingHttpExecutionGroup.shutdown();
        try {
            int executors = 0;
            for (EventExecutor ignored : BlockingHttpExecutionGroup.get(3)) {
                executors++;
            }
            assertEquals(3, executors);
        } finally {
            BlockingHttpExecutionGroup.shutdown();
        }
    }
}

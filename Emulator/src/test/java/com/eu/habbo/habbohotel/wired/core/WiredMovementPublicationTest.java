package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.ServerMessageFrame;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import com.eu.habbo.networking.gameserver.encoders.GameServerMessageEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WiredMovementPublicationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resetAfterCompositionRemovesOnlyOldFurnitureAtTransport(boolean broadcast) throws Exception {
        WiredMoveCarryHelper.beginMovementCollection();
        ServerMessage message;
        try {
            var field = WiredMoveCarryHelper.class.getDeclaredField("COLLECTED_MOVEMENTS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var collection = (List<WiredMovementsComposer.MovementData>) ((ThreadLocal<?>) field.get(null)).get();
            WiredMovementCollection.addFurniture(collection, 7141, move(7141));
            WiredMovementCollection.addFurniture(collection, 7810, move(7810));
            collection.add(WiredMovementsComposer.userDirectionUpdate(7, 2, 2));
            message = WiredMoveCarryHelper.finishMovementCollection();
        } finally {
            WiredMoveCarryHelper.clearThreadLocalsForCurrentThread();
        }
        if (broadcast) ServerMessageFrame.prepareBroadcast(message);
        foreignReset(7141);
        assertEquals(List.of(7810, -7), write(message, broadcast));
    }

    @Test
    void directComposerAndEachRecipientObserveResetWithoutChangingEarlierFrame() throws Exception {
        ServerMessage message = new WiredMovementsComposer(List.of(move(7142))).compose();
        ServerMessageFrame.prepareBroadcast(message);
        assertEquals(List.of(7142), write(message, true));
        foreignReset(7142);
        assertEquals(List.of(), write(message, true));
    }

    @Test
    void normalizationRetainsResetGenerationAndAllowsSubsequentMovement() throws Exception {
        var old = move(7143);
        foreignReset(7143);
        ServerMessage stale = new WiredMovementsComposer(List.of(old)).compose();
        assertEquals(List.of(), write(stale, false), "Normalization must not renew an old animation");
        var next = WiredMovementsComposer.furniMovement(7143, 1, 3, 7, 14, .02, .02);
        ServerMessage current = new WiredMovementsComposer(List.of(next,
                WiredMovementsComposer.furniMovement(7143, 7, 14, 7, 14, .02, .1))).compose();
        assertEquals(List.of(7143), write(current, false));
    }

    @Test
    void normalFurnitureAndUserOnlyMessagesKeepTheirTransportBehavior() throws Exception {
        assertEquals(List.of(7144), write(new WiredMovementsComposer(List.of(move(7144))).compose(), true));
        assertEquals(List.of(-8), write(new WiredMovementsComposer(List.of(
                WiredMovementsComposer.userDirectionUpdate(8, 4, 4))).compose(), true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resetWhileFrameWaitsForEncoderDoesNotPublishAnOldDestination(boolean broadcast) throws Exception {
        ServerMessage message = new WiredMovementsComposer(List.of(move(7145))).compose();
        if (broadcast) ServerMessageFrame.prepareBroadcast(message);
        var encoded = new LinkedBlockingQueue<ByteBuf>();
        var executors = new DefaultEventExecutorGroup(1);
        var channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object output, ChannelPromise promise) {
                encoded.add((ByteBuf) output);
                promise.setSuccess();
            }
        });
        var release = new CountDownLatch(1);
        try {
            channel.pipeline().addLast(executors, "delayed-encoder", new GameServerMessageEncoder());
            var entered = new CountDownLatch(1);
            executors.next().submit(() -> {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            writeToChannel(channel, message, broadcast);
            foreignReset(7145);
            assertTrue(encoded.isEmpty(), "The fixture must hold the frame before serialization");
            release.countDown();
            // The downstream EmbeddedChannel context needs its owner to drain scheduled tasks.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (encoded.isEmpty() && System.nanoTime() < deadline) {
                channel.runPendingTasks();
                Thread.yield();
            }
            ByteBuf bytes = encoded.poll();
            assertNotNull(bytes);
            try {
                bytes.skipBytes(6);
                assertEquals(0, bytes.readInt());
                assertEquals(0, bytes.readableBytes());
            } finally { bytes.release(); }
        } finally {
            release.countDown();
            executors.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
            channel.runPendingTasks();
            ByteBuf remaining;
            while ((remaining = encoded.poll()) != null) remaining.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void oldAndNewEpochInSameComposerStartsAtTheResetLocation() throws Exception {
        var old = move(7146);
        foreignReset(7146);
        ServerMessage message = new WiredMovementsComposer(List.of(old,
                WiredMovementsComposer.furniMovement(7146, 1, 3, 7, 14, .02, .1))).compose();
        ByteBuf bytes = message.get();
        try {
            bytes.skipBytes(6);
            assertEquals(1, bytes.readInt());
            assertEquals(1, bytes.readInt());
            assertEquals(1, bytes.readInt());
            assertEquals(3, bytes.readInt());
            assertEquals(7, bytes.readInt());
            assertEquals(14, bytes.readInt());
        } finally { bytes.release(); }
    }

    private static WiredMovementsComposer.MovementData move(int id) {
        return WiredMovementsComposer.furniMovement(id, 16, 13, 18, 15, .02, .02);
    }

    private static void foreignReset(int id) throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> WiredMoveCarryHelper.discardCollectedFurnitureMovements(id)).get(5, TimeUnit.SECONDS);
        }
    }

    private static List<Integer> write(ServerMessage message, boolean broadcast) throws Exception {
        if (broadcast) ServerMessageFrame.prepareBroadcast(message);
        var channel = new EmbeddedChannel(new GameServerMessageEncoder());
        try {
            writeToChannel(channel, message, broadcast);
            channel.flush();
            ByteBuf bytes = channel.readOutbound();
            assertNotNull(bytes);
            try {
                bytes.skipBytes(6);
                int count = bytes.readInt();
                var ids = new ArrayList<Integer>();
                for (int i = 0; i < count; i++) {
                    int type = bytes.readInt();
                    if (type == 3) {
                        ids.add(-bytes.readInt());
                        bytes.skipBytes(8);
                    } else {
                        assertEquals(1, type);
                        bytes.skipBytes(16);
                        skipString(bytes);
                        skipString(bytes);
                        ids.add(bytes.readInt());
                        bytes.skipBytes(8);
                        if (bytes.readBoolean()) bytes.skipBytes(4);
                        if (bytes.readBoolean()) bytes.skipBytes(4);
                    }
                }
                assertEquals(0, bytes.readableBytes());
                return ids;
            } finally { bytes.release(); }
        } finally { channel.finishAndReleaseAll(); }
    }

    private static void writeToChannel(EmbeddedChannel channel, ServerMessage message, boolean broadcast) throws Exception {
        GameClient client = mock(GameClient.class, CALLS_REAL_METHODS);
        var field = GameClient.class.getDeclaredField("channel");
        field.setAccessible(true);
        field.set(client, channel);
        var writer = GameClient.class.getDeclaredMethod("writeResponse", ServerMessage.class, boolean.class, boolean.class);
        writer.setAccessible(true);
        writer.invoke(client, message, broadcast, false);
    }

    private static void skipString(ByteBuf bytes) { bytes.skipBytes(bytes.readUnsignedShort()); }
}

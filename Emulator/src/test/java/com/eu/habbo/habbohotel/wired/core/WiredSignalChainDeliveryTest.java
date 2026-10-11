package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WiredSignalChainDeliveryTest {
    @Test
    void realEngineDispatcherAndEffectsCompleteAChainThroughOwnedContinuations() throws Exception {
        Room room = room();
        List<Integer> delivered = new ArrayList<>();
        com.eu.habbo.habbohotel.wired.api.IWiredTrigger trigger =
                new com.eu.habbo.habbohotel.wired.api.IWiredTrigger() {
                    public WiredEvent.Type listensTo() {
                        return WiredEvent.Type.SIGNAL_RECEIVED;
                    }

                    public boolean matches(HabboItem item, WiredEvent event) {
                        return true;
                    }
                };
        var stack = new com.eu.habbo.habbohotel.wired.api.WiredStack(
                mock(HabboItem.class), trigger, List.of(), List.of(context -> {
                    int channel = context.event().getSignalChannel();
                    delivered.add(channel);
                    if (channel < 127) assertTrue(WiredManager.dispatchEffectTriggeredEvent(signal(room, channel + 1)));
                }));
        AtomicInteger indexCalls = new AtomicInteger();
        WiredEngine engine = spy(new WiredEngine(
                mock(WiredServices.class),
                (candidate, type) -> {
                    indexCalls.incrementAndGet();
                    return List.of(stack);
                },
                100,
                id -> room));
        List<Runnable> tasks = new ArrayList<>();
        var scheduler = new WiredSignalContinuationScheduler((task, delay) -> {
            tasks.add(task);
            return () -> true;
        });
        doAnswer(invocation -> {
                    scheduler.schedule(
                            invocation.getArgument(0),
                            invocation.getArgument(1),
                            invocation.getArgument(2),
                            invocation.getArgument(3));
                    return null;
                })
                .when(engine)
                .scheduleSignalContinuation(any(), any(), any(), anyLong());
        try (var runtime = new Runtime(engine, 5, 1000)) {
            assertTrue(WiredManager.dispatchEffectTriggeredEvent(signal(room, 0)));
            assertEquals(List.of(0, 1, 2, 3, 4), delivered);
            while (!tasks.isEmpty()) tasks.removeFirst().run();
            assertEquals(java.util.stream.IntStream.range(0, 128).boxed().toList(), delivered);
            assertEquals(0, scheduler.pendingCount());
            assertEquals(1, indexCalls.get());
        } finally {
            scheduler.shutdown();
            engine.shutdownScheduledWork();
        }
    }

    @Test
    void failedReceiverDoesNotDiscardAcceptedSiblingsAndNestedPublicSignalsAreQueued() throws Exception {
        Room room = room();
        WiredEngine engine = mock(WiredEngine.class);
        List<Integer> delivered = new ArrayList<>();
        when(engine.handleEvent(any(), anyBoolean())).thenAnswer(invocation -> {
            WiredEvent event = invocation.getArgument(0);
            delivered.add(event.getSignalChannel());
            if (event.getSignalChannel() == 0) {
                assertTrue(WiredManager.handleEvent(signal(room, 1)));
                assertTrue(WiredManager.dispatchEffectTriggeredEvent(signal(room, 2)));
                assertEquals(List.of(0), delivered, "Nested signal must not run recursively");
            }
            if (event.getSignalChannel() == 1) throw new IllegalStateException("broken receiver fixture");
            return true;
        });
        try (var runtime = new Runtime(engine, 100, 100)) {
            WiredManager.dispatchEffectTriggeredEvent(signal(room, 0));
            assertEquals(List.of(0, 1, 2), delivered);
            assertFalse(WiredSignalExecutionScope.active());
            WiredManager.dispatchEffectTriggeredEvent(signal(room, 3));
            assertEquals(List.of(0, 1, 2, 3), delivered);
        }
    }

    @Test
    void delayedFrameFailureDoesNotPreventOtherPublicationsOrTheirTail() throws Exception {
        Room room = room();
        WiredEngine engine = mock(WiredEngine.class);
        List<Integer> delivered = new ArrayList<>();
        when(engine.handleEvent(any(), anyBoolean())).thenAnswer(invocation -> {
            WiredEvent event = invocation.getArgument(0);
            delivered.add(event.getSignalChannel());
            return true;
        });
        try (var runtime = new Runtime(engine, 100, 100)) {
            WiredManager.runDelayedSignalWave(
                    room,
                    List.of(
                            () -> {
                                WiredManager.dispatchEffectTriggeredEvent(signal(room, 1));
                                throw new IllegalStateException("broken frame fixture");
                            },
                            () -> WiredManager.dispatchEffectTriggeredEvent(signal(room, 2))));
            assertEquals(List.of(1, 2), delivered);
        }
    }

    @Test
    void longChainCompletesInOrderAcrossSlicesWithOriginalPayloadAndNoLostTail() throws Exception {
        Room room = room();
        var item = mock(HabboItem.class);
        List<WiredEvent> events = new ArrayList<>();
        List<Integer> delivered = new ArrayList<>();
        for (int i = 0; i < 3000; i++)
            events.add(WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                    .signalChannel(i)
                    .sourceItem(item)
                    .signalSourceItems(List.of(item))
                    .callStackDepth(7)
                    .createdAtMs(1000L)
                    .build());
        WiredEngine engine = mock(WiredEngine.class);
        List<Runnable> resumptions = new ArrayList<>();
        when(engine.handleEvent(any(), anyBoolean())).thenAnswer(invocation -> {
            WiredEvent event = invocation.getArgument(0);
            assertSame(events.get(event.getSignalChannel()), event);
            assertEquals(7, event.getCallStackDepth());
            assertSame(item, event.getSignalSourceItems().getFirst());
            delivered.add(event.getSignalChannel());
            if (event.getSignalChannel() + 1 < events.size())
                assertTrue(WiredManager.dispatchEffectTriggeredEvent(events.get(event.getSignalChannel() + 1)));
            return true;
        });
        doAnswer(invocation -> {
                    resumptions.add(invocation.getArgument(1));
                    return null;
                })
                .when(engine)
                .scheduleSignalContinuation(eq(room), any(), any(), anyLong());
        try (var runtime = new Runtime(engine, 5, 10_000)) {
            assertTrue(WiredManager.dispatchEffectTriggeredEvent(events.getFirst()));
            assertEquals(List.of(0, 1, 2, 3, 4), delivered);
            while (!resumptions.isEmpty()) resumptions.removeFirst().run();
            assertEquals(java.util.stream.IntStream.range(0, 3000).boxed().toList(), delivered);
            assertFalse(WiredSignalExecutionScope.active());
            assertTrue(WiredManager.dispatchEffectTriggeredEvent(events.getLast()));
            assertEquals(3001, delivered.size());
        }
    }

    @Test
    void stoppingRunawayAdmissionDoesNotDiscardAlreadyQueuedSiblings() throws Exception {
        Room room = room();
        WiredEngine engine = mock(WiredEngine.class);
        List<Integer> delivered = new ArrayList<>();
        when(engine.handleEvent(any(), anyBoolean())).thenAnswer(invocation -> {
            WiredEvent event = invocation.getArgument(0);
            delivered.add(event.getSignalChannel());
            if (event.getSignalChannel() == 0) {
                for (int i = 1; i <= 2; i++) assertTrue(WiredManager.dispatchEffectTriggeredEvent(signal(room, i)));
                assertFalse(WiredManager.dispatchEffectTriggeredEvent(signal(room, 3)));
            }
            return true;
        });
        try (var runtime = new Runtime(engine, 100, 3)) {
            WiredManager.dispatchEffectTriggeredEvent(signal(room, 0));
            assertEquals(List.of(0, 1, 2), delivered);
        }
    }

    @Test
    void nativeWaveRemainsAtomicAndIndependentPayloadsKeepTheirOrder() throws Exception {
        Room room = room();
        WiredEngine engine = mock(WiredEngine.class);
        List<Integer> delivered = new ArrayList<>();
        List<Runnable> resumptions = new ArrayList<>();
        doAnswer(invocation -> {
                    resumptions.add(invocation.getArgument(1));
                    return null;
                })
                .when(engine)
                .scheduleSignalContinuation(eq(room), any(), any(), anyLong());
        when(engine.executeSignalWave(any()))
                .thenAnswer(invocation -> ((java.util.function.Supplier<Boolean>) invocation.getArgument(0)).get());
        when(engine.handleEvent(any(), anyBoolean())).thenAnswer(invocation -> {
            WiredEvent event = invocation.getArgument(0);
            delivered.add(event.getSignalChannel());
            if (event.getSignalChannel() == 0) {
                for (int i = 1; i <= 4; i++)
                    assertTrue(WiredManager.dispatchEffectTriggeredEvent(
                            WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                                    .signalChannel(i)
                                    .signalPayloadExplicit(true)
                                    .build()));
            }
            return true;
        });
        try (var runtime = new Runtime(engine, 2, 100)) {
            WiredManager.dispatchEffectTriggeredEvent(signal(room, 0));
            assertEquals(List.of(0, 1, 2, 3, 4), delivered);
            verify(engine, times(1)).executeSignalWave(any());
        }
    }

    @Test
    void pacingRetainsPublicationAndReloadPreventsOldContinuation() throws Exception {
        Room room = room();
        WiredEngine engine = mock(WiredEngine.class);
        when(engine.signalExecutionDelay(room)).thenReturn(20L);
        List<Runnable> resumptions = new ArrayList<>();
        doAnswer(invocation -> {
                    assertEquals(20L, (long) invocation.getArgument(3));
                    resumptions.add(invocation.getArgument(1));
                    return null;
                })
                .when(engine)
                .scheduleSignalContinuation(eq(room), any(), any(), anyLong());
        try (var runtime = new Runtime(engine, 2, 100)) {
            WiredManager.dispatchEffectTriggeredEvent(signal(room, 0));
            verify(engine, never()).handleEvent(any(), anyBoolean());
            when(room.getLifecycleGeneration()).thenReturn(2L);
            resumptions.removeFirst().run();
            verify(engine, never()).handleEvent(any(), anyBoolean());
        }
    }

    private static WiredEvent signal(Room room, int channel) {
        return WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .signalChannel(channel)
                .build();
    }

    private static Room room() {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        when(room.isLoaded()).thenReturn(true);
        when(room.getLifecycleGeneration()).thenReturn(1L);
        when(room.getRoomSpecialTypes().getExtras(anyInt(), anyInt())).thenReturn(Set.of());
        return room;
    }

    private static class Runtime implements AutoCloseable {
        final java.lang.reflect.Field engine, initialized;
        final Object previousEngine, previousInitialized;
        final org.mockito.MockedStatic<Emulator> emulator;

        Runtime(WiredEngine replacement, int eventsPerSlice, int total) throws Exception {
            engine = WiredManager.class.getDeclaredField("engine");
            engine.setAccessible(true);
            initialized = WiredManager.class.getDeclaredField("initialized");
            initialized.setAccessible(true);
            previousEngine = engine.get(null);
            previousInitialized = initialized.get(null);
            engine.set(null, replacement);
            initialized.set(null, true);
            ConfigurationManager config = mock(ConfigurationManager.class);
            when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
            when(config.getInt("wired.signal.slice.events", 256)).thenReturn(eventsPerSlice);
            when(config.getInt("wired.signal.slice.ms", 8)).thenReturn(100_000);
            when(config.getInt("wired.signal.max.chain.ms", 250)).thenReturn(100_000);
            when(config.getInt("wired.signal.max.total.events.per.chain", 100_000))
                    .thenReturn(total);
            emulator = mockStatic(Emulator.class);
            emulator.when(Emulator::getConfig).thenReturn(config);
        }

        public void close() throws Exception {
            engine.set(null, previousEngine);
            initialized.set(null, previousInitialized);
            emulator.close();
        }
    }
}

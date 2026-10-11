package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WiredStackExecutorTest {

    @Test
    void fiveHundredSlotsAllowFiveHundredEventAndDirectExecutions() {
        for (boolean direct : new boolean[] {false, true}) {
            List<String> calls = new ArrayList<>();
            Room room = room(269);
            var special = mock(com.eu.habbo.habbohotel.rooms.RoomSpecialTypes.class);
            when(room.getRoomSpecialTypes()).thenReturn(special);
            java.util.Set<com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra> extras =
                    new java.util.LinkedHashSet<>();
            for (int i = 0; i < 5; i++) {
                var limit = new com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit(
                        14000 + i, 1, null, "", 0, 0);
                assertTrue(limit.saveData(
                        new com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings(
                                new int[] {100, 1000}, "", new int[0], 0),
                        null));
                extras.add(limit);
            }
            when(special.getExtras(0, 0)).thenReturn(extras);
            AtomicInteger executions = new AtomicInteger();
            var stack = new WiredStack(
                    item(14010), trigger(calls, true), List.of(), List.of(ctx -> executions.incrementAndGet()));
            var executor = executor(calls, 1000L);
            var event = event(room);
            for (int i = 0; i < 500; i++) {
                assertTrue(
                        direct
                                ? executor.executeDirect(stack, event, false)
                                : executor.executeEvent(stack, event, 1000L, false));
            }
            assertFalse(
                    direct
                            ? executor.executeDirect(stack, event, false)
                            : executor.executeEvent(stack, event, 1000L, false));
            assertEquals(500, executions.get());
        }
    }

    @Test
    void limitedReceiverCanFinishThroughItsNegativeBranchAfterResettingItsCondition() {
        List<String> calls = new ArrayList<>();
        Room room = room(268);
        var special = mock(com.eu.habbo.habbohotel.rooms.RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        var limit = new com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit(
                13354, 16, null, "", 0, 0);
        when(special.getExtras(0, 0)).thenReturn(java.util.Set.of(limit));
        var ready = new java.util.concurrent.atomic.AtomicBoolean(true);
        var finished = new java.util.concurrent.atomic.AtomicBoolean(false);
        var negative =
                new com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectNegativeSendSignal(
                        13355, 16, null, "", 0, 0) {
                    @Override
                    public void execute(WiredContext context) {
                        finished.set(true);
                    }
                };
        var stack = new WiredStack(
                item(13352),
                trigger(calls, true),
                List.of(ctx -> ready.get()),
                List.of(ctx -> ready.set(false), negative));
        var executor = executor(calls, 1000L);
        assertTrue(executor.executeEvent(stack, event(room), 1000L, false));
        assertTrue(executor.executeEvent(stack, event(room), 1100L, false));
        assertTrue(finished.get(), "The completion signal must survive the successful branch's limit");
        ready.set(true);
        assertFalse(
                executor.executeEvent(stack, event(room), 1200L, false),
                "A second successful reset must still respect the execution limit");
    }

    @Test
    void negativeSenderCannotAdvanceCounterWhileReceivingClockConditionIsFalse() {
        List<String> calls = new ArrayList<>();
        Room room = room(268);
        var active = new java.util.concurrent.atomic.AtomicBoolean(false);
        var counter = new AtomicInteger();
        var executor = executor(calls, 1000L);
        var signal = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room).build();
        var receiver = new WiredStack(
                item(12814),
                trigger(calls, true),
                List.of(ctx -> active.get()),
                List.of(ctx -> counter.incrementAndGet()));
        var sender =
                new com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectNegativeSendSignal(
                        12829, 16, null, "", 0, 0) {
                    @Override
                    public void execute(WiredContext context) {
                        dispatchSignalEvent(signal);
                    }
                };
        var source = new WiredStack(item(12835), trigger(calls, true), List.of(ctx -> false), List.of(sender));
        try (var manager = org.mockito.Mockito.mockStatic(WiredManager.class)) {
            manager.when(() -> WiredManager.dispatchEffectTriggeredEvent(signal))
                    .thenAnswer(i -> executor.executeEvent(receiver, signal, 1000L, false));
            assertTrue(executor.executeEvent(source, event(room, WiredEvent.Type.CUSTOM), 1000L, false));
            assertEquals(0, counter.get());
            active.set(true);
            assertTrue(executor.executeEvent(source, event(room, WiredEvent.Type.CUSTOM), 1000L, false));
            assertEquals(1, counter.get());
        }
    }

    @Test
    void oneClickAdmitsCaptureBeforePlacementLocksTheControllerInEitherStackOrder() {
        for (boolean placementFirst : new boolean[] {true, false}) {
            List<String> calls = new ArrayList<>();
            Room room = room(100);
            AtomicInteger phase = new AtomicInteger(0), captures = new AtomicInteger();
            IWiredTrigger trigger = trigger(calls, true);
            WiredStack placement = new WiredStack(
                    item(10001), trigger, List.of(ctx -> phase.get() == 0), List.of(ctx -> phase.set(2)));
            WiredStack capture = new WiredStack(
                    item(10002), trigger, List.of(ctx -> phase.get() == 0), List.of(ctx -> captures.incrementAndGet()));
            WiredStackExecutor executor = executor(calls, 1000L);
            WiredEvent click = event(room, WiredEvent.Type.USER_CLICKS_FURNI);
            assertTrue(executor.executeSignalBatch(click, () -> {
                executor.executeEvent(placementFirst ? placement : capture, click, 1000L, false);
                return executor.executeEvent(placementFirst ? capture : placement, click, 1000L, false);
            }));
            assertEquals(2, phase.get());
            assertEquals(1, captures.get());
            executor.executeSignalBatch(click, () -> executor.executeEvent(capture, click, 1000L, false));
            assertEquals(1, captures.get(), "A later click cannot bypass the locked controller");
        }
    }

    @Test
    void nativeSignalWaveKeepsTurnAdmissionStableAcrossIndependentPayloads() {
        List<String> calls = new ArrayList<>();
        Room room = room(96);
        AtomicInteger turn = new AtomicInteger(0), deliveries = new AtomicInteger();
        IWiredTrigger trigger = trigger(calls, true);
        WiredStack first =
                new WiredStack(item(9601), trigger, List.of(ctx -> turn.get() == 0), List.of(ctx -> turn.set(1)));
        WiredStack second =
                new WiredStack(item(9602), trigger, List.of(ctx -> turn.get() == 1), List.of(ctx -> turn.set(0)));
        WiredStack counter =
                new WiredStack(item(9603), trigger, List.of(), List.of(ctx -> deliveries.incrementAndGet()));
        WiredStackExecutor executor = executor(calls, 1000L);
        assertTrue(executor.executeSignalWave(() -> {
            boolean matched = false;
            for (int delivery = 0; delivery < 2; delivery++) {
                WiredEvent signal = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                        .signalPayloadExplicit(true)
                        .build();
                matched = executor.executeSignalBatch(signal, () -> {
                            executor.executeEvent(first, signal, 1000L, false);
                            executor.executeEvent(second, signal, 1000L, false);
                            return executor.executeEvent(counter, signal, 1000L, false);
                        })
                        || matched;
            }
            return matched;
        }));
        assertEquals(1, turn.get());
        assertEquals(2, deliveries.get());
    }

    @Test
    void admittedAltitudeReceiverDoesNotApplyAfterAnotherReceiverMovesItsSource() {
        for (boolean movementFirst : new boolean[] {true, false}) {
            List<String> calls = new ArrayList<>();
            Room room = room(99);
            var altitude = new java.util.concurrent.atomic.AtomicReference<Double>(2.06);
            IWiredTrigger trigger = trigger(calls, true);
            WiredStack movement =
                    new WiredStack(item(9901), trigger, List.of(), List.of(context -> altitude.set(2.02)));
            WiredStack animation = new WiredStack(
                    item(9902),
                    trigger,
                    List.of(context -> Math.abs(altitude.get() - 2.06) < 0.001),
                    List.of(context -> altitude.set(altitude.get() + 0.20)));
            WiredStackExecutor executor = executor(calls, 1000L);
            WiredEvent signal = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                    .signalPayloadExplicit(true)
                    .build();
            assertTrue(executor.executeSignalBatch(signal, () -> {
                executor.executeEvent(movementFirst ? movement : animation, signal, 1000L, false);
                return executor.executeEvent(movementFirst ? animation : movement, signal, 1000L, false);
            }));
            assertEquals(2.02, altitude.get(), 0.001);
        }
    }

    @Test
    void nativeReceiversDoNotSwitchTheTurnTwiceInOneSignal() {
        List<String> calls = new ArrayList<>();
        Room room = room(97);
        AtomicInteger turn = new AtomicInteger(0);
        IWiredTrigger trigger = trigger(calls, true);
        WiredStack first =
                new WiredStack(item(9701), trigger, List.of(ctx -> turn.get() == 0), List.of(ctx -> turn.set(1)));
        WiredStack second =
                new WiredStack(item(9702), trigger, List.of(ctx -> turn.get() == 1), List.of(ctx -> turn.set(0)));
        WiredStackExecutor executor = executor(calls, 1000L);
        for (int expected : new int[] {1, 0, 1, 0}) {
            WiredEvent signal = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                    .signalPayloadExplicit(true)
                    .build();
            assertTrue(executor.executeSignalBatch(signal, () -> {
                boolean a = executor.executeEvent(first, signal, 1000L, false);
                boolean b = executor.executeEvent(second, signal, 1000L, false);
                return a || b;
            }));
            assertEquals(expected, turn.get());
        }
    }

    @Test
    void signalBatchIsolatesReceiverFailuresAndDoesNotRetainDeferredWork() {
        List<String> calls = new ArrayList<>();
        Room room = room(98);
        AtomicInteger executions = new AtomicInteger();
        IWiredTrigger trigger = trigger(calls, true);
        WiredStack failing = new WiredStack(item(9801), trigger, List.of(), List.of(ctx -> {
            throw new IllegalStateException("receiver failure");
        }));
        WiredStack succeeding =
                new WiredStack(item(9802), trigger, List.of(), List.of(ctx -> executions.incrementAndGet()));
        WiredStackExecutor executor = executor(calls, 1000L);
        WiredEvent signal = event(room, WiredEvent.Type.SIGNAL_RECEIVED);
        assertTrue(executor.executeSignalBatch(signal, () -> {
            executor.executeEvent(failing, signal, 1000L, false);
            return executor.executeEvent(succeeding, signal, 1000L, false);
        }));
        assertEquals(1, executions.get());
        assertTrue(executor.executeEvent(succeeding, event(room), 1000L, false));
        assertEquals(2, executions.get());
    }

    @Test
    void eventExecutionPreservesTriggerSelectorConditionAndEffectOrder() {
        List<String> calls = new ArrayList<>();
        Room room = room(91);
        IWiredTrigger trigger = trigger(calls, true);
        IWiredCondition condition = context -> {
            calls.add("condition");
            return true;
        };
        IWiredEffect effect = context -> calls.add("effect");
        WiredStack stack = new WiredStack(item(9_101), trigger, List.of(condition), List.of(effect));
        WiredStackExecutor executor = executor(calls, 1_500L);

        assertTrue(executor.executeEvent(stack, event(room), 1_000L, false));
        assertEquals(
                List.of("trigger", "selectors", "filters", "finalize:1000", "targets", "condition", "effect"), calls);
    }

    @Test
    void directExecutionSkipsTriggerMatchingAndUsesInjectedClock() {
        List<String> calls = new ArrayList<>();
        Room room = room(92);
        IWiredTrigger trigger = trigger(calls, false);
        WiredStack stack = new WiredStack(item(9_201), trigger, List.of(), List.of(context -> calls.add("effect")));
        WiredStackExecutor executor = executor(calls, 2_345L);

        assertTrue(executor.executeDirect(stack, event(room), false));
        assertEquals(List.of("selectors", "filters", "finalize:2345", "targets", "effect"), calls);
    }

    @Test
    void triggerAndSelectorsAnimateWithoutAFinalEffect() {
        List<String> calls = new ArrayList<>();
        Room room = room(95);
        IWiredTrigger trigger = trigger(calls, true);
        InteractionWiredEffect selector = mock(InteractionWiredEffect.class);
        when(selector.isSelector()).thenReturn(true);
        WiredStack stack = new WiredStack(item(9_501), trigger, List.of(), List.of(selector));
        WiredStackExecutor executor = executor(calls, 5_000L);

        assertFalse(executor.executeEvent(stack, event(room), 5_000L, false));
        assertEquals(List.of("trigger", "selectors", "filters", "finalize:5000", "targets"), calls);
    }

    @Test
    void onlyIndividuallyPassingConditionsAnimateWhenTheStackFails() {
        List<String> calls = new ArrayList<>();
        Room room = room(96);
        InteractionWiredTrigger trigger = mock(InteractionWiredTrigger.class);
        InteractionWiredCondition passingCondition = mock(InteractionWiredCondition.class);
        InteractionWiredCondition failingCondition = mock(InteractionWiredCondition.class);
        when(trigger.matches(
                        org.mockito.ArgumentMatchers.any(HabboItem.class),
                        org.mockito.ArgumentMatchers.any(WiredEvent.class)))
                .thenReturn(true);
        when(passingCondition.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(failingCondition.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        WiredStack stack = new WiredStack(trigger, trigger, List.of(passingCondition, failingCondition), List.of());
        WiredStackExecutor executor = executor(calls, 6_000L);

        assertFalse(executor.executeEvent(stack, event(room), 6_000L, false));
        verify(trigger).activateBox(room, null, 6_000L);
        verify(passingCondition).activateBox(room, null, 6_000L);
        verify(failingCondition, never()).activateBox(room, null, 6_000L);
    }

    @Test
    void invalidActorRequirementStopsBeforeHookSideEffectsAndRecovers() {
        List<String> calls = new ArrayList<>();
        Room room = room(93);
        AtomicInteger matches = new AtomicInteger();
        IWiredTrigger trigger = new IWiredTrigger() {
            @Override
            public WiredEvent.Type listensTo() {
                return WiredEvent.Type.CUSTOM;
            }

            @Override
            public boolean matches(HabboItem triggerItem, WiredEvent event) {
                matches.incrementAndGet();
                return true;
            }

            @Override
            public boolean requiresActor() {
                return true;
            }
        };
        WiredStack stack = new WiredStack(item(9_301), trigger, List.of(), List.of(context -> calls.add("effect")));
        WiredStackExecutor executor = executor(calls, 3_000L);

        assertFalse(executor.executeEvent(stack, event(room), 3_000L, false));
        assertFalse(executor.executeDirect(stack, null, false));
        assertEquals(1, matches.get());
        assertEquals(List.of(), calls);
    }

    @Test
    void receiveSignalFeedbackUsesHistoricalRoomUsageBudget() {
        List<String> calls = new ArrayList<>();
        Room room = room(94);
        WiredStack stack =
                new WiredStack(item(9_401), trigger(calls, true), List.of(), List.of(context -> calls.add("effect")));
        WiredExecutionGuard executionGuard = executionGuard(4_000L, 10);
        WiredStackExecutor executor = executor(calls, 4_000L, executionGuard);
        WiredEvent signal = event(room, WiredEvent.Type.SIGNAL_RECEIVED);

        for (int index = 0; index < 20; index++) {
            executor.executeEvent(stack, signal, 4_000L, false);
        }

        long executedEffects = calls.stream().filter("effect"::equals).count();
        assertTrue(executedEffects > 0);
        assertTrue(executedEffects < 20);
        assertTrue(executionGuard.snapshot(room.getId()).getUsageCurrentWindow() > 10);
    }

    @Test
    void queuedSignalWaveCompletesEveryReceiverEvenWhenUsageLimitIsReachedMidWave() {
        List<String> calls = new ArrayList<>();
        Room room = room(101);
        var guard = executionGuard(4000L, 10);
        var executor = executor(calls, 4000L, guard);
        var stack = new WiredStack(item(10101), trigger(calls, true), List.of(), List.of(ctx -> calls.add("effect")));
        var signal = event(room, WiredEvent.Type.SIGNAL_RECEIVED);
        try (var scope = WiredSignalExecutionScope.begin()) {
            assertTrue(executor.executeSignalWave(() -> {
                for (int i = 0; i < 20; i++) assertTrue(executor.executeEvent(stack, signal, 4000L, false));
                return true;
            }));
        }
        assertEquals(20L, calls.stream().filter("effect"::equals).count());
        assertTrue(guard.snapshot(room.getId()).getUsageCurrentWindow() > 10);
        assertTrue(guard.signalExecutionDelay(room.getId()) > 0);
        assertFalse(WiredSignalExecutionScope.active());
    }

    private static WiredStackExecutor executor(List<String> calls, long now) {
        return executor(calls, now, 10_000);
    }

    private static WiredStackExecutor executor(List<String> calls, long now, int monitorUsageLimit) {
        return executor(calls, now, executionGuard(now, monitorUsageLimit));
    }

    private static WiredExecutionGuard executionGuard(long now, int monitorUsageLimit) {
        return new WiredExecutionGuard(
                new WiredExecutionGuard.Limits(
                        10, 100, 1_000L, 0L, 10_000, monitorUsageLimit, 100, 50, 150, 70, 5, 2, 60),
                () -> now,
                (room, eventType, count, limits, banned) -> {},
                (room, eventType, kind, depth, maximum) -> {});
    }

    private static WiredStackExecutor executor(List<String> calls, long now, WiredExecutionGuard executionGuard) {
        WiredConditionEvaluator conditionEvaluator = new WiredConditionEvaluator((room, format, arguments) -> {});
        WiredStackExecutor.Hooks hooks = new WiredStackExecutor.Hooks() {
            @Override
            public List<InteractionWiredEffect> executeSelectors(WiredStack stack, WiredContext context) {
                calls.add("selectors");
                return List.of();
            }

            @Override
            public void applySelectionFilterExtras(
                    WiredStack stack, WiredContext context, List<InteractionWiredEffect> executedSelectors) {
                calls.add("filters");
            }

            @Override
            public boolean selectorsHaveRequiredTargets(
                    List<InteractionWiredEffect> executedSelectors, WiredContext context) {
                calls.add("targets");
                return true;
            }

            @Override
            public void finalizeSelectors(
                    List<InteractionWiredEffect> executedSelectors, WiredContext context, long currentTime) {
                calls.add("finalize:" + currentTime);
            }

            @Override
            public void executeEffects(
                    WiredStack stack, List<IWiredEffect> effects, WiredContext context, long currentTime) {
                for (IWiredEffect effect : effects) {
                    effect.execute(context);
                }
            }
        };
        return new WiredStackExecutor(
                mock(WiredServices.class),
                100,
                conditionEvaluator,
                new WiredEffectPlanner(),
                executionGuard,
                hooks,
                () -> now,
                (room, format, arguments) -> {},
                new WiredStructuredDiagnostics(() -> false, (format, arguments) -> {}));
    }

    private static IWiredTrigger trigger(List<String> calls, boolean matches) {
        return new IWiredTrigger() {
            @Override
            public WiredEvent.Type listensTo() {
                return WiredEvent.Type.CUSTOM;
            }

            @Override
            public boolean matches(HabboItem triggerItem, WiredEvent event) {
                calls.add("trigger");
                return matches;
            }
        };
    }

    private static Room room(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(true);
        return room;
    }

    private static HabboItem item(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private static WiredEvent event(Room room) {
        return event(room, WiredEvent.Type.CUSTOM);
    }

    private static WiredEvent event(Room room, WiredEvent.Type type) {
        return WiredEvent.builder(type, room).createdAtMs(1_000L).build();
    }
}

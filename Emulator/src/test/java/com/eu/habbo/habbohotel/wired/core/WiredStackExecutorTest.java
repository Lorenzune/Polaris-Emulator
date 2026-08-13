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
        WiredStack stack = new WiredStack(
                trigger, trigger, List.of(passingCondition, failingCondition), List.of());
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
        WiredStack stack = new WiredStack(
                item(9_401), trigger(calls, true), List.of(), List.of(context -> calls.add("effect")));
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

    private static WiredStackExecutor executor(
            List<String> calls, long now, WiredExecutionGuard executionGuard) {
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
        return WiredEvent.builder(type, room)
                .createdAtMs(1_000L)
                .build();
    }
}

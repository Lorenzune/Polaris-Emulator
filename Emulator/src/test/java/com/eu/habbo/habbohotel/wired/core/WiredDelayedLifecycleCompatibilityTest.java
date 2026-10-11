package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.threading.ThreadPooling;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WiredDelayedLifecycleCompatibilityTest {

    private ThreadPooling previousThreading;
    private ThreadPooling scheduler;

    @BeforeEach
    void installCapturedScheduler() throws Exception {
        Field field = Emulator.class.getDeclaredField("threading");
        field.setAccessible(true);
        previousThreading = (ThreadPooling) field.get(null);
        scheduler = mock(ThreadPooling.class);
        when(scheduler.run(any(Runnable.class), anyLong())).thenReturn(mock(ScheduledFuture.class));
        field.set(null, scheduler);
    }

    @AfterEach
    void restoreScheduler() throws Exception {
        Field field = Emulator.class.getDeclaredField("threading");
        field.setAccessible(true);
        field.set(null, previousThreading);
    }

    @Test
    void delayUsesFiveHundredMillisecondTicksAndCompletesDiagnostics() {
        Room room = room(51, true, true);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, false));

        assertTrue(engine.handleEvent(event(room, null)));
        assertEquals(1, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());

        Runnable scheduled = scheduledTask();
        scheduled.run();

        assertEquals(1, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    @Test
    void roomUnloadDropsCapturedDelayedWork() {
        Room room = room(52, true, true);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, false));

        assertTrue(engine.handleEvent(event(room, null)));
        when(room.isLoaded()).thenReturn(false);
        scheduledTask().run();

        assertEquals(0, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    @Test
    void emptyLoadedRoomFinishesCapturedFurnitureWork() {
        Room room = room(53, true, true);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, false));

        assertTrue(engine.handleEvent(event(room, null)));
        when(room.getHabbos()).thenReturn(List.of());
        scheduledTask().run();

        // The last player can leave before an end-of-game furniture reset finishes.
        assertEquals(1, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    @Test
    void actorRequiredDelayedEffectIsDroppedAfterActorLeaves() {
        Room room = room(54, true, true);
        RoomUnit actor = mock(RoomUnit.class);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, true));

        when(room.getRoomUnits()).thenReturn(Set.of(actor));
        assertTrue(engine.handleEvent(event(room, actor)));
        when(room.getRoomUnits()).thenReturn(Set.of());
        scheduledTask().run();

        assertEquals(0, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    @Test
    void actorIndependentDelayedEffectExecutesWithoutExposingDepartedActorOrTarget() {
        Room room = room(55, true, true);
        RoomUnit actor = mock(RoomUnit.class);
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger visibleActors = new AtomicInteger();
        IWiredEffect effect = new IWiredEffect() {
            @Override
            public void execute(WiredContext context) {
                executions.incrementAndGet();
                visibleActors.addAndGet(context.actor().isPresent() ? 1 : 0);
                visibleActors.addAndGet(context.targets().users().contains(actor) ? 1 : 0);
            }

            @Override
            public int getDelay() {
                return 1;
            }
        };
        WiredEngine engine = engine(room, effect);

        when(room.getRoomUnits()).thenReturn(Set.of(actor));
        assertTrue(engine.handleEvent(event(room, actor)));
        when(room.getRoomUnits()).thenReturn(Set.of());
        scheduledTask().run();

        assertEquals(1, executions.get());
        assertEquals(0, visibleActors.get());
    }

    @Test
    void schedulerRejectionReleasesDiagnosticsReservation() {
        Room room = room(56, true, true);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, false));
        when(scheduler.run(any(Runnable.class), anyLong())).thenReturn(null);

        assertTrue(engine.handleEvent(event(room, null)));

        assertEquals(0, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    private Runnable scheduledTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).run(task.capture(), longThat(delay -> delay >= 0L && delay <= 500L));
        return task.getValue();
    }

    @Test
    void calledStackRunsSelectorsBeforeEffectsAndRetainsSelectionAcrossDelay() {
        Room room = room(57, true, true);
        HabboItem target = mock(HabboItem.class);
        when(target.getId()).thenReturn(9001);
        when(target.getRoomId()).thenReturn(57);
        when(room.getHabboItem(9001)).thenReturn(target);
        AtomicInteger immediate = new AtomicInteger();
        AtomicInteger delayed = new AtomicInteger();
        IWiredEffect selector = new IWiredEffect() {
            public void execute(WiredContext context) {
                context.targets().setItems(List.of(target));
            }

            public boolean isSelector() {
                return true;
            }
        };
        IWiredEffect first = context -> {
            assertEquals(Set.of(target), context.targets().items());
            immediate.incrementAndGet();
        };
        IWiredEffect later = new IWiredEffect() {
            public void execute(WiredContext context) {
                assertEquals(Set.of(target), context.targets().items());
                delayed.incrementAndGet();
            }

            public int getDelay() {
                return 1;
            }
        };
        WiredEngine engine =
                new WiredEngine(mock(WiredServices.class), (candidate, type) -> List.of(), 100, ignoredId -> room);
        engine.executeCalledEffects(null, List.of(first, later, selector), event(room, null));
        assertEquals(1, immediate.get());
        assertEquals(0, delayed.get());
        scheduledTask().run();
        assertEquals(1, delayed.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    @Test
    void calledStackHonorsRandomExtraInsteadOfExecutingBothTurnInitializers() {
        Room room = room(58, true, true);
        when(room.getRoomSpecialTypes()).thenReturn(mock(com.eu.habbo.habbohotel.rooms.RoomSpecialTypes.class));
        HabboItem anchor = mock(HabboItem.class);
        var extra = mock(com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRandom.class);
        when(room.getRoomSpecialTypes().getExtras(0, 0)).thenReturn(Set.of(extra));
        when(extra.selectWiredEffects(any()))
                .thenAnswer(invocation -> List.of(((List<IWiredEffect>) invocation.getArgument(0)).getFirst()));
        AtomicInteger first = new AtomicInteger(), second = new AtomicInteger();
        WiredEngine engine = new WiredEngine(mock(WiredServices.class), (candidate, type) -> List.of(), 100);
        engine.executeCalledEffects(
                anchor, List.of(ctx -> first.incrementAndGet(), ctx -> second.incrementAndGet()), event(room, null));
        assertEquals(1, first.get());
        assertEquals(0, second.get());
    }

    private static WiredEngine engine(Room room, IWiredEffect effect) {
        HabboItem triggerItem = mock(HabboItem.class);
        when(triggerItem.getId()).thenReturn(8001);
        IWiredTrigger trigger = new IWiredTrigger() {
            @Override
            public WiredEvent.Type listensTo() {
                return WiredEvent.Type.CUSTOM;
            }

            @Override
            public boolean matches(HabboItem item, WiredEvent event) {
                return true;
            }
        };
        WiredStack stack = new WiredStack(triggerItem, trigger, List.of(), List.of(effect));
        return new WiredEngine(mock(WiredServices.class), (candidate, type) -> List.of(stack), 100, ignoredId -> room);
    }

    private static IWiredEffect delayedEffect(AtomicInteger executions, boolean requiresActor) {
        return new IWiredEffect() {
            @Override
            public void execute(WiredContext context) {
                executions.incrementAndGet();
            }

            @Override
            public int getDelay() {
                return 1;
            }

            @Override
            public boolean requiresActor() {
                return requiresActor;
            }
        };
    }

    private static Room room(int id, boolean loaded, boolean occupied) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(loaded);
        when(room.getLifecycleGeneration()).thenReturn(1L);
        when(room.getHabbos()).thenReturn(occupied ? List.of(mock(Habbo.class)) : List.of());
        return room;
    }

    private static WiredEvent event(Room room, RoomUnit actor) {
        return WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .actor(actor)
                .createdAtMs(System.currentTimeMillis())
                .build();
    }
}

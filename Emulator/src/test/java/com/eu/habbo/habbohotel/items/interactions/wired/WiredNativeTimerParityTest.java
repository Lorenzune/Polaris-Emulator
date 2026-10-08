package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionCounterTimeMatches;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionLessTimeElapsed;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMoreTimeElapsed;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectAdjustClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectControlClock;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAtSetTime;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerClockCounter;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeater;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeaterLong;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeaterShort;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import com.eu.habbo.threading.runnables.games.GameUpCounter;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WiredNativeTimerParityTest {
    private static WiredSettings nativeSettings(int[] ints, int... sources) {
        var settings = new WiredSettings(ints, "", new int[0], 0, 0);
        settings.setNativeVariableFormat(true);
        settings.setFurniSourceTypes(sources);
        return settings;
    }

    private static WiredContext context(Room room) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
    }

    @Test
    void nativeClockFieldsPreserveOddHalfSecondsAndSeparateSources() {
        var adjust = new WiredEffectAdjustClock(1, 1, mock(Item.class), "", 0, 0);
        var control = new WiredEffectControlClock(2, 1, mock(Item.class), "", 0, 0);
        var trigger = new WiredTriggerClockCounter(3, 1, mock(Item.class), "", 0, 0);
        var condition = new WiredConditionCounterTimeMatches(4, 1, mock(Item.class), "", 0, 0);
        assertArrayEquals(
                new int[] {2, 201, 99, 119},
                WiredNativeTimerSettings.normalize(adjust, nativeSettings(new int[] {59, 99, 1, 2}, 201))
                        .getIntParams());
        assertArrayEquals(
                new int[] {4, 200},
                WiredNativeTimerSettings.normalize(control, nativeSettings(new int[] {4}, 200))
                        .getIntParams());
        assertArrayEquals(
                new int[] {99, 119, 200},
                WiredNativeTimerSettings.normalize(trigger, nativeSettings(new int[] {59, 99, 1}, 200))
                        .getIntParams());
        var any = nativeSettings(new int[] {59, 99, 1, 1}, 100);
        any.setQuantifier(1);
        assertArrayEquals(
                new int[] {1, 99, 119, 100, 1},
                WiredNativeTimerSettings.normalize(condition, any).getIntParams());
        assertThrows(
                IllegalArgumentException.class,
                () -> WiredNativeTimerSettings.normalize(trigger, nativeSettings(new int[] {0, 0, 0}, 201)));
        assertThrows(
                IllegalArgumentException.class,
                () -> WiredNativeTimerSettings.normalize(adjust, nativeSettings(new int[] {60, 0, 0, 0}, 100)));
    }

    @Test
    void nativeTimerUnitsSaveReloadAndRejectOutOfRangeWithoutChangingState() throws Exception {
        var shortTimer = new WiredTriggerRepeaterShort(1, 1, mock(Item.class), "", 0, 0);
        var normal = new WiredTriggerRepeater(2, 1, mock(Item.class), "", 0, 0);
        var longTimer = new WiredTriggerRepeaterLong(3, 1, mock(Item.class), "", 0, 0);
        var once = new WiredTriggerAtSetTime(4, 1, mock(Item.class), "", 0, 0);
        assertEquals(50, shortTimer.getRepeatTimeMs());
        assertTrue(shortTimer.saveData(nativeSettings(new int[] {3})));
        assertEquals(150, shortTimer.getRepeatTimeMs());
        assertTrue(normal.saveData(nativeSettings(new int[] {3})));
        assertTrue(longTimer.saveData(nativeSettings(new int[] {3})));
        assertTrue(once.saveData(nativeSettings(new int[] {3})));
        assertTrue(normal.getWiredData().contains("1500"));
        assertTrue(longTimer.getWiredData().contains("15000"));
        assertEquals(1500, once.executeTime);
        String before = shortTimer.getWiredData();
        assertThrows(IllegalArgumentException.class, () -> shortTimer.saveData(nativeSettings(new int[] {11})));
        assertEquals(before, shortTimer.getWiredData());
        var data = mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn(before);
        shortTimer.onPickUp();
        shortTimer.loadWiredData(data, null);
        assertEquals(150, shortTimer.getRepeatTimeMs());
        when(data.getString("wired_data")).thenReturn("{broken");
        shortTimer.loadWiredData(data, null);
        assertEquals(50, shortTimer.getRepeatTimeMs());
        when(data.getString("wired_data")).thenReturn("{\"repeatTime\":1500}");
        normal.loadWiredData(data, null);
        assertTrue(normal.getWiredData().contains("1500"));
    }

    @Test
    void nativeElapsedConditionsTranslateTheSwfOffsetAndCompareHalfSeconds() {
        var room = mock(Room.class);
        when(room.getLastTimerResetMillis()).thenAnswer(ignored -> System.currentTimeMillis() - 750);
        var more = new WiredConditionMoreTimeElapsed(1, 1, mock(Item.class), "", 0, 0);
        var less = new WiredConditionLessTimeElapsed(2, 1, mock(Item.class), "", 0, 0);
        assertTrue(more.saveData(nativeSettings(new int[] {2})));
        assertTrue(more.evaluate(context(room)));
        assertTrue(less.saveData(nativeSettings(new int[] {3})));
        assertTrue(less.evaluate(context(room)));
        assertFalse(less.saveData(new WiredSettings(new int[0], "", new int[0], 0)));
    }

    @Test
    void triggerOneShotAndRepeaterRestartAfterResetRatherThanFollowingServerPhase() {
        var room = mock(Room.class);
        when(room.isLoaded()).thenReturn(true);
        var tickService = mock(WiredTickService.class);
        var tick = new AtomicLong(103);
        when(tickService.getTickCount()).thenAnswer(ignored -> tick.get());
        var repeater = new WiredTriggerRepeater(1, 1, mock(Item.class), "", 0, 0);
        repeater.setRoomId(8);
        var once = new WiredTriggerAtSetTime(2, 1, mock(Item.class), "", 0, 0);
        once.setRoomId(8);
        try (var ticks = mockStatic(WiredTickService.class);
                var manager = mockStatic(WiredManager.class)) {
            ticks.when(WiredTickService::getInstance).thenReturn(tickService);
            manager.when(() -> WiredManager.isTriggerExecutionAllowed(
                            org.mockito.ArgumentMatchers.eq(room),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyLong()))
                    .thenReturn(true);
            repeater.onRegistered(room, 0);
            once.onRegistered(room, 0);
            repeater.onWiredTick(room, 110, 50);
            once.onWiredTick(room, 110, 50);
            manager.verify(() -> WiredManager.triggerTimerRepeat(room, repeater), times(0));
            manager.verify(() -> WiredManager.triggerTimerTick(room, once), times(0));
            repeater.onWiredTick(room, 113, 50);
            once.onWiredTick(room, 113, 50);
            once.onWiredTick(room, 123, 50);
            manager.verify(() -> WiredManager.triggerTimerTick(room, once), times(1));
            tick.set(115);
            repeater.resetTimer();
            once.resetTimer();
            repeater.onWiredTick(room, 123, 50);
            once.onWiredTick(room, 123, 50);
            manager.verify(() -> WiredManager.triggerTimerRepeat(room, repeater), times(1));
            repeater.onWiredTick(room, 125, 50);
            once.onWiredTick(room, 125, 50);
            manager.verify(() -> WiredManager.triggerTimerRepeat(room, repeater), times(2));
            manager.verify(() -> WiredManager.triggerTimerTick(room, once), times(2));
        }
    }

    @Test
    void clockExecutionAndReloadUseTheSelectedHalfSecondAndDoNotCommitRejectedSelections() throws Exception {
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var room = mock(Room.class);
        when(environment.getRoomManager().getRoom(8)).thenReturn(room);
        var config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(20);
        var counter = mock(InteractionGameUpCounter.class);
        when(counter.getId()).thenReturn(10);
        when(counter.getRoomId()).thenReturn(8);
        when(room.getHabboItem(10)).thenReturn(counter);
        var adjust = new WiredEffectAdjustClock(1, 1, mock(Item.class), "", 0, 0);
        adjust.setRoomId(8);
        var trigger = new WiredTriggerClockCounter(2, 1, mock(Item.class), "", 0, 0);
        trigger.setRoomId(8);
        var condition = new WiredConditionCounterTimeMatches(3, 1, mock(Item.class), "", 0, 0);
        condition.setRoomId(8);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            var settings = nativeSettings(new int[] {3, 2, 1, 2}, 100);
            settings.setFurniIds(new int[] {10});
            assertTrue(adjust.saveData(settings, null));
            adjust.execute(context(room));
            verify(counter).adjustCounter(room, 2, 2, 7);
            var data = mock(ResultSet.class);
            String savedAdjust = adjust.getWiredData();
            when(data.getString("wired_data")).thenReturn(savedAdjust);
            adjust.onPickUp();
            adjust.loadWiredData(data, room);
            adjust.execute(context(room));
            verify(counter, times(2)).adjustCounter(room, 2, 2, 7);
            var clockSettings = nativeSettings(new int[] {3, 2, 1}, 100);
            clockSettings.setFurniIds(new int[] {10});
            assertTrue(trigger.saveData(clockSettings));
            when(counter.getCurrentTimeInMs()).thenReturn(123500);
            assertTrue(trigger.matches(
                    counter,
                    WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                            .sourceItem(counter)
                            .build()));
            var compareSettings = nativeSettings(new int[] {3, 2, 1, 1}, 100);
            compareSettings.setFurniIds(new int[] {10});
            assertTrue(condition.saveData(compareSettings));
            assertTrue(condition.evaluate(context(room)));
            String previous = condition.getWiredData();
            compareSettings.setFurniIds(new int[] {404});
            assertFalse(condition.saveData(compareSettings));
            assertEquals(previous, condition.getWiredData());
        }
    }

    @Test
    void counterEmitsAnEventAtHalfSeconds() {
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var room = mock(Room.class);
        when(environment.getRoomManager().getRoom(8)).thenReturn(room);
        var counter = mock(InteractionGameUpCounter.class);
        when(counter.getRoomId()).thenReturn(8);
        when(counter.isRunning()).thenReturn(true);
        when(counter.getNextTickDelayMs()).thenReturn(500L);
        when(counter.getCurrentTimeInMs()).thenReturn(500);
        try (var emulator = mockStatic(Emulator.class);
                var manager = mockStatic(WiredManager.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            new GameUpCounter(counter).run();
            verify(counter).advanceCounterInMs(500);
            manager.verify(() -> WiredManager.triggerClockCounter(room, counter));
        }
    }

    @Test
    void controlClockDispatchesAllFiveCommandsToLiveSelectedCounters() throws Exception {
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        var room = mock(Room.class);
        when(environment.getRoomManager().getRoom(8)).thenReturn(room);
        var config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(20);
        var counter = mock(InteractionGameUpCounter.class);
        when(counter.getId()).thenReturn(10);
        when(counter.getRoomId()).thenReturn(8);
        when(room.getHabboItem(10)).thenReturn(counter);
        var control = new WiredEffectControlClock(1, 1, mock(Item.class), "", 0, 0);
        control.setRoomId(8);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            for (int action = 0; action < 5; action++) {
                var settings = nativeSettings(new int[] {action}, 100);
                settings.setFurniIds(new int[] {10});
                assertTrue(control.saveData(settings, null));
                control.execute(context(room));
            }
            verify(counter).restartFromZero(room);
            verify(counter).stopCounter(room);
            verify(counter).resetCounter(room);
            verify(counter).pauseCounter(room);
            verify(counter).resumeCounter(room);
        }
    }
}

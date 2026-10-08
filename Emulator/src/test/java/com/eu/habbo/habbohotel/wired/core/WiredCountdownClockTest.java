package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerClockCounter;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.wired.migrate.WiredEvents;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.threading.runnables.games.GameTimer;
import org.junit.jupiter.api.Test;

class WiredCountdownClockTest {
    private static Item timerBase() {
        var base = mock(Item.class);
        when(base.getCustomParams()).thenReturn("30,60,120");
        return base;
    }

    static class Countdown extends InteractionGameTimer {
        Countdown() {
            super(674, 16, timerBase(), "", 0, 0);
            baseTime = 5;
            timeNow = 5;
        }
    }

    @Test
    void countdownKeepsSecondDisplayAndReachesFourAndAHalfSecondsExactly() throws Exception {
        var clock = new Countdown();
        var room = mock(Room.class);
        when(room.getHabboItem(674)).thenReturn(clock);
        var trigger = new WiredTriggerClockCounter(113, 16, mock(Item.class), "", 0, 0);
        var data = mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn(
                "{\"minutes\":0,\"halfSecondSteps\":9,\"furniSource\":100,\"itemIds\":[674]}");
        trigger.loadWiredData(data, room);
        assertTrue(trigger.getWiredData().contains("674"));
        WiredEvent event = WiredEvents.clockCounter(room, clock);
        assertFalse(trigger.matches(trigger, event));
        for (int pulse = 1; pulse <= 10; pulse++) {
            assertEquals(pulse % 2 == 0, clock.advanceClockHalfSecond());
            assertEquals(5 - pulse / 2, clock.getTimeNow());
            assertEquals(5000 - pulse * 500, clock.getClockTimeInMs());
            assertEquals(pulse == 1, trigger.matches(trigger, event));
        }
    }

    @Test
    void runnableDispatchesHalfSecondPulsesButUpdatesDisplayOnlyOnWholeSeconds() {
        var clock = new Countdown();
        clock.setRoomId(268);
        clock.setRunning(true);
        var room = mock(Room.class);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(268)).thenReturn(room);
        var threads = mock(ThreadPooling.class);
        List<Integer> pulses = new ArrayList<>();
        try (var emulator = mockStatic(Emulator.class); var wired = mockStatic(WiredManager.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getThreading).thenReturn(threads);
            wired.when(() -> WiredManager.triggerClockCounter(room, clock)).thenAnswer(i -> {
                pulses.add(clock.getClockTimeInMs());
                return false;
            });
            var runnable = new GameTimer(clock);
            runnable.run();
            verify(room, never()).updateItem(clock);
            runnable.run();
            verify(room).updateItem(clock);
            assertEquals(List.of(4500,4000), pulses);
            assertEquals(4, clock.getTimeNow());
            clock.pauseTimer(room);
            runnable.run();
            assertEquals(List.of(4500,4000), pulses);
        }
    }

    @Test
    void zeroClockTriggerFiresAtEndRatherThanAtStart() throws Exception {
        var clock = new Countdown();
        var room = mock(Room.class);
        when(room.getHabboItem(674)).thenReturn(clock);
        var trigger = new WiredTriggerClockCounter(814, 16, mock(Item.class), "", 0, 0);
        var data = mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn(
                "{\"minutes\":0,\"halfSecondSteps\":0,\"furniSource\":100,\"itemIds\":[674]}");
        trigger.loadWiredData(data, room);
        var event = WiredEvents.clockCounter(room, clock);
        assertFalse(trigger.matches(trigger, event));
        for (int pulse = 1; pulse <= 10; pulse++) {
            clock.advanceClockHalfSecond();
            assertEquals(pulse == 10, trigger.matches(trigger, event));
        }
    }

    @Test
    void nativeClockSelectionAcceptsGameTimerAndRoundTrips() throws Exception {
        var clock = new Countdown();
        var room = mock(Room.class);
        when(room.getHabboItem(674)).thenReturn(clock);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(268)).thenReturn(room);
        var configuration = mock(ConfigurationManager.class);
        when(configuration.getInt(anyString())).thenReturn(50);
        var trigger = new WiredTriggerClockCounter(113, 16, mock(Item.class), "", 0, 0);
        trigger.setRoomId(268);
        var settings = new WiredSettings(new int[]{4,0,1}, "", new int[]{674}, 0, 0);
        settings.setNativeVariableFormat(true);
        settings.setFurniSourceTypes(new int[]{100});
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(configuration);
            assertTrue(trigger.saveData(settings));
            String saved = trigger.getWiredData();
            var data = mock(ResultSet.class);
            when(data.getString("wired_data")).thenReturn(saved);
            trigger.loadWiredData(data, room);
            assertEquals(saved, trigger.getWiredData());
        }
    }
}

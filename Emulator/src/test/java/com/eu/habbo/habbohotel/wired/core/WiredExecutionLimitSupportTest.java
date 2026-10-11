package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraAnimationTime;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WiredExecutionLimitSupportTest {
    @ParameterizedTest
    @MethodSource("executionStacks")
    void allAccessoriesContributeTheirConfiguredExecutionSlots(int[] quotas, int expectedTotal) {
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>();
        for (int i = 0; i < quotas.length; i++) {
            extras.add(limit(i + 1, quotas[i], 1000));
        }
        Fixture fixture = fixture(extras);
        WiredExecutionLimitSupport limits = WiredExecutionLimitSupport.forStack(fixture.room, fixture.trigger);
        assertEquals(expectedTotal, limits.maxExecutions());
        for (int i = 0; i < expectedTotal; i++) {
            assertTrue(limits.canExecuteAt(10000));
            assertTrue(limits.tryAcquireExecutionSlot(10000));
        }
        assertFalse(limits.canExecuteAt(10000));
        assertFalse(limits.tryAcquireExecutionSlot(10000));
        assertFalse(limits.canExecuteAt(10999));
        assertTrue(limits.canExecuteAt(11000));
        assertTrue(limits.tryAcquireExecutionSlot(11000));
    }

    private static Stream<Arguments> executionStacks() {
        return Stream.of(
                Arguments.of(new int[] {100}, 100),
                Arguments.of(new int[] {100, 100, 100, 100, 100}, 500),
                Arguments.of(new int[] {7, 13, 80}, 100),
                Arguments.of(new int[] {80, 13, 7}, 100),
                Arguments.of(IntStream.range(0, 1000).map(i -> 1 + i % 2).toArray(), 1500));
    }

    @Test
    void triggerAdmissionUsesAvailableSlotsFromOtherAccessoriesWithoutConsumingThem() {
        var first = limit(1, 2, 1000);
        var second = limit(2, 3, 1000);
        assertTrue(first.tryAcquireExecutionSlot(10000));
        assertTrue(first.tryAcquireExecutionSlot(10000));
        Fixture fixture = fixture(new LinkedHashSet<>(java.util.List.of(first, second)));
        for (int i = 0; i < 10; i++) {
            assertTrue(WiredManager.isTriggerExecutionAllowed(fixture.room, fixture.trigger, 10000));
        }
        WiredExecutionLimitSupport limits = WiredExecutionLimitSupport.forStack(fixture.room, fixture.trigger);
        for (int i = 0; i < 3; i++) {
            assertTrue(limits.tryAcquireExecutionSlot(10000));
        }
        assertFalse(WiredManager.isTriggerExecutionAllowed(fixture.room, fixture.trigger, 10000));
    }

    @Test
    void eachAccessoryKeepsItsOwnTimeWindow() {
        Fixture fixture = fixture(new LinkedHashSet<>(java.util.List.of(limit(1, 1, 500), limit(2, 1, 1000))));
        WiredExecutionLimitSupport limits = WiredExecutionLimitSupport.forStack(fixture.room, fixture.trigger);
        assertTrue(limits.tryAcquireExecutionSlot(10000));
        assertTrue(limits.tryAcquireExecutionSlot(10000));
        assertFalse(limits.tryAcquireExecutionSlot(10499));
        assertTrue(limits.tryAcquireExecutionSlot(10500));
        assertFalse(limits.tryAcquireExecutionSlot(10500));
        assertFalse(limits.tryAcquireExecutionSlot(10999));
        assertTrue(limits.tryAcquireExecutionSlot(11000));
        assertTrue(limits.tryAcquireExecutionSlot(11000));
        assertFalse(limits.tryAcquireExecutionSlot(11000));
    }

    @Test
    void unrelatedAccessoriesDoNotIntroduceAnExecutionLimit() {
        Fixture fixture = fixture(Set.of(mock(WiredExtraAnimationTime.class)));
        WiredExecutionLimitSupport limits = WiredExecutionLimitSupport.forStack(fixture.room, fixture.trigger);
        for (int i = 0; i < 200; i++) {
            assertTrue(limits.canExecuteAt(10000));
            assertTrue(limits.tryAcquireExecutionSlot(10000));
        }
    }

    private static WiredExtraExecutionLimit limit(int id, int quota, int windowMs) {
        var limit = new WiredExtraExecutionLimit(id, 1, null, "", 0, 0);
        assertTrue(limit.saveData(new WiredSettings(new int[] {quota, windowMs}, "", new int[0], 0), null));
        return limit;
    }

    private static Fixture fixture(Set<InteractionWiredExtra> extras) {
        Room room = mock(Room.class, RETURNS_DEEP_STUBS);
        HabboItem trigger = mock(HabboItem.class);
        when(trigger.getX()).thenReturn((short) 8);
        when(trigger.getY()).thenReturn((short) 9);
        when(room.getRoomSpecialTypes().getExtras((short) 8, (short) 9)).thenReturn(extras);
        return new Fixture(room, trigger);
    }

    private record Fixture(Room room, HabboItem trigger) {}
}

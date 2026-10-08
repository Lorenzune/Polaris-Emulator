package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredFilterRuntimeParityTest {
    @Test
    void selectorLimitDoesNotDropManuallySelectedLightsOrForwardedSignalTargets() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var trigger = mock(HabboItem.class);
        var first = mock(HabboItem.class);
        var second = mock(HabboItem.class);
        when(first.getId()).thenReturn(121);
        when(second.getId()).thenReturn(703);
        var extra = new WiredExtraFilterFurni(1, 2, mock(Item.class), "", 0, 0);
        extra.saveData(new WiredSettings(new int[] {1}, "", new int[0], 0), null);
        when(room.getRoomSpecialTypes().getExtras((short) 0, (short) 0)).thenReturn(Set.of(extra));
        var lights = List.of(first, second);
        var ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                        .signalPayloadExplicit(true).signalSourceItems(lights).build(),
                trigger, mock(WiredServices.class), new WiredState(100));
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getRandom).thenReturn(new Random(0));
            assertEquals(lights, WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SELECTED, lights));
            assertEquals(lights, WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SELECTED_SECONDARY,
                    List.of(), lights));
            assertEquals(lights, WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SIGNAL, null));
            ctx.targets().setItems(lights);
            WiredSelectionFilterSupport.applySelectorFilters(room, trigger, ctx);
            assertEquals(1, WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SELECTOR, null).size());
            assertEquals(lights, WiredSourceUtil.resolveItems(ctx, WiredSourceUtil.SOURCE_SELECTED, lights));
        }
    }

    @Test
    void quantityFromSelectorTargetsDoesNotReenterItsOwnFilter() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(10)).thenReturn(room);
        var first = mock(HabboItem.class);
        when(first.getId()).thenReturn(1);
        var second = mock(HabboItem.class);
        when(second.getId()).thenReturn(2);
        var trigger = mock(HabboItem.class);
        var extra = new WiredExtraFilterFurni(1, 2, mock(Item.class), "", 0, 0);
        extra.setRoomId(10);
        when(room.getRoomSpecialTypes().getExtras((short) 0, (short) 0)).thenReturn(Set.of(extra));
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getRandom).thenReturn(new Random(0));
            var settings = new WiredSettings(new int[] {1, 1, 0}, "", new int[0], 0);
            settings.setNativeVariableFormat(true);
            settings.setVariableTokens(new String[] {"internal:@id"});
            settings.setFurniSourceTypes(new int[] {200});
            extra.saveData(settings, null);
            var ctx = new WiredContext(
                    WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room).build(),
                    trigger,
                    mock(WiredServices.class),
                    new WiredState(100));
            ctx.targets().setItems(List.of(first, second));
            assertTimeout(
                    Duration.ofSeconds(2),
                    () -> assertEquals(
                            1,
                            WiredSelectionFilterSupport.filterItems(room, trigger, ctx, List.of(first, second))
                                    .size()));
            ctx.targets().setItems(List.of());
            assertEquals(
                    List.of(), WiredSelectionFilterSupport.filterItems(room, trigger, ctx, List.of(first, second)));
            assertEquals(List.of(), WiredSelectionFilterSupport.filterItems(room, trigger, ctx, List.of()));
        }
    }
}

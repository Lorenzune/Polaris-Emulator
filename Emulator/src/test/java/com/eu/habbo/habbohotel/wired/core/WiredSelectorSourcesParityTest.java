package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectRemoteSelector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredSelectorSourcesParityTest {
    @Test
    void independentSelectorsKeepBothGroupsBeforeTheNeighborhoodFilter() {
        var room = mock(Room.class);
        var trigger = mock(HabboItem.class);
        var ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room).sourceItem(trigger).build(),
                trigger, mock(WiredServices.class), new WiredState(100));
        var sphere = mock(HabboItem.class);
        var wildCard = mock(HabboItem.class);
        var outside = mock(HabboItem.class);
        var color = itemSelector(Set.of(sphere, outside), false);
        var bonus = itemSelector(Set.of(wildCard), false);
        WiredExecutionScope.execute(color, ctx);
        WiredExecutionScope.execute(bonus, ctx);
        assertEquals(Set.of(sphere, wildCard, outside), ctx.targets().items());
        var neighborhood = mock(InteractionWiredEffect.class);
        when(neighborhood.isSelector()).thenReturn(true);
        when(neighborhood.usesExistingSelectorTargets()).thenReturn(true);
        doAnswer(invocation -> {
            WiredContext current = invocation.getArgument(0);
            var matches = new java.util.LinkedHashSet<>(current.targets().items());
            matches.retainAll(Set.of(sphere, wildCard));
            current.targets().setItems(matches);
            return null;
        }).when(neighborhood).execute(any(WiredContext.class));
        WiredExecutionScope.execute(neighborhood, ctx);
        assertEquals(Set.of(sphere, wildCard), ctx.targets().items());
    }

    @Test
    void emptyIndependentSelectionKeepsEarlierResultsWithoutAddingTheTrigger() {
        var trigger = mock(HabboItem.class);
        var ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, mock(Room.class)).sourceItem(trigger).build(),
                trigger, mock(WiredServices.class), new WiredState(100));
        WiredExecutionScope.execute(itemSelector(Set.of(), false), ctx);
        assertTrue(ctx.targets().items().isEmpty());
        var sphere = mock(HabboItem.class);
        WiredExecutionScope.execute(itemSelector(Set.of(sphere), false), ctx);
        WiredExecutionScope.execute(itemSelector(Set.of(), false), ctx);
        assertEquals(Set.of(sphere), ctx.targets().items());
        WiredExecutionScope.execute(itemSelector(Set.of(), true), ctx);
        assertTrue(ctx.targets().items().isEmpty());
    }

    @Test
    void remoteStackUsesTheUnionOfItsIndependentSelectors() {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var stack = mock(HabboItem.class);
        var sphere = mock(HabboItem.class);
        var bonus = mock(HabboItem.class);
        var first = itemSelector(Set.of(sphere), false);
        var second = itemSelector(Set.of(bonus), false);
        when(second.getZ()).thenReturn(1D);
        when(room.getRoomSpecialTypes().getEffects((short) 0, (short) 0)).thenReturn(Set.of(first, second));
        assertEquals(Set.of(sphere, bonus), WiredSourceUtil.resolveRemoteSelectors(context(room, stack), stack).items());
    }

    private static InteractionWiredEffect itemSelector(Set<HabboItem> items, boolean deferred) {
        var selector = mock(InteractionWiredEffect.class);
        when(selector.isSelector()).thenReturn(true);
        when(selector.usesExistingSelectorTargets()).thenReturn(deferred);
        doAnswer(invocation -> {
            ((WiredContext) invocation.getArgument(0)).targets().setItems(items);
            return null;
        }).when(selector).execute(any(WiredContext.class));
        return selector;
    }

    @Test
    void remoteStacksCombineFurnitureAndUsersByUnionOrIntersectionAfterReload() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var leftStack = mock(InteractionWiredEffect.class);
        var rightStack = mock(InteractionWiredEffect.class);
        when(rightStack.getX()).thenReturn((short) 1);
        when(room.getHabboItem(10)).thenReturn(leftStack);
        when(room.getHabboItem(20)).thenReturn(rightStack);
        var leftSelector = mock(InteractionWiredEffect.class);
        var rightSelector = mock(InteractionWiredEffect.class);
        when(leftSelector.isSelector()).thenReturn(true);
        when(rightSelector.isSelector()).thenReturn(true);
        when(room.getRoomSpecialTypes().getEffects((short) 0, (short) 0)).thenReturn(Set.of(leftSelector));
        when(room.getRoomSpecialTypes().getEffects((short) 1, (short) 0)).thenReturn(Set.of(rightSelector));
        var common = mock(HabboItem.class);
        var left = mock(HabboItem.class);
        var right = mock(HabboItem.class);
        var sharedUser = mock(RoomUnit.class);
        var otherUser = mock(RoomUnit.class);
        doAnswer(invocation -> {
                    WiredContext child = invocation.getArgument(0);
                    child.targets().setItems(Set.of(common, left));
                    child.targets().setUsers(Set.of(sharedUser));
                    return null;
                })
                .when(leftSelector)
                .execute(any(WiredContext.class));
        doAnswer(invocation -> {
                    WiredContext child = invocation.getArgument(0);
                    child.targets().setItems(Set.of(common, right));
                    child.targets().setUsers(Set.of(sharedUser, otherUser));
                    return null;
                })
                .when(rightSelector)
                .execute(any(WiredContext.class));
        var rows = mock(ResultSet.class);
        for (int mode : new int[] {0, 1}) {
            var remote = new WiredEffectRemoteSelector(1, 2, mock(Item.class), "", 0, 0);
            var settings = new com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings(
                    new int[] {mode, 0}, "", new int[] {10, 20}, 0);
            settings.setNativeVariableFormat(true);
            remote.saveData(settings, null);
            when(rows.getString("wired_data")).thenReturn(remote.getWiredData());
            var reloaded = new WiredEffectRemoteSelector(2, 2, mock(Item.class), "", 0, 0);
            reloaded.loadWiredData(rows, room);
            var ctx = context(room, null);
            reloaded.execute(ctx);
            assertEquals(
                    mode == 0 ? Set.of(common, left, right) : Set.of(common),
                    ctx.targets().items());
            assertEquals(
                    mode == 0 ? Set.of(sharedUser, otherUser) : Set.of(sharedUser),
                    ctx.targets().users());
        }
    }

    private static WiredContext context(Room room, HabboItem stack) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room).build(),
                stack,
                mock(WiredServices.class),
                new WiredState(100));
    }

    @Test
    void clickedUserAndBothSelectionsDoNotFallBackToTheActorOrPrimaryGroup() {
        var room = mock(Room.class);
        var actor = mock(RoomUnit.class);
        var clicked = mock(RoomUnit.class);
        var event = WiredEvent.builder(WiredEvent.Type.USER_CLICKS_USER, room)
                .actor(actor)
                .targetUnit(clicked)
                .build();
        assertEquals(List.of(clicked), WiredTriggerSourceUtil.resolveUsers(null, event, 11, null));
        assertTrue(WiredTriggerSourceUtil.resolveUsers(null, event, 999, null).isEmpty());
        var ctx = new WiredContext(event, null, mock(WiredServices.class), new WiredState(100));
        var primary = mock(HabboItem.class);
        var secondary = mock(HabboItem.class);
        assertEquals(
                List.of(secondary), WiredSourceUtil.resolveItemsRaw(ctx, 101, List.of(primary), List.of(secondary)));
        assertTrue(WiredSourceUtil.resolveUsersRaw(ctx, 999).isEmpty());
    }

    @Test
    void selectorEvaluationPreservesContextVariablesAndDoesNotReenterForAnEmptyDimension() {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var stack = mock(HabboItem.class);
        var selector = mock(InteractionWiredEffect.class);
        when(selector.isSelector()).thenReturn(true);
        when(room.getRoomSpecialTypes().getEffects((short) 0, (short) 0)).thenReturn(Set.of(selector));
        var ctx = context(room, stack);
        ctx.contextVariables().assignValue(33, 7, false);
        doAnswer(invocation -> {
                    WiredContext child = invocation.getArgument(0);
                    assertEquals(7, child.contextVariables().getValue(33));
                    assertTrue(WiredSourceUtil.resolveUsersRaw(child, 200).isEmpty());
                    child.targets().setItems(Set.of());
                    return null;
                })
                .when(selector)
                .execute(any(WiredContext.class));
        assertTrue(WiredSourceUtil.resolveItemsRaw(ctx, 200, null).isEmpty());
        assertTrue(ctx.targets().isItemsModifiedBySelector());
    }

    @Test
    void circularRemoteStacksTerminateWithoutSelectingTheLinkedWiredAsFurniture() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var first = new WiredEffectRemoteSelector(1, 2, mock(Item.class), "", 0, 0);
        var second = new WiredEffectRemoteSelector(2, 2, mock(Item.class), "", 0, 0);
        second.setX((short) 1);
        var rows = mock(ResultSet.class);
        when(rows.getString("wired_data")).thenReturn("{\"nativeSelection\":true,\"pickedFurniIds\":[2]}");
        first.loadWiredData(rows, room);
        when(rows.getString("wired_data")).thenReturn("{\"nativeSelection\":true,\"pickedFurniIds\":[1]}");
        second.loadWiredData(rows, room);
        when(room.getHabboItem(1)).thenReturn(first);
        when(room.getHabboItem(2)).thenReturn(second);
        when(room.getRoomSpecialTypes().getEffects((short) 0, (short) 0)).thenReturn(Set.of(first));
        when(room.getRoomSpecialTypes().getEffects((short) 1, (short) 0)).thenReturn(Set.of(second));
        var ctx = context(room, first);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> first.execute(ctx));
        assertTrue(ctx.targets().items().isEmpty());
        assertTrue(ctx.targets().users().isEmpty());
    }
}

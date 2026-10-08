package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMatchStatePosition;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionMovementValidation;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMatchFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniAsGroup;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveRotateFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRelativeMove;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerFurniStateToggled;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredSnapshotRegressionTest {
    private static void field(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    @Test
    void stateOnlySnapshotsUpdateEverySelectedItemWithoutRequestingAMove() throws Exception {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile tile = new RoomTile((short) 17, (short) 1, (short) 0, RoomTileState.OPEN, true);
        when(layout.getTile((short) 17, (short) 1)).thenReturn(tile);
        HabboItem gate = mock(HabboItem.class), light = mock(HabboItem.class);
        for (HabboItem item : List.of(gate, light)) {
            when(item.getX()).thenReturn((short) 17);
            when(item.getY()).thenReturn((short) 1);
            when(item.getExtradata()).thenReturn("0");
            when(item.allowWiredResetState()).thenReturn(true);
        }
        when(room.getHabboItem(209)).thenReturn(gate);
        when(room.getHabboItem(251)).thenReturn(light);
        var effect = new WiredEffectMatchFurni(361, 16, mock(Item.class), "", 0, 0);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn(
                "{\"state\":true,\"direction\":false,\"position\":false,\"altitude\":false,\"furniSource\":100,"
                + "\"items\":[{\"item_id\":209,\"state\":\"1\",\"x\":17,\"y\":1,\"z\":0},"
                + "{\"item_id\":251,\"state\":\"1\",\"x\":17,\"y\":1,\"z\":0}]}");
        effect.loadWiredData(row, room);
        WiredContext context = mock(WiredContext.class);
        when(context.room()).thenReturn(room);
        try (var helper = mockStatic(WiredMoveCarryHelper.class)) {
            assertDoesNotThrow(() -> effect.execute(context));
            verify(gate).setExtradata("1");
            verify(light).setExtradata("1");
            verify(room).updateItemState(gate);
            verify(room).updateItemState(light);
            helper.verifyNoInteractions();
        }
    }

    @Test
    void restoresRotationAndHeightTogetherAndAlsoHeightOnly() throws Exception {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile tile = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 1, (short) 1)).thenReturn(tile);
        HabboItem item = mock(HabboItem.class);
        when(item.getX()).thenReturn((short) 1);
        when(item.getY()).thenReturn((short) 1);
        when(item.getZ()).thenReturn(3.0);
        var effect = new WiredEffectMatchFurni(1, 1, mock(Item.class), "", 0, 0);
        Method apply = WiredEffectMatchFurni.class.getDeclaredMethod(
                "applySetting", Room.class, HabboItem.class, WiredMatchFurniSetting.class, WiredContext.class);
        apply.setAccessible(true);
        List<Object[]> calls = new ArrayList<>();
        try (var helper = mockStatic(WiredMoveCarryHelper.class, invocation -> {
            if (invocation.getMethod().getName().equals("moveFurni")) calls.add(invocation.getArguments());
            return FurnitureMovementError.NONE;
        })) {
            field(effect, "direction", true);
            field(effect, "altitude", true);
            apply.invoke(effect, room, item, new WiredMatchFurniSetting(10, "0", 2, 1, 1, 5.0), null);
            assertEquals(1, calls.size());
            assertEquals(2, calls.getFirst()[4]);
            assertEquals(5.0, calls.getFirst()[5]);
            calls.clear();
            field(effect, "direction", false);
            field(effect, "position", true);
            apply.invoke(effect, room, item, new WiredMatchFurniSetting(10, "0", 0, 1, 1, 5.0), null);
            assertEquals(1, calls.size());
            assertEquals(5.0, calls.getFirst()[5]);
            calls.clear();
            apply.invoke(effect, room, item, new WiredMatchFurniSetting(10, "0", 0, 1, 1, 3.0), null);
            assertTrue(calls.isEmpty());
        }
    }

    @Test
    void loadsMissingAndMalformedDataWithoutCrashingOrKeepingPartialSettings() throws Exception {
        var effect = new WiredEffectMatchFurni(1, 1, mock(Item.class), "", 0, 0);
        var trigger = new WiredTriggerFurniStateToggled(2, 1, mock(Item.class), "", 0, 0);
        for (String payload : new String[] {null, "", "{broken", "oops:0:10", "1:bad:1:1:1"}) {
            ResultSet row = mock(ResultSet.class);
            when(row.getString("wired_data")).thenReturn(payload);
            assertDoesNotThrow(() -> effect.loadWiredData(row, mock(Room.class)));
            assertDoesNotThrow(() -> trigger.loadWiredData(row, mock(Room.class)));
        }
    }

    @Test
    void rejectsRotationOnlyWhenTheRoomRejectsItsFootprintWithoutMovingRealItems() throws Exception {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile tile = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 1, (short) 1)).thenReturn(tile);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(10);
        when(item.getX()).thenReturn((short) 1);
        when(item.getY()).thenReturn((short) 1);
        when(item.getMaximumRotations()).thenReturn(4);
        when(room.getHabboItem(10)).thenReturn(item);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        var effect = new WiredEffectMoveRotateFurni(1, 1, mock(Item.class), "", 0, 0);
        field(effect, "rotation", 1);
        try (var sources = mockStatic(WiredSourceUtil.class);
                var helper = mockStatic(WiredMoveCarryHelper.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection()))
                    .thenReturn(List.of(item));
            helper.when(() -> WiredMoveCarryHelper.getSimulationMovementError(
                            eq(room), eq(effect), eq(item), eq(tile), anyInt(), eq(ctx), eq(false)))
                    .thenReturn(FurnitureMovementError.INVALID_MOVE);
            assertFalse(effect.simulate(ctx, new WiredSimulation(room)));
            verify(item, never()).setRotation(anyInt());
        }
    }

    @Test
    void movementValidationRejectsMissingLayoutForPreviouslyUnsimulatedEffects() {
        Room room = mock(Room.class);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        var effects = List.of(
                new WiredEffectMoveFurniAsGroup(1, 1, mock(Item.class), "", 0, 0),
                new WiredEffectRelativeMove(2, 1, mock(Item.class), "", 0, 0),
                new WiredEffectMatchFurni(3, 1, mock(Item.class), "", 0, 0));
        when(ctx.stack()).thenReturn(new WiredStack(null, null, List.of(), new ArrayList<>(effects)));
        var condition = new WiredConditionMovementValidation(4, 1, mock(Item.class), "", 0, 0);
        assertFalse(condition.evaluate(ctx));
        for (var effect : effects) assertFalse(effect.simulate(ctx, new WiredSimulation(room)));
    }

    @Test
    void rejectedSavesKeepPreviousConditionAndTriggerSettings() throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(1);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            var condition = new WiredConditionMatchStatePosition(1, 1, mock(Item.class), "", 0, 0);
            var trigger = new WiredTriggerFurniStateToggled(2, 1, mock(Item.class), "", 0, 0);
            String beforeCondition = condition.getWiredData();
            String beforeTrigger = trigger.getWiredData();
            var invalid = new WiredSettings(new int[] {1, 1, 1, 1, 200, 1}, "", new int[] {10, 11}, 0, 0);
            assertFalse(condition.saveData(invalid));
            assertFalse(trigger.saveData(invalid));
            assertEquals(beforeCondition, condition.getWiredData());
            assertEquals(beforeTrigger, trigger.getWiredData());
        }
    }

    @Test
    void nativeSnapshotPreservesTriggerOriginAndCapturedHeightOnReloadAndLegacyFallback() throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(50);
        GameEnvironment env = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        Room room = mock(Room.class);
        when(env.getRoomManager().getRoom(anyInt())).thenReturn(room);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 1, (short) 1))
                .thenReturn(new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true));
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(10);
        when(item.getX()).thenReturn((short) 1);
        when(item.getY()).thenReturn((short) 1);
        when(item.getZ()).thenReturn(5.0);
        when(item.getExtradata()).thenReturn("1");
        when(room.getHabboItem(10)).thenReturn(item);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment).thenReturn(env);
            var condition = new WiredConditionMatchStatePosition(1, 1, mock(Item.class), "", 0, 0);
            var nativeSettings = new WiredSettings(new int[] {1, 1, 1, 1}, "", new int[] {10}, 0, 0);
            nativeSettings.setNativeVariableFormat(true);
            nativeSettings.setFurniSourceTypes(new int[] {0});
            nativeSettings.setQuantifier(1);
            assertTrue(condition.saveData(nativeSettings));
            String saved = condition.getWiredData();
            assertTrue(saved.contains("\"furniSource\":0"));
            ResultSet row = mock(ResultSet.class);
            when(row.getString("wired_data")).thenReturn(saved);
            condition.onPickUp();
            condition.loadWiredData(row, room);
            assertEquals(saved, condition.getWiredData());
            assertTrue(condition.shouldMatchAltitude());
            assertEquals(5.0, condition.getMatchFurniSettings().iterator().next().z);
            when(row.getString("wired_data"))
                    .thenReturn(saved.replace("\"explicitSource\":true", "\"explicitSource\":false"));
            condition.loadWiredData(row, room);
            assertTrue(condition.getWiredData().contains("\"furniSource\":100"));
        }
    }

    @Test
    void invalidNativeFlagsAreRejectedBeforeTranslatingOrChangingSelection() {
        var settings = new WiredSettings(new int[] {1, 1, 2, 0}, "", new int[] {10}, 0, 0);
        settings.setNativeVariableFormat(true);
        settings.setFurniSourceTypes(new int[] {100});
        assertThrows(IllegalArgumentException.class, () -> WiredNativeSnapshotSettings.normalize(settings, true));
        assertEquals(10, settings.getFurniIds()[0]);
    }

    @Test
    void relativeMovesAccumulateVirtualPositionsAndUseSharedCollisionChecks() throws Exception {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile next = new RoomTile((short) 2, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile second = new RoomTile((short) 3, (short) 1, (short) 0, RoomTileState.OPEN, true);
        when(layout.getTile((short) 2, (short) 1)).thenReturn(next);
        when(layout.getTile((short) 3, (short) 1)).thenReturn(second);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(10);
        when(item.getX()).thenReturn((short) 1);
        when(item.getY()).thenReturn((short) 1);
        when(room.getHabboItem(10)).thenReturn(item);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        var relative = new WiredEffectRelativeMove(1, 1, mock(Item.class), "", 0, 0);
        field(relative, "horizontalDistance", 1);
        try (var sources = mockStatic(WiredSourceUtil.class);
                var helper = mockStatic(WiredMoveCarryHelper.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection()))
                    .thenReturn(List.of(item));
            helper.when(() -> WiredMoveCarryHelper.getSimulationMovementError(
                            eq(room), eq(relative), eq(item), any(), anyInt(), eq(ctx), eq(false)))
                    .thenReturn(FurnitureMovementError.NONE);
            WiredSimulation simulation = new WiredSimulation(room);
            assertTrue(relative.simulate(ctx, simulation));
            assertEquals(2, simulation.getItemPosition(item).x);
            assertTrue(relative.simulate(ctx, simulation));
            assertEquals(3, simulation.getItemPosition(item).x);
            helper.when(() -> WiredMoveCarryHelper.getSimulationMovementError(
                            eq(room), eq(relative), eq(item), eq(next), anyInt(), eq(ctx), eq(false)))
                    .thenReturn(FurnitureMovementError.INVALID_MOVE);
            assertFalse(relative.simulate(ctx, new WiredSimulation(room)));
            verify(item, never()).setX(anyShort());
        }
    }

    @Test
    void simulationRejectsInvalidHeightBeforeCallingRoomMovement() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 1, (short) 1))
                .thenReturn(new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true));
        HabboItem item = mock(HabboItem.class);
        for (double z : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, Room.MAXIMUM_FURNI_HEIGHT + 1}) {
            WiredSimulation simulation = new WiredSimulation(room);
            assertFalse(simulation.moveItem(item, (short) 1, (short) 1, z, 0));
            assertTrue(simulation.hasFailed());
        }
        verify(room, never()).furnitureFitsAt(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void nativeUseAndStateChangeHaveDistinctEventsAndLegacyTriggersDoNotFireTwice() throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(50);
        GameEnvironment env = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        Room room = mock(Room.class);
        when(env.getRoomManager().getRoom(anyInt())).thenReturn(room);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(10);
        when(item.getExtradata()).thenReturn("1");
        when(room.getHabboItem(10)).thenReturn(item);
        Item useBase = mock(Item.class);
        when(useBase.getName()).thenReturn("wf_trg_stuff_state");
        Item stateBase = mock(Item.class);
        when(stateBase.getName()).thenReturn("wf_trg_state_changed");
        var use = new WiredTriggerFurniStateToggled(1, 1, useBase, "", 0, 0);
        var changed = new WiredTriggerFurniStateToggled(2, 1, stateBase, "", 0, 0);
        var legacy = new WiredTriggerFurniStateToggled(3, 1, stateBase, "", 0, 0);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment).thenReturn(env);
            var useSettings = new WiredSettings(new int[0], "", new int[] {10}, 0, 0);
            useSettings.setNativeVariableFormat(true);
            useSettings.setFurniSourceTypes(new int[] {100});
            assertTrue(use.saveData(useSettings));
            var stateSettings = new WiredSettings(new int[] {1}, "", new int[] {10}, 0, 0);
            stateSettings.setNativeVariableFormat(true);
            stateSettings.setFurniSourceTypes(new int[] {100});
            assertTrue(changed.saveData(stateSettings));
            assertTrue(legacy.saveData(new WiredSettings(new int[] {0, 100}, "", new int[] {10}, 0, 0)));
            var usedEvent = com.eu.habbo.habbohotel.wired.core.WiredEvent.builder(
                            com.eu.habbo.habbohotel.wired.core.WiredEvent.Type.FURNI_USED, room)
                    .sourceItem(item)
                    .build();
            var stateEvent = com.eu.habbo.habbohotel.wired.core.WiredEvent.builder(
                            com.eu.habbo.habbohotel.wired.core.WiredEvent.Type.FURNI_STATE_UPDATED, room)
                    .sourceItem(item)
                    .triggeredByEffect(true)
                    .build();
            var legacyEvent = com.eu.habbo.habbohotel.wired.core.WiredEvent.builder(
                            com.eu.habbo.habbohotel.wired.core.WiredEvent.Type.FURNI_STATE_CHANGED, room)
                    .sourceItem(item)
                    .build();
            assertTrue(use.matches(use, usedEvent));
            assertFalse(use.matches(use, stateEvent));
            assertFalse(changed.matches(changed, usedEvent));
            assertTrue(changed.matches(changed, stateEvent));
            // Older presets put STATE_CHANGE fields on wf_trg_stuff_state.
            // Carries and borrows must receive automatic state changes without an actor.
            assertTrue(use.saveData(stateSettings));
            assertTrue(use.matches(use, stateEvent));
            assertFalse(use.matches(use, usedEvent));
            assertFalse(use.requiresActor());
            var allStates = new WiredSettings(new int[] {0}, "", new int[] {10}, 0, 0);
            allStates.setNativeVariableFormat(true);
            allStates.setFurniSourceTypes(new int[] {100});
            assertTrue(use.saveData(allStates));
            assertTrue(use.matches(use, stateEvent));
            assertFalse(use.requiresActor());
            assertTrue(use.saveData(useSettings));
            assertTrue(use.requiresActor());
            assertFalse(changed.requiresActor());
            assertTrue(legacy.matches(legacy, legacyEvent));
            assertFalse(legacy.matches(legacy, usedEvent));
            assertFalse(legacy.matches(legacy, stateEvent));
            String saved = changed.getWiredData();
            ResultSet row = mock(ResultSet.class);
            when(row.getString("wired_data")).thenReturn(saved);
            changed.onPickUp();
            changed.loadWiredData(row, room);
            assertEquals(saved, changed.getWiredData());
            assertFalse(changed.requiresActor());
            when(item.getExtradata()).thenReturn("2");
            assertFalse(changed.matches(changed, stateEvent));
            useSettings.setIntParams(new int[] {1});
            assertTrue(use.saveData(useSettings));
            assertTrue(use.matches(use, stateEvent));
            when(item.getExtradata()).thenReturn("3");
            assertFalse(use.matches(use, stateEvent));
            String savedUse = use.getWiredData();
            when(row.getString("wired_data")).thenReturn(savedUse);
            use.loadWiredData(row, room);
            assertFalse(use.matches(use, stateEvent));
            assertEquals(savedUse, use.getWiredData());
            useSettings.setIntParams(new int[] {2});
            String before = use.getWiredData();
            assertThrows(IllegalArgumentException.class, () -> use.saveData(useSettings));
            assertEquals(before, use.getWiredData());
        }
    }

    @Test
    void directionalMovementSearchesValidAlternativesWithoutMutatingSavedDirection() throws Exception {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile origin = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile north = new RoomTile((short) 1, (short) 0, (short) 0, RoomTileState.OPEN, true);
        RoomTile east = new RoomTile((short) 2, (short) 1, (short) 0, RoomTileState.OPEN, true);
        when(layout.getTile((short) 1, (short) 1)).thenReturn(origin);
        when(layout.getTile((short) 2, (short) 1)).thenReturn(east);
        when(layout.getTileInFront(origin, 0)).thenReturn(north);
        when(layout.getTileInFront(origin, 2)).thenReturn(east);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(10);
        when(item.getX()).thenReturn((short) 1);
        when(item.getY()).thenReturn((short) 1);
        when(room.getHabboItem(10)).thenReturn(item);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        var effect = new com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeFurniDirection(
                1, 1, mock(Item.class), "", 0, 0);
        field(effect, "furniSource", 0);
        field(effect, "blockedAction", 2);
        try (var sources = mockStatic(WiredSourceUtil.class);
                var helper = mockStatic(WiredMoveCarryHelper.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(0), anyCollection()))
                    .thenReturn(List.of(item));
            helper.when(() -> WiredMoveCarryHelper.getSimulationMovementError(
                            eq(room), eq(effect), eq(item), eq(north), eq(0), eq(ctx), eq(true)))
                    .thenReturn(FurnitureMovementError.INVALID_MOVE);
            helper.when(() -> WiredMoveCarryHelper.getSimulationMovementError(
                            eq(room), eq(effect), eq(item), eq(east), eq(0), eq(ctx), anyBoolean()))
                    .thenReturn(FurnitureMovementError.NONE);
            String before = effect.getWiredData();
            WiredSimulation simulation = new WiredSimulation(room);
            assertTrue(effect.simulate(ctx, simulation));
            assertEquals(2, simulation.getItemPosition(item).x);
            assertEquals(before, effect.getWiredData());
            verify(item, never()).setX(anyShort());
        }
    }

    @Test
    void moveToSimulationUsesExistingSpacingWithoutChangingItsRuntimeOffset() throws Exception {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        when(room.getLayout()).thenReturn(layout);
        RoomTile origin = new RoomTile((short) 1, (short) 1, (short) 0, RoomTileState.OPEN, true);
        RoomTile destination = new RoomTile((short) 1, (short) 5, (short) 0, RoomTileState.OPEN, true);
        when(layout.getTile((short) 1, (short) 1)).thenReturn(origin);
        when(layout.getTile((short) 1, (short) 5)).thenReturn(destination);
        when(layout.getTileInFront(origin, 0, 4)).thenReturn(destination);
        HabboItem target = mock(HabboItem.class);
        when(target.getId()).thenReturn(10);
        when(target.getX()).thenReturn((short) 1);
        when(target.getY()).thenReturn((short) 1);
        when(room.getHabboItem(10)).thenReturn(target);
        HabboItem mover = mock(HabboItem.class);
        when(mover.getId()).thenReturn(11);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        when(ctx.legacySettings()).thenReturn(new Object[] {mover});
        var effect = new com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniTo(
                1, 1, mock(Item.class), "", 0, 0);
        field(effect, "spacing", 2);
        field(effect, "indexOffset", new java.util.LinkedHashMap<>(java.util.Map.of(10, 2)));
        try (var sources = mockStatic(WiredSourceUtil.class);
                var helper = mockStatic(WiredMoveCarryHelper.class);
                var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getRandom).thenReturn(new java.util.Random(1));
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection()))
                    .thenReturn(List.of(target));
            helper.when(() -> WiredMoveCarryHelper.getSimulationMovementError(
                            eq(room), eq(effect), eq(mover), eq(destination), anyInt(), eq(ctx), eq(false)))
                    .thenReturn(FurnitureMovementError.NONE);
            WiredSimulation simulation = new WiredSimulation(room);
            assertTrue(effect.simulate(ctx, simulation));
            assertEquals(5, simulation.getItemPosition(mover).y);
            Field offsets = effect.getClass().getDeclaredField("indexOffset");
            offsets.setAccessible(true);
            assertEquals(java.util.Map.of(10, 2), offsets.get(effect));
        }
    }

    @Test
    void nativeRelativeOffsetsRoundTripAndFailedSavesKeepPreviousConfiguration() throws Exception {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(50);
        when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(20);
        GameEnvironment env = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        Room room = mock(Room.class);
        when(env.getRoomManager().getRoom(anyInt())).thenReturn(room);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(10);
        when(room.getHabboItem(10)).thenReturn(item);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            emulator.when(Emulator::getGameEnvironment).thenReturn(env);
            var effect = new WiredEffectRelativeMove(1, 1, mock(Item.class), "", 0, 0);
            var settings = new WiredSettings(new int[] {-3, 5}, "", new int[] {10}, 0, 2);
            settings.setNativeVariableFormat(true);
            settings.setFurniSourceTypes(new int[] {201});
            assertTrue(effect.saveData(settings, null));
            String saved = effect.getWiredData();
            assertTrue(saved.contains("\"horizontalDirection\":0"));
            assertTrue(saved.contains("\"horizontalDistance\":3"));
            ResultSet row = mock(ResultSet.class);
            when(row.getString("wired_data")).thenReturn(saved);
            effect.onPickUp();
            effect.loadWiredData(row, room);
            assertEquals(saved, effect.getWiredData());
            settings.setIntParams(new int[] {20, -20});
            settings.setDelay(21);
            assertThrows(
                    com.eu.habbo.messages.incoming.wired.WiredSaveException.class,
                    () -> effect.saveData(settings, null));
            assertEquals(saved, effect.getWiredData());
            settings.setIntParams(new int[] {Integer.MIN_VALUE, 0});
            assertThrows(IllegalArgumentException.class, () -> effect.saveData(settings, null));
            assertEquals(saved, effect.getWiredData());
        }
    }
}

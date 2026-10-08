package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionVariableValueMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectVariableSelectorBase;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredNativeVariableSettingsTest {
    @Test
    void giveChangeAndRemoveExecuteUsingNativeFieldsAndSurviveReload() throws Exception {
        var room = mock(Room.class, RETURNS_DEEP_STUBS);
        var user = mock(Habbo.class, RETURNS_DEEP_STUBS);
        var unit = mock(RoomUnit.class);
        var manager = mock(RoomUserVariableManager.class);
        when(user.getHabboInfo().getId()).thenReturn(9);
        when(room.getHabbo(unit)).thenReturn(user);
        when(room.getUserVariableManager()).thenReturn(manager);
        when(manager.getDefinitionInfo(33))
                .thenReturn(new WiredVariableDefinitionInfo(33, "score", true, 0, false, false));
        when(manager.getCurrentValue(9, 33)).thenReturn(4);
        var environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(0)).thenReturn(room);
        var config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.max_delay", 20)).thenReturn(20);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(20);
        try (var emulator = mockStatic(Emulator.class);
                var sources = mockStatic(WiredSourceUtil.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            sources.when(() -> WiredSourceUtil.isDefaultUserSource(201)).thenReturn(true);
            var ctx = mock(WiredContext.class);
            when(ctx.room()).thenReturn(room);
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, 201)).thenReturn(List.of(unit));
            var give = new WiredEffectGiveVariable(1, 2, mock(Item.class), "", 0, 0);
            var initial = nativeSettings(new int[] {1, -1, -7, 1}, "33");
            initial.setUserSourceTypes(new int[] {201});
            assertTrue(give.saveData(initial, null));
            give.execute(ctx);
            verify(manager).assignVariable(user, 33, -7, true);
            var change = new WiredEffectChangeVariableValue(2, 2, mock(Item.class), "", 0, 0);
            var increment = nativeSettings(new int[] {1, 1, 0, 0, 3, -20}, "custom:33", "n");
            increment.setUserSourceTypes(new int[] {201, 0});
            assertTrue(change.saveData(increment, null));
            var stored = mock(ResultSet.class);
            when(stored.getString("wired_data")).thenReturn(change.getWiredData());
            var reopened = new WiredEffectChangeVariableValue(2, 2, mock(Item.class), "", 0, 0);
            reopened.loadWiredData(stored, room);
            reopened.execute(ctx);
            verify(manager).updateVariableValue(9, 33, 7);
            var remove = new WiredEffectRemoveVariable(3, 2, mock(Item.class), "", 0, 0);
            var deletion = nativeSettings(new int[] {1}, "33");
            deletion.setUserSourceTypes(new int[] {201});
            assertTrue(remove.saveData(deletion, null));
            remove.execute(ctx);
            verify(manager).removeVariable(9, 33);
        }
    }

    @Test
    void valueComparisonKeepsBothReferencesAndSecondarySelection() {
        var input = nativeSettings(new int[] {0, 5, 1, -1, -42, -20}, "custom:33", "custom:44");
        input.setFurniIds2(new int[] {8, 9});
        input.setFurniSourceTypes(new int[] {100, 101});
        input.setUserSourceTypes(new int[] {200, 201});
        input.setQuantifier(1);
        var result = WiredNativeVariableSettings.normalize(mock(WiredConditionVariableValueMatch.class), input);
        assertArrayEquals(new int[] {1, 1, 1, -42, 2, 200, 100, 201, 101, 1}, result.getIntParams());
        assertEquals("custom:33\tcustom:44\t8;9", result.getStringParam());
    }

    @Test
    void swfComparisonCodesTranslateForConditionsAndSelectorsWithoutChangingLegacyCodes() {
        // SWF radio values: <, =, >, <=, !=, >=. Persisted Polaris order differs.
        int[] expected = {4, 2, 0, 3, 5, 1};
        for (int code = 0; code < expected.length; code++) {
            var condition = mock(WiredConditionVariableValueMatch.class);
            var input = nativeSettings(new int[] {-10, code, 0, 0, 10, -10}, "33", "0");
            assertEquals(expected[code], WiredNativeVariableSettings.normalize(condition, input).getIntParams()[1]);
            var selector = mock(WiredEffectVariableSelectorBase.class);
            var selection = nativeSettings(new int[] {code, 0, 0, 10, -10}, "33", "0");
            assertEquals(expected[code], WiredNativeVariableSettings.normalize(selector, selection).getIntParams()[1]);
            var legacy = new WiredSettings(new int[] {3, code, 0, 10, 3}, "33", new int[0], 0);
            assertSame(legacy, WiredNativeVariableSettings.normalize(condition, legacy));
        }
    }

    @Test
    void oldStoredAndLegacyWireFieldsDoNotGetReinterpreted() {
        var input = new WiredSettings(new int[] {1, 11}, "score", new int[0], 0);
        assertSame(input, WiredNativeVariableSettings.normalize(mock(WiredExtraUserVariable.class), input));
        var nativeInput = nativeSettings(new int[] {11, 1});
        assertArrayEquals(
                new int[] {1, 11},
                WiredNativeVariableSettings.normalize(mock(WiredExtraUserVariable.class), nativeInput)
                        .getIntParams());
    }

    @Test
    void unsupportedTargetsAndNonIntValuesAreRejectedRatherThanReassigned() {
        var item = mock(WiredEffectGiveVariable.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> WiredNativeVariableSettings.normalize(item, nativeSettings(new int[] {99, 0, 7, 0}, "33")));
        assertThrows(
                IllegalArgumentException.class,
                () -> WiredNativeVariableSettings.normalize(item, nativeSettings(new int[] {1, 1, 7, 0}, "33")));
        assertThrows(
                IllegalArgumentException.class,
                () -> WiredNativeVariableSettings.normalize(item, nativeSettings(new int[] {-10, 0, 7, 0}, "33")));
        assertThrows(
                IllegalArgumentException.class,
                () -> WiredNativeVariableSettings.normalize(
                        mock(WiredEffectRemoveVariable.class), nativeSettings(new int[] {-10}, "33")));
    }

    @Test
    void byteLevelTailReadsBooleanFlagsWithoutMovingTheSelectionBoundary() {
        var buffer = Unpooled.buffer();
        buffer.writeInt(1).writeInt(1);
        writeString(buffer, "");
        buffer.writeInt(0).writeInt(3).writeInt(InteractionWired.WIRED_NATIVE_VARIABLE_SETTINGS_MARKER);
        buffer.writeInt(1).writeInt(100).writeInt(1).writeInt(201).writeInt(1);
        writeString(buffer, "33");
        buffer.writeInt(0).writeInt(1).writeBoolean(true).writeBoolean(false).writeInt(2);
        try {
            var settings = InteractionWired.readSettings(new ClientMessage(0, buffer), true);
            assertTrue(settings.isNativeVariableFormat());
            assertTrue(settings.isFilterExisting());
            assertFalse(settings.isInvert());
            assertEquals(2, settings.getStuffTypeSelectionCode());
            assertEquals(3, settings.getDelay());
            assertArrayEquals(new String[] {"33"}, settings.getVariableTokens());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static void writeString(io.netty.buffer.ByteBuf buffer, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length).writeBytes(bytes);
    }

    private static WiredSettings nativeSettings(int[] params, String... tokens) {
        var settings = new WiredSettings(params, "", new int[0], 0);
        settings.setNativeVariableFormat(true);
        settings.setVariableTokens(tokens);
        return settings;
    }
}

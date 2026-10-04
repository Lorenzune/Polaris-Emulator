package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.*;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.*;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraScanChestFurniByType;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.*;
import com.eu.habbo.habbohotel.wired.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WiredChestNativeTest {
    @Test void rewardsUseChestBubblesWithTheConfiguredPopupBehaviorAndReadableTitle() {
        var user = mock(Habbo.class, RETURNS_DEEP_STUBS);
        var config = new WiredChestConfiguration(); config.popup = "Complimenti!"; config.showByDefault = false;
        config.notifyReward(user, 3);
        var packets = org.mockito.ArgumentCaptor.forClass(com.eu.habbo.messages.outgoing.MessageComposer.class);
        verify(user.getClient()).sendResponse(packets.capture());
        var bubble = (com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer) packets.getValue();
        assertEquals("wired.reward", bubble.getErrorKey()); assertEquals("Complimenti!", bubble.getKeys().get("message"));
        assertEquals("0", bubble.getKeys().get("openByDefault")); assertEquals("Ricompensa ricevuta", bubble.getKeys().get("title"));
        clearInvocations(user.getClient()); config.showByDefault = true; config.notifyReward(user, 3);
        verify(user.getClient()).sendResponse(packets.capture());
        assertEquals("1", ((com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer)packets.getValue()).getKeys().get("openByDefault"));
        clearInvocations(user.getClient()); config.notifyReward(user, 0);
        verify(user.getClient()).sendResponse(packets.capture());
        assertEquals("chest.rejected", ((com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer)packets.getValue()).getErrorKey());
    }
    @Test void nativeAllAndAnyCompareEachChestRatherThanTheirCombinedStock() {
        var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
        var first = mock(InteractionWiredChestFurni.class); var second = mock(InteractionWiredChestFurni.class);
        var a = new ChestStorage(); var b = new ChestStorage(); when(first.getContents()).thenReturn(a); when(second.getContents()).thenReturn(b);
        when(room.getHabboItem(10)).thenReturn(first); when(room.getHabboItem(11)).thenReturn(second);
        for (int n = 0; n < 2; n++) a.addFurniItem(item(55, "a"));
        for (int n = 0; n < 4; n++) b.addFurniItem(item(55, "b"));
        var reference = mock(HabboItem.class, RETURNS_DEEP_STUBS); when(reference.getBaseItem().getId()).thenReturn(55); when(room.getHabboItem(90)).thenReturn(reference);
        var amount = new WiredConditionChestHasItems(1, 2, mock(Item.class), "", 0, 0);
        var type = new WiredConditionChestHasItemType(1, 2, mock(Item.class), "", 0, 0);
        var config = new WiredChestConfiguration(); config.typeIds = List.of(90);
        try (var sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection())).thenAnswer(call -> new ArrayList<>((Collection<HabboItem>)call.getArgument(2)));
            for (int quantifier : new int[]{0, 1}) {
                config.conditionQuantifier = quantifier;
                var data = settings(config, 3, 0, -20, 5); data.setFurniIds(new int[]{10, 11});
                amount.saveData(data); type.saveData(data);
                assertEquals(quantifier == 1, amount.evaluate(ctx)); assertEquals(quantifier == 1, type.evaluate(ctx));
            }
        }
    }
    @Test void referenceCategoriesResolveActualFurnitureAndUserVariables() {
        var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
        var reference = mock(HabboItem.class); when(reference.getId()).thenReturn(90);
        var furniVariables = mock(RoomFurniVariableManager.class); when(room.getFurniVariableManager()).thenReturn(furniVariables);
        when(furniVariables.hasVariable(90, 33)).thenReturn(true); when(furniVariables.getCurrentValue(90, 33)).thenReturn(8);
        var unit = mock(RoomUnit.class); var user = mock(Habbo.class, RETURNS_DEEP_STUBS); when(user.getHabboInfo().getId()).thenReturn(7); when(room.getHabbo(unit)).thenReturn(user);
        var userVariables = mock(RoomUserVariableManager.class); when(room.getUserVariableManager()).thenReturn(userVariables);
        when(userVariables.hasVariable(7, 33)).thenReturn(true); when(userVariables.getCurrentValue(7, 33)).thenReturn(12);
        var config = new WiredChestConfiguration(); config.variableId = 33; config.amountOption = 1; config.referenceSource = 200; config.referenceUserSource = 201;
        try (var sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(200), anyCollection())).thenReturn(List.of(reference));
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, 201)).thenReturn(List.of(unit));
            config.amountTarget = 0; assertEquals(8, config.amount(ctx, 99));
            config.amountTarget = 1; assertEquals(12, config.amount(ctx, 99));
            when(userVariables.hasVariable(7, 33)).thenReturn(false); assertNull(config.amount(ctx, 99));
        }
    }
    @Test void selectedFurnitureVariableReferenceSurvivesConfigurationAndReadsItsOwnValue() {
        var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
        var reference = mock(HabboItem.class); when(reference.getId()).thenReturn(90); when(room.getHabboItem(90)).thenReturn(reference);
        var manager = mock(RoomFurniVariableManager.class); when(room.getFurniVariableManager()).thenReturn(manager);
        when(manager.hasVariable(90, 33)).thenReturn(true); when(manager.getCurrentValue(90, 33)).thenReturn(8);
        var original = new WiredChestConfiguration(); original.variableId = 33; original.referenceSource = 100; original.referenceIds = List.of(90);
        var config = WiredChestConfiguration.read(settings(original, 1, 1, 0, 5));
        assertNotNull(config); assertEquals(List.of(90), config.referenceIds);
        try (var sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(ctx, 100, List.of(reference))).thenReturn(List.of(reference));
            assertEquals(8, config.amount(ctx, 99));
            when(room.getHabboItem(90)).thenReturn(null);
            sources.when(() -> WiredSourceUtil.resolveItems(ctx, 100, List.of())).thenReturn(List.of());
            assertNull(config.amount(ctx, 99));
        }
    }
    @Test void internalVariableReferenceUsesTheSameSelectedCategoryAndSource() {
        var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
        var reference = mock(HabboItem.class);
        var config = new WiredChestConfiguration(); config.amountOption = 1; config.amountTarget = 0;
        config.referenceSource = 200; config.variableToken = "internal:@position_x";
        try (var sources = mockStatic(WiredSourceUtil.class); var internals = mockStatic(WiredInternalVariableSupport.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(200), anyCollection())).thenReturn(List.of(reference));
            internals.when(() -> WiredInternalVariableSupport.readFurniValue(room, reference, "@position_x")).thenReturn(8);
            assertEquals(8, config.amount(ctx, 99));
            config.amountTarget = -20; config.variableToken = "internal:@execution_time";
            internals.when(() -> WiredInternalVariableSupport.readContextValue(ctx, "@execution_time")).thenReturn(12);
            assertEquals(12, config.amount(ctx, 99));
        }
    }
    private WiredSettings settings(WiredChestConfiguration config, int... params) {
        return new WiredSettings(params, WiredManager.getGson().toJson(config), new int[]{10}, 0);
    }
    private ChestFurniStoredItem item(int type, String data) {
        var item = new ChestFurniStoredItem(); item.baseItemId = type; item.extradata = data; return item;
    }
    @Test void fifoAndLifoRemoveActualInstancesAndKeepAggregateCounts() {
        var storage = new ChestStorage();
        var first = item(5, "first"); var second = item(6, "second"); var last = item(5, "last");
        storage.addFurniItem(first); storage.addFurniItem(second); storage.addFurniItem(last);
        assertSame(last, storage.removeFurniInOrder(1, 2).getFirst());
        assertSame(first, storage.removeFurniInOrder(1, 1).getFirst());
        assertEquals(0, storage.count(1, 5));
        assertSame(second, storage.removeFurniInOrder(9, 1).getFirst());
        assertEquals(0, storage.furniItemCount());
    }
    @Test void randomRemovalNeverDuplicatesOrExceedsTheChest() {
        var storage = new ChestStorage();
        for (int n = 0; n < 30; n++) storage.addFurniItem(item(n % 3, "" + n));
        var removed = storage.removeFurniInOrder(40, 0);
        assertEquals(30, new HashSet<>(removed).size());
        assertEquals(0, storage.furniItemCount());
    }
    @Test void nativeAndLegacyConfigurationsRoundTrip() throws Exception {
        var config = new WiredChestConfiguration(); config.amountOption = 1; config.variableId = 33; config.amountTarget = -10;
        config.currencyType = 5; config.popup = "Premio"; config.typeIds = List.of(90); config.rewardingMode = 1;
        var effect = new WiredEffectGiveCurrencyFromChest(1, 2, mock(Item.class), "", 0, 0);
        effect.saveData(settings(config, 1, 12, 1, -10, 0, 11, 200), null);
        var restored = new WiredEffectGiveCurrencyFromChest(1, 2, mock(Item.class), "", 0, 0);
        var result = mock(java.sql.ResultSet.class); when(result.getString("wired_data")).thenReturn(effect.getWiredData());
        restored.loadWiredData(result, null);
        assertEquals(effect.getWiredData(), restored.getWiredData());
        effect.saveData(new WiredSettings(new int[]{12, 0}, "", new int[]{10}, 0), null);
        assertTrue(effect.getWiredData().contains("\"amount\":12"));
        assertFalse(effect.getWiredData().contains("configuration"));
    }
    @Test void allCurrencyChoicesDebitOnlyTheirOwnPoolAndLogTheActualType() throws Exception {
        for (int currency : new int[]{-1, 0, 5}) {
            var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
            var unit = mock(RoomUnit.class); var user = mock(Habbo.class, RETURNS_DEEP_STUBS); when(room.getHabbo(unit)).thenReturn(user);
            var chest = mock(InteractionWiredChestCurrency.class); var storage = new ChestStorage(); when(chest.getContents()).thenReturn(storage);
            when(room.getHabboItem(10)).thenReturn(chest);
            storage.depositCurrency(-1, 20); storage.depositCurrency(0, 20); storage.depositCurrency(5, 20);
            var config = new WiredChestConfiguration(); config.currencyType = currency;
            var effect = new WiredEffectGiveCurrencyFromChest(1, 2, mock(Item.class), "", 0, 0);
            effect.saveData(settings(config, 0, 7, 0, -20, 0, 11, 0), null);
            try (var sources = mockStatic(WiredSourceUtil.class)) {
                sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection())).thenReturn(List.of(chest));
                sources.when(() -> WiredSourceUtil.resolveUsers(ctx, 0)).thenReturn(List.of(unit));
                effect.execute(ctx);
            }
            for (int type : new int[]{-1, 0, 5}) assertEquals(type == currency ? 13 : 20, storage.count(0, type));
            if (currency == -1) verify(user).giveCredits(eq(7), anyString()); else verify(user).givePoints(eq(currency), eq(7), anyString());
            assertEquals(currency, storage.getLog().getFirst().currencyType);
        }
    }
    @Test void giveAllAndLockedChestNeverMintCurrency() throws Exception {
        var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
        var unit = mock(RoomUnit.class); var user = mock(Habbo.class, RETURNS_DEEP_STUBS); when(room.getHabbo(unit)).thenReturn(user);
        var chest = mock(InteractionWiredChestCurrency.class); var storage = new ChestStorage(); when(chest.getContents()).thenReturn(storage); when(room.getHabboItem(10)).thenReturn(chest);
        storage.depositCurrency(5, 4); storage.setLocked(true);
        var config = new WiredChestConfiguration(); config.currencyType = 5; config.rewardingMode = 1;
        var effect = new WiredEffectGiveCurrencyFromChest(1, 2, mock(Item.class), "", 0, 0);
        effect.saveData(settings(config, 1, 1, 0, -20, 0, 11, 0), null);
        try (var sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection())).thenReturn(List.of(chest));
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, 0)).thenReturn(List.of(unit));
            effect.execute(ctx); assertEquals(4, storage.count(0, 5)); verify(user, never()).givePoints(anyInt(), anyInt(), anyString());
            storage.setLocked(false); effect.execute(ctx); assertEquals(0, storage.count(0, 5)); verify(user).givePoints(eq(5), eq(4), anyString());
        }
    }
    @Test void conditionsUseSelectedTypeInstancesAndAllThreeCurrencyBalances() {
        var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
        var chest = mock(InteractionWiredChestFurni.class); var storage = new ChestStorage(); when(chest.getContents()).thenReturn(storage);
        var reference = mock(HabboItem.class, RETURNS_DEEP_STUBS); when(reference.getBaseItem().getId()).thenReturn(55);
        when(room.getHabboItem(10)).thenReturn(chest); when(room.getHabboItem(90)).thenReturn(reference);
        storage.addFurniItem(item(55, "a")); storage.addFurniItem(item(55, "b")); storage.addFurniItem(item(77, "c"));
        storage.depositCurrency(-1, 2); storage.depositCurrency(0, 3); storage.depositCurrency(5, 4);
        var config = new WiredChestConfiguration(); config.typeIds = List.of(90);
        var types = new WiredConditionChestHasItemType(1, 2, mock(Item.class), "", 0, 0);
        var amounts = new WiredConditionChestHasItems(1, 2, mock(Item.class), "", 0, 0);
        types.saveData(settings(config, 2, 0, -20, 1)); amounts.saveData(settings(config, 12, 0, -20, 1));
        try (var sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection())).thenAnswer(call -> new ArrayList<>((Collection<HabboItem>)call.getArgument(2)));
            assertTrue(types.evaluate(ctx)); assertTrue(amounts.evaluate(ctx));
            types.saveData(settings(config, 3, 0, -20, 1)); assertFalse(types.evaluate(ctx));
        }
    }
    @Test void missingVariableDoesNotFallBackToTheConstant() {
        var config = new WiredChestConfiguration(); config.amountOption = 1; config.variableId = 33;
        var ctx = mock(WiredContext.class); when(ctx.room()).thenReturn(mock(Room.class));
        try (var variables = mockStatic(WiredContextVariableSupport.class)) {
            variables.when(() -> WiredContextVariableSupport.getCurrentValue(ctx, 33)).thenReturn(null);
            assertNull(config.amount(ctx, 99));
            variables.when(() -> WiredContextVariableSupport.getCurrentValue(ctx, 33)).thenReturn(7);
            assertEquals(7, config.amount(ctx, 99));
        }
    }
    @Test void zeroAmountTypeConditionSurvivesReload() throws Exception {
        var config = new WiredChestConfiguration(); config.typeIds = List.of(90);
        var condition = new WiredConditionChestHasItemType(1, 2, mock(Item.class), "", 0, 0);
        condition.saveData(settings(config, 0, 0, -20, 1));
        var restored = new WiredConditionChestHasItemType(1, 2, mock(Item.class), "", 0, 0);
        var result = mock(java.sql.ResultSet.class); when(result.getString("wired_data")).thenReturn(condition.getWiredData());
        restored.loadWiredData(result, null);
        assertEquals(condition.getWiredData(), restored.getWiredData());
    }
    @Test void scannerWritesContextCountWithoutReplacingSelectorTargets() {
        var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
        var chest = mock(InteractionWiredChestFurni.class); var storage = new ChestStorage(); when(chest.getContents()).thenReturn(storage); when(room.getHabboItem(10)).thenReturn(chest);
        var reference = mock(HabboItem.class, RETURNS_DEEP_STUBS); when(reference.getBaseItem().getId()).thenReturn(55); when(room.getHabboItem(90)).thenReturn(reference);
        storage.addFurniItem(item(55, "a")); storage.addFurniItem(item(55, "b")); storage.addFurniItem(item(77, "c"));
        var config = new WiredChestConfiguration(); config.typeIds = List.of(90); config.variableId = 33;
        var scanner = new WiredExtraScanChestFurniByType(1, 2, mock(Item.class), "", 0, 0);
        assertTrue(scanner.saveData(settings(config, 0), null));
        try (var sources = mockStatic(WiredSourceUtil.class); var variables = mockStatic(WiredContextVariableSupport.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection())).thenAnswer(call -> new ArrayList<>((Collection<HabboItem>)call.getArgument(2)));
            variables.when(() -> WiredContextVariableSupport.hasDefinition(room, 33)).thenReturn(true);
            scanner.scan(ctx);
            variables.verify(() -> WiredContextVariableSupport.assignVariable(ctx, room, 33, 2, true));
            verify(ctx, never()).targets();
        }
    }

    @Test void furniRewardUsesTheRequestedOrderAndContinuesAcrossChests() throws Exception {
        var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
        var unit = mock(RoomUnit.class); var user = mock(Habbo.class, RETURNS_DEEP_STUBS); when(room.getHabbo(unit)).thenReturn(user);
        var first = mock(InteractionWiredChestFurni.class); var second = mock(InteractionWiredChestFurni.class);
        when(first.getContents()).thenReturn(new ChestStorage()); when(second.getContents()).thenReturn(new ChestStorage());
        when(room.getHabboItem(10)).thenReturn(first); when(room.getHabboItem(11)).thenReturn(second);
        var config = new WiredChestConfiguration(); config.iterationMode = 2;
        var data = settings(config, 0, 3, 0, -20, 0, 2, 0); data.setFurniIds(new int[]{10, 11});
        var effect = new WiredEffectGiveFurniFromChest(1, 2, mock(Item.class), "", 0, 0); effect.saveData(data, null);
        try (var sources = mockStatic(WiredSourceUtil.class); var transfers = mockStatic(ChestWiredFurniUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection())).thenReturn(List.of(first, second));
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, 0)).thenReturn(List.of(unit));
            transfers.when(() -> ChestWiredFurniUtil.giveFromChestInOrder(user, first, 3, 2)).thenReturn(1);
            transfers.when(() -> ChestWiredFurniUtil.giveFromChestInOrder(user, second, 2, 2)).thenReturn(2);
            effect.execute(ctx);
            transfers.verify(() -> ChestWiredFurniUtil.giveFromChestInOrder(user, first, 3, 2));
            transfers.verify(() -> ChestWiredFurniUtil.giveFromChestInOrder(user, second, 2, 2));
        }
    }

    @Test void scannerPreviewMatchesTheFourGlowingRoomIcons() {
        var ctx = mock(WiredContext.class); var room = mock(Room.class); when(ctx.room()).thenReturn(room);
        var chest = mock(InteractionWiredChestFurni.class); var storage = new ChestStorage(); when(chest.getContents()).thenReturn(storage); when(room.getHabboItem(10)).thenReturn(chest);
        var reference = mock(HabboItem.class, RETURNS_DEEP_STUBS); when(reference.getBaseItem().getId()).thenReturn(10); when(room.getHabboItem(90)).thenReturn(reference);
        for (int n = 1; n <= 10; n++) storage.addFurniItem(item(n, ""));
        when(chest.previewKeys()).thenReturn(storage.furniItems().stream().map(InteractionWiredChestFurni::previewKey).limit(4).collect(java.util.stream.Collectors.toSet()));
        var config = new WiredChestConfiguration(); config.typeIds = List.of(90); config.variableId = 33;
        var scanner = new WiredExtraScanChestFurniByType(1, 2, mock(Item.class), "", 0, 0);
        try (var sources = mockStatic(WiredSourceUtil.class); var variables = mockStatic(WiredContextVariableSupport.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection())).thenAnswer(call -> new ArrayList<>((Collection<HabboItem>)call.getArgument(2)));
            variables.when(() -> WiredContextVariableSupport.hasDefinition(room, 33)).thenReturn(true);
            scanner.saveData(settings(config, 1), null); scanner.scan(ctx);
            variables.verify(() -> WiredContextVariableSupport.assignVariable(ctx, room, 33, 0, true));
            scanner.saveData(settings(config, 0), null); scanner.scan(ctx);
            variables.verify(() -> WiredContextVariableSupport.assignVariable(ctx, room, 33, 1, true));
        }
    }
}

package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.ContractConfiguration;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContractTrade;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectCancelTransaction;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectInitTransaction;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTransactionComplete;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTransactionFail;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableScope;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredContractTransactionComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredContractTransactionsTest {
    private Habbo user(int id) {
        var user = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(user.getHabboInfo().getId()).thenReturn(id);
        when(user.getHabboInfo().getUsername()).thenReturn("Tester");
        when(user.getInventory().getItemsComponent().getItemsAsValueCollection())
                .thenReturn(Set.of());
        return user;
    }

    private InteractionWiredChestCurrency chest(int id) {
        var chest = mock(InteractionWiredChestCurrency.class);
        when(chest.getId()).thenReturn(id);
        when(chest.getContents()).thenReturn(new ChestStorage());
        return chest;
    }

    private WiredTransactionExecutor.Entry entry(InteractionWiredChest chest, int direction, int type, int amount) {
        return new WiredTransactionExecutor.Entry(chest, ContractTerm.currency(direction, type, amount));
    }

    @Test
    void cumulativePaymentsCannotSpendTheSameBalanceTwice() {
        var user = user(1);
        var chest = chest(10);
        try (var wallet = mockStatic(ChestWiredCurrencyUtil.class)) {
            wallet.when(() -> ChestWiredCurrencyUtil.getBalance(user, -1)).thenReturn(10);
            assertFalse(WiredTransactionExecutor.executeEntries(
                    user, List.of(entry(chest, 0, -1, 6), entry(chest, 0, -1, 6))));
            assertEquals(0, chest.getContents().total(0));
            wallet.verify(() -> ChestWiredCurrencyUtil.take(any(), anyInt(), anyInt()), never());
        }
    }

    @Test
    void cumulativeRewardsCannotReuseTheSameStock() {
        var user = user(2);
        var chest = chest(10);
        chest.getContents().add(0, 5, 10);
        try (var wallet = mockStatic(ChestWiredCurrencyUtil.class)) {
            assertFalse(WiredTransactionExecutor.executeEntries(
                    user, List.of(entry(chest, 1, 5, 6), entry(chest, 1, 5, 6))));
            assertEquals(10, chest.getContents().count(0, 5));
            wallet.verify(() -> ChestWiredCurrencyUtil.give(any(), anyInt(), anyInt()), never());
        }
    }

    @Test
    void fullChestRejectsTheEntirePaymentAcrossCurrencies() {
        var user = user(3);
        var chest = chest(10);
        chest.getContents().setCapacity(10);
        try (var wallet = mockStatic(ChestWiredCurrencyUtil.class)) {
            wallet.when(() -> ChestWiredCurrencyUtil.getBalance(eq(user), anyInt()))
                    .thenReturn(100);
            assertFalse(WiredTransactionExecutor.executeEntries(
                    user, List.of(entry(chest, 0, -1, 6), entry(chest, 0, 5, 6))));
            assertEquals(0, chest.getContents().total(0));
            wallet.verify(() -> ChestWiredCurrencyUtil.take(any(), anyInt(), anyInt()), never());
        }
    }

    @Test
    void noChestNeverMintsAReward() {
        var user = user(4);
        try (var wallet = mockStatic(ChestWiredCurrencyUtil.class)) {
            assertFalse(WiredTransactionExecutor.executeEntries(user, List.of(entry(null, 1, -1, 100))));
            wallet.verifyNoInteractions();
        }
    }

    @Test
    void allThreeCurrenciesCommitTogetherAndRecordTheirTypes() {
        var user = user(5);
        var chest = chest(10);
        chest.getContents().add(0, 0, 20);
        chest.getContents().add(0, 5, 10);
        try (var wallet = mockStatic(ChestWiredCurrencyUtil.class)) {
            wallet.when(() -> ChestWiredCurrencyUtil.getBalance(eq(user), anyInt()))
                    .thenReturn(100);
            wallet.when(() -> ChestWiredCurrencyUtil.exchange(eq(user), anyMap(), anyMap()))
                    .thenReturn(true);
            assertTrue(WiredTransactionExecutor.executeEntries(
                    user, List.of(entry(chest, 0, -1, 30), entry(chest, 1, 0, 8), entry(chest, 1, 5, 6))));
            assertEquals(30, chest.getContents().count(0, -1));
            assertEquals(12, chest.getContents().count(0, 0));
            assertEquals(4, chest.getContents().count(0, 5));
            wallet.verify(() -> ChestWiredCurrencyUtil.exchange(user, Map.of(-1, 30L), Map.of(0, 8L, 5, 6L)));
            verify(chest).persistContents();
        }
    }

    @Test
    void aFailedAtomicWalletBatchLeavesChestUntouched() {
        var user = user(6);
        var chest = chest(10);
        try (var wallet = mockStatic(ChestWiredCurrencyUtil.class)) {
            wallet.when(() -> ChestWiredCurrencyUtil.getBalance(eq(user), anyInt()))
                    .thenReturn(100);
            wallet.when(() -> ChestWiredCurrencyUtil.exchange(eq(user), anyMap(), anyMap()))
                    .thenReturn(false);
            assertFalse(WiredTransactionExecutor.executeEntries(
                    user, List.of(entry(chest, 0, -1, 10), entry(chest, 0, 5, 10))));
            assertEquals(0, chest.getContents().total(0));
            wallet.verify(() -> ChestWiredCurrencyUtil.give(eq(user), anyInt(), anyInt()), never());
            verify(chest, never()).persistContents();
        }
    }

    @Test
    void walletExchangeUsesOneLedgerBatchForDebitsAndRewards() {
        var user = user(8);
        var plugin = mock(com.eu.habbo.plugin.PluginManager.class);
        when(plugin.fireEvent(any())).thenAnswer(call -> call.getArgument(0));
        try (var emulator = mockStatic(com.eu.habbo.Emulator.class);
                var ledger = mockStatic(LedgerWalletMutation.class)) {
            emulator.when(com.eu.habbo.Emulator::getPluginManager).thenReturn(plugin);
            assertTrue(ChestWiredCurrencyUtil.exchange(user, Map.of(-1, 10L, 0, 5L), Map.of(-1, 2L, 0, 3L, 5, 7L)));
            ledger.verify(
                    () -> LedgerWalletMutation.executeBatch(
                            eq(user),
                            argThat(operations -> operations.size() == 5
                                    && operations.stream()
                                                    .filter(op -> op.currencyType() == -1)
                                                    .mapToInt(op -> op.delta())
                                                    .sum()
                                            == -8
                                    && operations.stream()
                                                    .filter(op -> op.currencyType() == 0)
                                                    .mapToInt(op -> op.delta())
                                                    .sum()
                                            == -2
                                    && operations.stream()
                                                    .filter(op -> op.currencyType() == 5)
                                                    .mapToInt(op -> op.delta())
                                                    .sum()
                                            == 7)),
                    times(1));
        }
    }

    @Test
    void failedLedgerBatchDoesNotPublishUpdatedBalances() throws Exception {
        var user = user(9);
        var plugin = mock(com.eu.habbo.plugin.PluginManager.class);
        when(plugin.fireEvent(any())).thenAnswer(call -> call.getArgument(0));
        try (var emulator = mockStatic(com.eu.habbo.Emulator.class);
                var ledger = mockStatic(LedgerWalletMutation.class)) {
            emulator.when(com.eu.habbo.Emulator::getPluginManager).thenReturn(plugin);
            ledger.when(() -> LedgerWalletMutation.executeBatch(eq(user), anyList()))
                    .thenThrow(new java.sql.SQLException("rollback"));
            assertFalse(ChestWiredCurrencyUtil.exchange(user, Map.of(-1, 10L, 5, 2L), Map.of(0, 5L)));
            verify(user.getClient(), never()).sendResponse(any(MessageComposer.class));
        }
    }

    @Test
    void registeredContractsReadAndPersistFurnitureTermsInsteadOfMisreadingTheFiveIntegerStride() throws Exception {
        var contract = new InteractionWiredContractTrade(20, 1, mock(Item.class), "", 0, 0);
        var settings = new WiredSettings(new int[0], "", new int[0], 0, 0);
        settings.setIntParams(new int[] {2, 0, 1, 0, 44, 3, 1, 0, 5, 0, 7});
        settings.setFurniIds(new int[] {10});
        settings.setStringParam("{\"version\":1,\"receiveText\":\"Test\"}");
        assertTrue(contract.saveData(settings, null));
        assertEquals(44, contract.getAssetTerms().get(0).baseItemId);
        assertEquals(3, contract.getAssetTerms().get(0).amount);
        assertEquals(5, contract.getAssetTerms().get(1).currencyType);
        var row = mock(java.sql.ResultSet.class);
        when(row.getString("wired_data")).thenReturn(contract.getWiredData());
        var loaded = new InteractionWiredContractTrade(20, 1, mock(Item.class), "", 0, 0);
        loaded.loadWiredData(row, mock(Room.class));
        assertEquals(44, loaded.getAssetTerms().get(0).baseItemId);
        assertEquals("Test", loaded.getConfiguration().receiveText);
    }

    @Test
    void contractElementAmountsRespectTheNativeEditorLimits() throws Exception {
        var contract = new InteractionWiredContractTrade(20, 1, mock(Item.class), "", 0, 0);
        var settings = new WiredSettings(new int[0], "", new int[0], 0, 0);
        settings.setIntParams(new int[] {1, 0, 1, 0, 44, 500});
        assertTrue(contract.saveData(settings, null));
        settings.setIntParams(new int[] {1, 0, 1, 0, 44, 501});
        assertFalse(contract.saveData(settings, null));
        assertEquals(500, contract.getAssetTerms().getFirst().amount);
        settings.setIntParams(new int[] {1, 0, 0, 5, 0, 100000});
        assertTrue(contract.saveData(settings, null));
        settings.setIntParams(new int[] {1, 0, 0, 5, 0, 100001});
        assertFalse(contract.saveData(settings, null));
    }

    @Test
    void transactionTriggersOnlyMatchTheSelectedContract() {
        var contract = mock(HabboItem.class);
        when(contract.getId()).thenReturn(20);
        var other = mock(HabboItem.class);
        when(other.getId()).thenReturn(21);
        var room = mock(Room.class);
        var fail = new WiredTriggerTransactionFail(1, 1, mock(Item.class), "", 0, 0);
        var success = new WiredTriggerTransactionComplete(2, 1, mock(Item.class), "", 0, 0);
        var settings = new WiredSettings(new int[0], "", new int[0], 0, 0);
        settings.setFurniIds(new int[] {20});
        fail.saveData(settings);
        success.saveData(settings);
        assertTrue(fail.matches(
                null,
                WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAIL, room)
                        .sourceItem(contract)
                        .build()));
        assertFalse(fail.matches(
                null,
                WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAIL, room)
                        .sourceItem(other)
                        .build()));
        assertTrue(success.matches(
                null,
                WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETE, room)
                        .sourceItem(contract)
                        .build()));
    }

    @Test
    void confirmationIsRequiredAndReplayCannotApplyASecondTransaction() {
        session(false);
    }

    @Test
    void cancelClosesTheOfferWithoutExecutingItsTerms() {
        session(true);
    }

    private void session(boolean cancel) {
        var user = user(cancel ? 102 : 101);
        var room = mock(Room.class);
        when(room.getId()).thenReturn(9);
        when(user.getHabboInfo().getCurrentRoom()).thenReturn(room);
        var ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);
        when(ctx.contextVariables()).thenReturn(new WiredContextVariableScope());
        var contract = mock(com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.class);
        when(contract.getId()).thenReturn(20);
        when(contract.getAssetTerms()).thenReturn(List.of(ContractTerm.currency(0, -1, 10)));
        when(contract.getConfiguration()).thenReturn(new ContractConfiguration());
        when(contract.getWiredData()).thenReturn("{}");
        var chest = chest(10);
        when(room.getHabboItem(10)).thenReturn(chest);
        when(room.getHabboItem(20)).thenReturn(contract);
        when(room.getHabbo(user.getRoomUnit())).thenReturn(user);
        when(room.getItemsAt(anyInt(), anyInt())).thenReturn(Set.of());
        var effect = new WiredEffectInitTransaction(30, 1, mock(Item.class), "", 0, 0);
        var unit = user.getRoomUnit();
        try (var source = mockStatic(WiredSourceUtil.class);
                var execution = mockStatic(WiredTransactionExecutor.class)) {
            source.when(() -> WiredSourceUtil.resolveItems(eq(ctx), eq(100), anyCollection()))
                    .thenAnswer(c -> new ArrayList<>((Collection<HabboItem>) c.getArgument(2)));
            source.when(() -> WiredSourceUtil.resolveUsers(ctx, 0)).thenReturn(List.of(unit));
            execution
                    .when(() -> WiredTransactionExecutor.validateEntries(eq(user), anyList()))
                    .thenReturn(true);
            execution
                    .when(() -> WiredTransactionExecutor.executeEntries(eq(user), anyList()))
                    .thenReturn(true);
            WiredContractTransactions.start(ctx, effect, List.of(10, 20), new int[] {0, 1, 0, -20, 0, 300}, "");
            var packets = org.mockito.ArgumentCaptor.forClass(MessageComposer.class);
            verify(user.getClient()).sendResponse(packets.capture());
            var offer = WiredManager.getGson()
                    .fromJson(
                            ((WiredContractTransactionComposer) packets.getValue()).getOffer(),
                            WiredContractTransactions.Offer.class);
            execution.verify(() -> WiredTransactionExecutor.executeEntries(any(), anyList()), never());
            WiredContractTransactions.respond(user, "forged", 2, 0, 0, -1);
            execution.verify(() -> WiredTransactionExecutor.executeEntries(any(), anyList()), never());
            if (cancel) {
                WiredContractTransactions.respond(user, offer.token, 0, 0, 0, -1);
            } else {
                WiredContractTransactions.respond(user, offer.token, 1, 0, 0, -1);
                execution.verify(() -> WiredTransactionExecutor.executeEntries(any(), anyList()), never());
                WiredContractTransactions.respond(user, offer.token, 2, 0, 0, -1);
                WiredContractTransactions.respond(user, offer.token, 2, 0, 0, -1);
            }
            execution.verify(
                    () -> WiredTransactionExecutor.executeEntries(eq(user), anyList()), cancel ? never() : times(1));
        }
    }

    @Test
    void transactionTriggerSourcesSurviveReloadAndResolveTheEventContract() throws Exception {
        var room = mock(Room.class);
        var contract = mock(HabboItem.class);
        when(contract.getId()).thenReturn(20);
        var settings = new WiredSettings(new int[0], "{\"version\":1,\"furniSource\":0}", new int[0], 0, 0);
        var success = new WiredTriggerTransactionComplete(2, 1, mock(Item.class), "", 0, 0);
        var fail = new WiredTriggerTransactionFail(1, 1, mock(Item.class), "", 0, 0);
        success.saveData(settings);
        fail.saveData(settings);
        var row = mock(java.sql.ResultSet.class);
        when(row.getString("wired_data")).thenReturn(success.getWiredData());
        success.loadWiredData(row, room);
        when(row.getString("wired_data")).thenReturn(fail.getWiredData());
        fail.loadWiredData(row, room);
        assertTrue(success.matches(
                success,
                WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETE, room)
                        .sourceItem(contract)
                        .build()));
        assertTrue(fail.matches(
                fail,
                WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAIL, room)
                        .sourceItem(contract)
                        .build()));
        assertFalse(fail.matches(
                fail, WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAIL, room).build()));
    }

    @Test
    void selectedContractLegacyTriggerDataStillLoads() throws Exception {
        var trigger = new WiredTriggerTransactionComplete(1, 1, mock(Item.class), "", 0, 0);
        var room = mock(Room.class);
        var row = mock(java.sql.ResultSet.class);
        var contract = mock(HabboItem.class);
        when(contract.getId()).thenReturn(20);
        when(row.getString("wired_data")).thenReturn("[20]");
        trigger.loadWiredData(row, room);
        assertTrue(trigger.matches(
                trigger,
                WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETE, room)
                        .sourceItem(contract)
                        .build()));
    }

    @Test
    void cancelPreservesBothSourcesAndPassesThemToPendingTransactionResolution() throws Exception {
        var effect = new WiredEffectCancelTransaction(1, 1, mock(Item.class), "", 0, 0);
        var settings = new WiredSettings(
                new int[] {0}, "{\"version\":1,\"furniSource\":200,\"userSource\":201}", new int[] {20}, 0, 0);
        effect.saveData(settings, null);
        var row = mock(java.sql.ResultSet.class);
        when(row.getString("wired_data")).thenReturn(effect.getWiredData());
        effect.loadWiredData(row, mock(Room.class));
        var ctx = mock(WiredContext.class);
        try (var sessions = mockStatic(WiredContractTransactions.class)) {
            effect.execute(ctx);
            sessions.verify(() -> WiredContractTransactions.cancel(
                    eq(ctx),
                    eq(0),
                    eq(List.of(20)),
                    argThat(source -> source.furniSource == 200 && source.userSource == 201)));
        }
    }

    @Test
    void selectorOriginMatchesOnlyTheResolvedContract() {
        var room = mock(Room.class);
        var contract = mock(HabboItem.class);
        var other = mock(HabboItem.class);
        when(contract.getId()).thenReturn(20);
        when(other.getId()).thenReturn(21);
        var sources = WiredTransactionSources.read("{\"version\":1,\"furniSource\":200}");
        try (var resolver = mockStatic(WiredSourceUtil.class)) {
            resolver.when(() -> WiredSourceUtil.resolveItems(any(WiredContext.class), eq(200), anyCollection()))
                    .thenReturn(List.of(contract));
            assertTrue(sources.matches(
                    null,
                    WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETE, room)
                            .sourceItem(contract)
                            .build(),
                    List.of()));
            assertFalse(sources.matches(
                    null,
                    WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETE, room)
                            .sourceItem(other)
                            .build(),
                    List.of()));
        }
    }
}

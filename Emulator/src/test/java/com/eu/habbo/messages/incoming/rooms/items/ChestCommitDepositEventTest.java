package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestDepositSession;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestWiredCurrencyUtil;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ClientMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChestCommitDepositEventTest {
    private org.mockito.MockedStatic<com.eu.habbo.habbohotel.wired.core.WiredManager> wired;

    @org.junit.jupiter.api.BeforeEach
    void captureEvents() {
        wired = mockStatic(com.eu.habbo.habbohotel.wired.core.WiredManager.class);
        wired.when(com.eu.habbo.habbohotel.wired.core.WiredManager::getGson).thenCallRealMethod();
    }

    private void completedExactlyOnce(ChestCommitDepositEvent handler, InteractionWiredChest chest) {
        var captor = org.mockito.ArgumentCaptor.forClass(com.eu.habbo.habbohotel.wired.core.WiredEvent.class);
        wired.verify(
                () -> com.eu.habbo.habbohotel.wired.core.WiredManager.dispatchEffectTriggeredEvent(captor.capture()),
                times(1));
        var event = captor.getValue();
        assertEquals(com.eu.habbo.habbohotel.wired.core.WiredEvent.Type.TRANSACTION_COMPLETE, event.getType());
        assertSame(chest, event.getSourceItem().orElseThrow());
        assertSame(handler.client.getHabbo().getRoomUnit(), event.getActor().orElseThrow());

        var trigger = new com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerTransactionComplete(
                99, 7, mock(Item.class), "", 0, 0);
        trigger.saveData(new com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings(
                new int[0], "", new int[] {chest.getId()}, 0, 0));
        assertTrue(trigger.matches(trigger, event));
        wired.clearInvocations();
    }

    @Test
    void currencyDepositsDebitAndStoreTheSelectedWallet() throws Exception {
        for (int currency : new int[] {-1, 0, 5}) {
            var chest = spy(new InteractionWiredChestCurrency(17, 7, mock(Item.class), "0", 0, 0));
            doNothing().when(chest).persistContents(any(Room.class));
            var handler = request(chest, 3, 0, currency);
            when(handler.packet.bytesAvailable()).thenReturn(4);
            try (var wallet = mockStatic(ChestWiredCurrencyUtil.class)) {
                wallet.when(() -> ChestWiredCurrencyUtil.take(handler.client.getHabbo(), currency, 3))
                        .thenReturn(true);
                handler.handle();
                wallet.verify(() -> ChestWiredCurrencyUtil.take(handler.client.getHabbo(), currency, 3));
            }
            assertEquals(3, chest.getContents().count(ChestStorage.KIND_CURRENCY, currency));
            assertEquals(3, chest.getContents().total(ChestStorage.KIND_CURRENCY));
            completedExactlyOnce(handler, chest);
        }
    }

    @Test
    void unsupportedDepositCurrencyCannotDebitAnyWallet() throws Exception {
        var chest = new InteractionWiredChestCurrency(17, 7, mock(Item.class), "0", 0, 0);
        var handler = request(chest, 3, 0, 99);
        when(handler.packet.bytesAvailable()).thenReturn(4);
        try (var wallet = mockStatic(ChestWiredCurrencyUtil.class)) {
            handler.handle();
            wallet.verifyNoInteractions();
        }
        assertEquals(0, chest.getContents().total(ChestStorage.KIND_CURRENCY));
        wired.verify(
                () -> com.eu.habbo.habbohotel.wired.core.WiredManager.dispatchEffectTriggeredEvent(
                        any(com.eu.habbo.habbohotel.wired.core.WiredEvent.class)),
                never());
    }

    @AfterEach
    void clearSession() {
        ChestDepositSession.clear(7);
        wired.close();
    }

    private ChestCommitDepositEvent request(InteractionWiredChest chest, int credits, int count, Integer... ids) {
        var habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(habbo.getHabboInfo().getId()).thenReturn(7);
        var room = mock(Room.class);
        when(habbo.getHabboInfo().getCurrentRoom()).thenReturn(room);
        when(room.getHabboItem(17)).thenReturn(chest);
        var handler = new ChestCommitDepositEvent();
        handler.client = mock(GameClient.class);
        when(handler.client.getHabbo()).thenReturn(habbo);
        handler.packet = mock(ClientMessage.class);
        var values = new java.util.ArrayDeque<Integer>();
        values.add(17);
        values.add(credits);
        values.add(count);
        java.util.Collections.addAll(values, ids);
        when(handler.packet.readInt()).thenAnswer(invocation -> values.remove());
        ChestDepositSession.start(7, 17);
        return handler;
    }

    @Test
    void cancellationNeverTouchesInventoryOrChestContents() throws Exception {
        var chest = new InteractionWiredChestFurni(17, 7, mock(Item.class), "0", 0, 0);
        var handler = request(chest, 0, 0);
        handler.handle();
        verify(handler.client.getHabbo(), never()).getInventory();
        assertEquals(0, chest.getContents().furniItemCount());
        assertTrue(chest.getContents().getLog().isEmpty());
        assertEquals(0, ChestDepositSession.getChestItemId(7));
        wired.verify(
                () -> com.eu.habbo.habbohotel.wired.core.WiredManager.dispatchEffectTriggeredEvent(
                        any(com.eu.habbo.habbohotel.wired.core.WiredEvent.class)),
                never());
    }

    @Test
    void wholeOfferIsRejectedWhenCapacityCannotHoldIt() throws Exception {
        var chest = new InteractionWiredChestFurni(17, 7, mock(Item.class), "0", 0, 0);
        chest.getContents().setCapacity(1);
        var handler = request(chest, 0, 2, 11, 12);
        handler.handle();
        verify(handler.client.getHabbo().getInventory().getItemsComponent(), never())
                .removeHabboItem(any(com.eu.habbo.habbohotel.users.HabboItem.class));
        assertEquals(0, chest.getContents().furniItemCount());
        assertTrue(chest.getContents().getLog().isEmpty());
    }

    @Test
    void duplicateIdsCannotRemoveTheSameItemTwice() throws Exception {
        var chest = new InteractionWiredChestFurni(17, 7, mock(Item.class), "0", 0, 0);
        var handler = request(chest, 0, 2, 11, 11);
        handler.handle();
        verify(handler.client.getHabbo(), never()).getInventory();
        assertTrue(chest.getContents().getLog().isEmpty());
    }

    @Test
    void currencyOfferOverCapacityNeverDebitsWallet() throws Exception {
        var chest = new InteractionWiredChestCurrency(17, 7, mock(Item.class), "0", 0, 0);
        chest.getContents().setCapacity(5);
        var handler = request(chest, 6, 0);
        handler.handle();
        verify(handler.client.getHabbo().getHabboInfo(), never()).getCredits();
        assertEquals(0, chest.getContents().total(ChestStorage.KIND_CURRENCY));
        wired.verify(
                () -> com.eu.habbo.habbohotel.wired.core.WiredManager.dispatchEffectTriggeredEvent(
                        any(com.eu.habbo.habbohotel.wired.core.WiredEvent.class)),
                never());
    }

    @Test
    void confirmedFurniOfferTransfersOnceAndWritesOneTransaction() throws Exception {
        var chest = new InteractionWiredChestFurni(17, 7, mock(Item.class), "0", 0, 0);
        var handler = request(chest, 0, 1, 11);
        var inventory = handler.client.getHabbo().getInventory().getItemsComponent();
        var map = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<com.eu.habbo.habbohotel.users.HabboItem>();
        var base = mock(Item.class);
        when(base.getType()).thenReturn(com.eu.habbo.habbohotel.items.FurnitureType.FLOOR);
        when(base.getId()).thenReturn(99);
        when(base.getSpriteId()).thenReturn(14153);
        var item = mock(com.eu.habbo.habbohotel.users.HabboItem.class);
        when(item.getId()).thenReturn(11);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getExtradata()).thenReturn("42");
        map.put(11, item);
        when(inventory.getItems()).thenReturn(map);
        when(inventory.getHabboItem(anyInt())).thenAnswer(call -> map.get((int) call.getArgument(0)));
        doAnswer(call -> {
                    map.remove(((com.eu.habbo.habbohotel.users.HabboItem) call.getArgument(0)).getId());
                    return null;
                })
                .when(inventory)
                .removeHabboItem(any(com.eu.habbo.habbohotel.users.HabboItem.class));
        try (var emulator = mockStatic(com.eu.habbo.Emulator.class)) {
            emulator.when(com.eu.habbo.Emulator::getThreading)
                    .thenReturn(mock(com.eu.habbo.threading.ThreadPooling.class));
            handler.handle();
        }
        assertEquals(0, map.size());
        assertEquals(1, chest.getContents().furniItemCount());
        assertEquals("42", chest.getContents().furniItems().getFirst().extradata);
        assertEquals(1, chest.getContents().getLog().size());
        assertArrayEquals(new int[] {14153}, chest.getContents().getLog().getFirst().furniTypeIds);
        assertEquals(0, ChestDepositSession.getChestItemId(7));
        completedExactlyOnce(handler, chest);
    }
}

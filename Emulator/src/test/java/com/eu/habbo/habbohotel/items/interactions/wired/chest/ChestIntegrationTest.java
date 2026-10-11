package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import org.junit.jupiter.api.Test;

class ChestIntegrationTest {
    @Test
    void furniAppearanceSendsDistinctNativeIconTypesAndHidesThemWhenClosed() {
        var chest = new InteractionWiredChestFurni(10, 7, mock(Item.class), "1", 0, 0);
        chest.addViewer(7, mock(Room.class));
        for (int index = 0; index < 6; index++) {
            var row = new ChestFurniStoredItem();
            row.baseItemId = 100 + index;
            row.spriteId = 14000 + index;
            row.inventoryId = 200 + index;
            chest.getContents().tryDepositFurni(row);
        }
        var message = mock(com.eu.habbo.messages.ServerMessage.class);
        chest.serializeExtradata(message);
        verify(message).appendString("false,14000;false,14001;false,14002;false,14003");
        verify(message).appendString("visuals");
        chest.setExtradata("0");
        var closed = mock(com.eu.habbo.messages.ServerMessage.class);
        chest.serializeExtradata(closed);
        verify(closed).appendString("");
    }

    @Test
    void currencyAppearanceUsesAllNativeBalanceStatesAndClosesAfterLastViewer() {
        Room room = mock(Room.class);
        var chest = new InteractionWiredChestCurrency(10, 7, mock(Item.class), "0", 0, 0);
        chest.addViewer(7, room);
        assertEquals("1", chest.getExtradata());
        chest.getContents().depositCurrency(-1, 1);
        chest.updateAppearanceState(room);
        assertEquals("2", chest.getExtradata());
        chest.getContents().depositCurrency(-1, 19);
        chest.updateAppearanceState(room);
        assertEquals("3", chest.getExtradata());
        chest.getContents().depositCurrency(-1, 80);
        chest.updateAppearanceState(room);
        assertEquals("4", chest.getExtradata());
        chest.addViewer(8, room);
        chest.removeViewer(7, room);
        assertEquals("4", chest.getExtradata());
        chest.removeViewer(8, room);
        assertEquals("0", chest.getExtradata());
        chest.getContents().setAppearanceState(1);
        chest.updateAppearanceState(room);
        assertEquals("4", chest.getExtradata());
        chest.getContents().setAppearanceState(2);
        chest.updateAppearanceState(room);
        assertEquals("0", chest.getExtradata());
    }

    @Test
    void furniAppearanceNeverSendsTheUnsupportedStateTwo() {
        Room room = mock(Room.class);
        var chest = new InteractionWiredChestFurni(10, 7, mock(Item.class), "0", 0, 0);
        chest.addViewer(7, room);
        assertEquals("1", chest.getExtradata());
        chest.getContents().tryDepositFurni(new ChestFurniStoredItem());
        chest.updateAppearanceState(room);
        assertEquals("1", chest.getExtradata());
        chest.removeViewer(7, room);
        assertEquals("0", chest.getExtradata());
    }

    private Habbo user(int id) {
        Habbo user = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(user.getHabboInfo().getId()).thenReturn(id);
        return user;
    }

    @Test
    void locksAndCapacitySurvivePersistenceWithoutLosingContents() {
        ChestStorage storage = new ChestStorage();
        storage.depositCurrency(-1, 40);
        storage.setLocked(true);
        storage.setAutoLock(true);
        storage.setCapacity(100);
        ChestStorage restored = ChestStorage.fromJson(storage.toJson());
        assertTrue(restored.isLocked());
        assertTrue(restored.isAutoLock());
        assertEquals(100, restored.getCapacity());
        assertEquals(40, restored.total(ChestStorage.KIND_CURRENCY));
        assertEquals(60, restored.depositCurrency(-1, 1000));
        assertEquals(0, restored.depositCurrency(-1, 1));
    }

    @Test
    void capacityOverrideCanDisableDepositsAndCannotExceedPurchasedCapacity() {
        ChestStorage storage = new ChestStorage();
        storage.setCapacity(0);
        assertEquals(0, storage.depositCurrency(-1, 1));
        assertFalse(storage.tryDepositFurni(new ChestFurniStoredItem()));
        storage.setCapacity(Integer.MAX_VALUE);
        assertEquals(storage.getCapacityMax(), storage.getCapacity());
        assertEquals(5000, ChestStorage.fromJson("{\"entries\":[]}").getCapacity());
    }

    @Test
    void ownerCanOperateLockedChestButRoomRightsCannotBypassLock() {
        var chest = new InteractionWiredChestCurrency(10, 7, mock(Item.class), "0", 0, 0);
        Room room = mock(Room.class);
        Habbo owner = user(7), editor = user(8), visitor = user(9);
        when(room.canModifyWired(editor)).thenReturn(true);
        assertTrue(chest.canWithdraw(owner, room));
        assertTrue(chest.canWithdraw(editor, room));
        assertFalse(chest.canWithdraw(visitor, room));
        chest.getContents().setLocked(true);
        assertTrue(chest.canWithdraw(owner, room));
        assertFalse(chest.canWithdraw(editor, room));
        assertFalse(chest.canDeposit(editor, room));
        assertTrue(chest.canManage(owner));
        assertFalse(chest.canManage(editor));
    }

    @Test
    void donationAccessDoesNotBypassLock() {
        var chest = new InteractionWiredChestFurni(10, 7, mock(Item.class), "0", 0, 0);
        Habbo visitor = user(9);
        Room room = mock(Room.class);
        chest.getContents().setAccessDonate(true);
        assertTrue(chest.canDeposit(visitor, room));
        chest.getContents().setLocked(true);
        assertFalse(chest.canDeposit(visitor, room));
    }

    @Test
    void obsoleteWiredConfigurationCannotReplaceOrMintStoredContents() throws Exception {
        for (InteractionWiredChest chest : new InteractionWiredChest[] {
            new InteractionWiredChestCurrency(10, 7, mock(Item.class), "0", 0, 0),
            new InteractionWiredChestFurni(11, 7, mock(Item.class), "0", 0, 0)
        }) {
            chest.getContents().depositCurrency(-1, 20);
            String before = chest.getWiredData();
            assertFalse(chest.saveData(new WiredSettings(new int[] {-1, 99999}, "", new int[0], 0), null));
            assertEquals(before, chest.getWiredData());
        }
    }

    @Test
    void playerStateRetainsLegacyPrefixAndAppendsNativeFooterPermissions() {
        Item item = mock(Item.class);
        var chest = new InteractionWiredChestCurrency(10, 7, item, "0", 0, 0);
        chest.getContents().setLocked(true);
        chest.getContents().setAutoLock(true);
        chest.getContents().setCapacity(100);
        var message = new ChestDataComposer(chest, user(7)).compose();
        var buffer = message.get();
        try {
            var tail = buffer.duplicate();
            tail.readerIndex(tail.writerIndex() - 9);
            assertTrue(tail.readBoolean());
            assertTrue(tail.readBoolean());
            assertEquals(100, tail.readInt());
            assertTrue(tail.readBoolean());
            assertTrue(tail.readBoolean());
            assertTrue(tail.readBoolean());
        } finally {
            buffer.release();
        }
    }

    @Test
    void chestControlsUseTheAuthoritativeWiredMenuPermissions() {
        var chest = new InteractionWiredChestCurrency(10, 7, mock(Item.class), "0", 0, 0);
        Room room = mock(Room.class);
        Habbo editor = user(8), inspector = user(9);
        when(room.canModifyWired(editor)).thenReturn(true);
        when(room.canInspectWired(inspector)).thenReturn(true);
        assertTrue(chest.canEdit(editor, room));
        assertTrue(chest.canWithdraw(editor, room));
        assertTrue(chest.canRead(inspector, room));
        assertFalse(chest.canEdit(inspector, room));
        assertFalse(chest.canWithdraw(inspector, room));
    }

    @Test
    void furniLogFiltersArePersistedAndSentByTheEmulator() {
        var chest = new InteractionWiredChestFurni(10, 7, mock(Item.class), "0", 0, 0);
        var log = new ChestStorage.LogEntry("deposit", 123000, "Loren", 0, 2);
        log.furniTypeIds = new int[] {14153};
        chest.getContents().addLog(log);
        assertArrayEquals(
                log.furniTypeIds,
                ChestStorage.fromJson(chest.getContents().toJson()).getLog().getFirst().furniTypeIds);
        var buffer = new com.eu.habbo.messages.outgoing.rooms.items.ChestLogComposer(chest)
                .compose()
                .get();
        try {
            var tail = buffer.duplicate();
            tail.readerIndex(tail.writerIndex() - 21);
            assertEquals(1, tail.readInt());
            assertEquals(1, tail.readInt());
            assertEquals(14153, tail.readInt());
            assertEquals(1, tail.readInt());
            assertFalse(tail.readBoolean());
            assertEquals(0, tail.readInt());
        } finally {
            buffer.release();
        }
    }

    @Test
    void wiredFurniRewardsRemoveRealStoredRowsAndKeepTheirMetadata() {
        var chest = new InteractionWiredChestFurni(10, 7, mock(Item.class), "0", 0, 0);
        var stored = new ChestFurniStoredItem();
        stored.baseItemId = 99;
        stored.spriteId = 100;
        stored.extradata = "42";
        stored.limitedStack = 7;
        stored.limitedSells = 20;
        chest.getContents().addFurniItem(stored);
        Habbo recipient = user(8);
        Item base = mock(Item.class);
        var created = mock(com.eu.habbo.habbohotel.users.HabboItem.class);
        var environment = mock(com.eu.habbo.habbohotel.GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getItemManager().getItem(99)).thenReturn(base);
        when(environment.getItemManager().createItem(8, base, 7, 20, "42")).thenReturn(created);
        try (var emulator = mockStatic(com.eu.habbo.Emulator.class)) {
            emulator.when(com.eu.habbo.Emulator::getGameEnvironment).thenReturn(environment);
            assertEquals(1, ChestWiredFurniUtil.giveFromChestByType(recipient, chest, false, 99, "", 10));
        }
        assertEquals(0, chest.getContents().furniItemCount());
        assertEquals(0, chest.getContents().count(ChestStorage.KIND_FURNI, 99));
        assertEquals("wired_withdraw", chest.getContents().getLog().get(0).type);
        verify(environment.getItemManager()).createItem(8, base, 7, 20, "42");
    }

    @Test
    void failedInventoryCreationRestoresTheStoredFurni() {
        var chest = new InteractionWiredChestFurni(10, 7, mock(Item.class), "0", 0, 0);
        var stored = new ChestFurniStoredItem();
        stored.baseItemId = 99;
        chest.getContents().addFurniItem(stored);
        var environment = mock(com.eu.habbo.habbohotel.GameEnvironment.class, RETURNS_DEEP_STUBS);
        Item base = mock(Item.class);
        when(environment.getItemManager().getItem(99)).thenReturn(base);
        when(environment.getItemManager().createItem(anyInt(), eq(base), anyInt(), anyInt(), any()))
                .thenReturn(null);
        try (var emulator = mockStatic(com.eu.habbo.Emulator.class)) {
            emulator.when(com.eu.habbo.Emulator::getGameEnvironment).thenReturn(environment);
            assertEquals(0, ChestWiredFurniUtil.giveFromChestByType(user(8), chest, false, 99, "", 1));
        }
        assertEquals(1, chest.getContents().furniItemCount());
        assertEquals(1, chest.getContents().count(ChestStorage.KIND_FURNI, 99));
        assertTrue(chest.getContents().getLog().isEmpty());
    }
}

package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import java.util.List;

/** Shared deposit logic for wired furni chests (bulk + per-inventory-item). */
public final class ChestFurniDepositHelper {
    private ChestFurniDepositHelper() {}

    public static boolean depositInventoryItem(Habbo habbo, InteractionWiredChest chest, HabboItem inventoryItem) {
        if (habbo == null || chest == null || inventoryItem == null) return false;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return false;

        ChestStorage contents = chest.getContents();
        if (!(chest instanceof InteractionWiredChestFurni) || !chest.canDeposit(habbo, room)) return false;

        Item baseItem = inventoryItem.getBaseItem();
        if (baseItem == null || (baseItem.getType() != FurnitureType.FLOOR && baseItem.getType() != FurnitureType.WALL)) return false;

        HabboItem removed = habbo.getInventory().getItemsComponent().getHabboItem(inventoryItem.getId());
        if (removed == null) return false;
        habbo.getInventory().getItemsComponent().removeHabboItem(removed);

        ChestFurniStoredItem stored = ChestFurniStoredItem.fromHabboItem(removed, removed.getId());

        // Atomic capacity-guarded add; if the chest is full, hand the item back rather than lose it.
        if (!contents.tryDepositFurni(stored)) {
            habbo.getInventory().getItemsComponent().addItem(removed);
            return false;
        }
        ChestStorage.LogEntry log = new ChestStorage.LogEntry("deposit", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), 0, 1);
        log.setFurniItems(java.util.stream.Stream.of(stored));
        contents.addLog(log);
        chest.persistContents(room);

        habbo.getClient().sendResponse(new RemoveHabboItemComposer(removed.getGiftAdjustedId()));
        habbo.getClient().sendResponse(new InventoryRefreshComposer());
        Emulator.getThreading().runPersistence(new QueryDeleteHabboItems(List.of(removed)));

        habbo.getClient().sendResponse(new ChestDataComposer(chest, habbo));
        ChestFurniPackets.sendDelta(habbo.getClient(), chest.getId(), List.of(), List.of(stored));
        return true;
    }
}

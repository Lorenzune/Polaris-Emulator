package com.eu.habbo.messages.incoming.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import java.util.HashMap;
import java.util.Map;

public class RequestInventoryItemsDelete extends MessageHandler {
    private static final int MAX_DELETE_AMOUNT = 1000;

    public int getRatelimit() {
        return 500;
    }

    public void handle() {
        int itemId = this.packet.readInt();
        int amount = this.packet.readInt();

        boolean batch = this.packet.bytesAvailable() >= 4;
        if (amount <= 0 || amount > MAX_DELETE_AMOUNT) {
            if (!batch || amount <= 0 || amount > 10000) return;
        }
        if (batch) {
            int count = this.packet.readInt();
            if (count <= 0 || count > 10000 || count != amount || this.packet.bytesAvailable() != count * 4) return;
            Habbo habbo = this.client.getHabbo();
            if (habbo == null) return;
            if (habbo.getRoomUnit().hasStatus(com.eu.habbo.habbohotel.rooms.RoomUnitStatus.TRADING)) return;
            Map<Integer, HabboItem> selected = new HashMap<>();
            for (int i = 0; i < count; i++) {
                HabboItem selectedItem = habbo.getInventory().getItemsComponent().getHabboItem(this.packet.readInt());
                if (selectedItem != null) selected.put(selectedItem.getId(), selectedItem);
            }
            Map<Integer, HabboItem> removed = new HashMap<>();
            for (HabboItem selectedItem : selected.values()) {
                habbo.getInventory().getItemsComponent().removeHabboItem(selectedItem);
                if (habbo.getInventory().getItemsComponent().getHabboItem(selectedItem.getId()) == null) {
                    removed.put(selectedItem.getId(), selectedItem);
                    habbo.getClient().sendResponse(new RemoveHabboItemComposer(selectedItem.getGiftAdjustedId()));
                }
            }
            if (!removed.isEmpty()) {
                habbo.getClient().sendResponse(new InventoryRefreshComposer());
                persistDeleted(removed.values());
            }
            return;
        }


        HabboItem habboItem =
                this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);
        if (habboItem == null) return;
        Item item = habboItem.getBaseItem();
        if (item == null) return;
        if (!hasFurnitureInInventory(this.client.getHabbo(), item, amount)) return;
        final Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;
        Map<Integer, HabboItem> toRemove = new HashMap<>();
        for (int i = 0; i < amount; i++) {
            HabboItem habboInventoryItem =
                    habbo.getInventory().getItemsComponent().getAndRemoveHabboItem(item);
            if (habboInventoryItem != null) toRemove.put(habboInventoryItem.getId(), habboInventoryItem);
        }
        toRemove.values().forEach(object -> {
            habbo.getClient().sendResponse(new RemoveHabboItemComposer(object.getGiftAdjustedId()));
        });
        habbo.getClient().sendResponse(new InventoryRefreshComposer());
        persistDeleted(toRemove.values());
    }

    private boolean hasFurnitureInInventory(Habbo habbo, Item item, Integer amount) {
        int count = 0;
        for (HabboItem habboItem : habbo.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (habboItem.getBaseItem().getId() == item.getId()) count++;
        }
        return count >= amount;
    }

    private void persistDeleted(java.util.Collection<HabboItem> items) {
        Emulator.getThreading().runPersistence(new QueryDeleteHabboItems(items));
    }
}

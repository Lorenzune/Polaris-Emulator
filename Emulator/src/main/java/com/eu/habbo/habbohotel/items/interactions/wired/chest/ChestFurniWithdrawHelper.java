package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import java.util.List;

public final class ChestFurniWithdrawHelper {
    private ChestFurniWithdrawHelper() {}

    public static int completeWithdraw(
            GameClient client, InteractionWiredChest chest, List<ChestFurniStoredItem> removedItems) {
        if (client == null || chest == null || removedItems == null || removedItems.isEmpty()) {
            return 0;
        }

        Habbo habbo = client.getHabbo();
        if (habbo == null) {
            return 0;
        }

        int delivered = ChestWiredFurniUtil.giveStoredItemsToInventory(habbo, removedItems, chest);
        client.sendResponse(new InventoryRefreshComposer());
        int withdrawn = delivered;
        if (withdrawn > 0) {
            ChestStorage.LogEntry log = new ChestStorage.LogEntry(
                    "withdraw", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), withdrawn, 0);
            log.setFurniItems(removedItems.stream()
                    .filter(item -> !chest.getContents().furniItems().contains(item)));
            chest.getContents().addLog(log);
        }
        chest.persistContents(habbo.getHabboInfo().getCurrentRoom());

        client.sendResponse(new ChestDataComposer(chest, habbo));
        ChestFurniPackets.sendFullChunks(client, chest.getId(), chest.getContents());

        ChestNotifications.send(
                habbo,
                delivered > 0 ? "success" : "rejected",
                delivered > 0
                        ? "Prelievo dei Furni dallo scrigno completato."
                        : "Impossibile consegnare i Furni. Sono rimasti nello scrigno.");
        return delivered;
    }
}

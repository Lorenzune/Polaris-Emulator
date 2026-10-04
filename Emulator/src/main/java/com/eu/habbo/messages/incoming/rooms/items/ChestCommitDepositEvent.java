package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Validates the entire offer before transferring any item, after final confirmation. */
public class ChestCommitDepositEvent extends MessageHandler {
    private int requestedChestId;
    @Override public int getRatelimit() { return 500; }
    @Override public void handle() throws Exception {
        var habbo = client.getHabbo();
        var room = habbo == null ? null : habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;
        int chestId = packet.readInt(), credits = packet.readInt(), count = packet.readInt();
        requestedChestId = chestId;
        if (habbo.getRoomUnit() != null && habbo.getRoomUnit().hasStatus(com.eu.habbo.habbohotel.rooms.RoomUnitStatus.TRADING)) { reject("trading_error", "Concludi lo scambio in corso prima di depositare nello scrigno."); return; }
        if (credits < 0 || count < 0 || count > 100 || !ChestDepositSession.isDepositingInto(habbo.getHabboInfo().getId(), chestId)) { reject(); return; }
        if (credits == 0 && count == 0) { ChestDepositSession.clear(habbo.getHabboInfo().getId()); return; }
        var item = room.getHabboItem(chestId);
        if (!(item instanceof InteractionWiredChest chest) || !chest.canDeposit(habbo, room)) { reject(); return; }
        var ids = new HashSet<Integer>();
        for (int index = 0; index < count; index++) {
            int id = packet.readInt();
            if (id <= 0 || !ids.add(id)) { reject(); return; }
        }
        int currencyType = packet.bytesAvailable() >= 4 ? packet.readInt() : -1;
        if (currencyType != -1 && currencyType != 0 && currencyType != 5) { reject(); return; }
        var contents = chest.getContents();
        var inventory = habbo.getInventory().getItemsComponent();
        synchronized (contents) {
            if (!ChestDepositSession.isDepositingInto(habbo.getHabboInfo().getId(), chestId)) { reject(); return; }
            if (chest instanceof InteractionWiredChestCurrency) {
                if (count != 0 || credits <= 0) { reject(); return; }
                if (credits > contents.getCapacity() - contents.total(ChestStorage.KIND_CURRENCY)) { reject("capacity_exceeds", "Lo scrigno non ha abbastanza spazio per il deposito."); return; }
                if (!ChestWiredCurrencyUtil.take(habbo, currencyType, credits)) { reject("rejected", "Non hai abbastanza valuta per completare il deposito."); return; }
                contents.add(ChestStorage.KIND_CURRENCY, currencyType, credits);
                contents.addLog(new ChestStorage.LogEntry(currencyType == -1 ? "deposit" : currencyType == 5 ? "deposit_diamonds" : "deposit_duckets", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), 0, credits).withCurrency(currencyType));
            } else if (chest instanceof InteractionWiredChestFurni) {
                if (credits != 0 || count == 0) { reject(); return; }
                if (count > contents.getCapacity() - contents.furniItemCount()) { reject("capacity_exceeds", "Lo scrigno non ha abbastanza spazio per i Furni offerti."); return; }
                var removed = new ArrayList<HabboItem>();
                synchronized (inventory.getItems()) {
                    for (int id : ids) {
                        var candidate = inventory.getHabboItem(id);
                        if (candidate == null || candidate.getBaseItem() == null || (candidate.getBaseItem().getType() != FurnitureType.FLOOR && candidate.getBaseItem().getType() != FurnitureType.WALL)) { reject(); return; }
                        removed.add(candidate);
                    }
                    try {
                        for (var candidate : removed) {
                            inventory.removeHabboItem(candidate);
                            if (inventory.getHabboItem(candidate.getId()) != null) throw new IllegalStateException("Inventory removal cancelled");
                        }
                    } catch (RuntimeException exception) {
                        for (var candidate : removed) if (inventory.getHabboItem(candidate.getId()) == null) inventory.addItem(candidate);
                        reject(); return;
                    }
                    var stored = removed.stream().map(candidate -> ChestFurniStoredItem.fromHabboItem(candidate, candidate.getId())).toList();
                    for (var row : stored) contents.tryDepositFurni(row);
                    var log = new ChestStorage.LogEntry("deposit", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), 0, stored.size());
                    log.setFurniItems(stored.stream());
                    contents.addLog(log);
                }
                for (var candidate : removed) client.sendResponse(new RemoveHabboItemComposer(candidate.getGiftAdjustedId()));
                Emulator.getThreading().runPersistence(new QueryDeleteHabboItems(List.copyOf(removed)));
                client.sendResponse(new InventoryRefreshComposer());
            } else return;
            client.sendResponse(new com.eu.habbo.messages.outgoing.rooms.items.ChestDepositResultComposer(chestId, true));
            ChestNotifications.send(habbo, "success", "Deposito nello scrigno completato.");
            ChestDepositSession.clear(habbo.getHabboInfo().getId());
            chest.persistContents(room);
            client.sendResponse(new ChestDataComposer(chest, habbo));
            if (chest instanceof InteractionWiredChestFurni) ChestFurniPackets.sendFullChunks(client, chestId, contents);
        }
        ChestTransactionEvents.deposited(room, habbo, chest);
    }
    private void reject() { reject("rejected", "Deposito annullato: controlla i permessi e gli oggetti selezionati. Nulla è stato trasferito."); }
    private void reject(String event, String message) {
        client.sendResponse(new com.eu.habbo.messages.outgoing.rooms.items.ChestDepositResultComposer(requestedChestId, false));
        ChestNotifications.send(client.getHabbo(), event, message);
    }
}

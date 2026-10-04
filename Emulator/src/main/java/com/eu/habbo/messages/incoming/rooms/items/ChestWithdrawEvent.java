package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestNotifications;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

/**
 * Player withdraws currency from a wired chest (Scrigno). Reads {@code int itemId, int currencyType,
 * int amount} (amount {@code < 0} = withdraw all of that type). Restricted to users with room rights
 * (anti-theft). Takes from the pool, credits the user, logs it, pushes back the state.
 */
public class ChestWithdrawEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();
        int currencyType = this.packet.readInt();
        int amount = this.packet.readInt();
        if (currencyType < 0) currencyType = -1;

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency chest)) return;

        if (!chest.canWithdraw(habbo, room)) return;

        ChestStorage contents = chest.getContents();
        // Atomic check-and-take: never returns more than is present, so racing another thread
        // (another user or a wired effect on the room thread) can't over-withdraw or duplicate.
        int taken = contents.withdrawCurrency(currencyType, amount);
        if (taken <= 0) { ChestNotifications.send(habbo, "rejected", "Nessuna valuta disponibile per questo prelievo."); return; }

        contents.addLog(new ChestStorage.LogEntry(currencyType == -1 ? "withdraw" : currencyType == 5 ? "withdraw_diamonds" : currencyType == 0 ? "withdraw_duckets" : "withdraw", System.currentTimeMillis(), habbo.getHabboInfo().getUsername(), taken, 0).withCurrency(currencyType));
        chest.persistContents(room);

        if (currencyType < 0) habbo.giveCredits(taken);
        else habbo.givePoints(currencyType, taken);

        ChestNotifications.send(habbo, "success", "Prelievo dallo scrigno completato.");
        this.client.sendResponse(new ChestDataComposer(chest, habbo));
    }
}

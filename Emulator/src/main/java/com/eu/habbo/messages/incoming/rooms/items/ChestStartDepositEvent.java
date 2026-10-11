package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestDepositSession;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Start furni deposit mode (official Kigike / header 3514 wire shape).
 * We use header {@code ChestStartDepositEvent} (9324) because 3514 is unavailable in Nitro.
 */
public class ChestStartDepositEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int chestItemId = this.packet.readInt();
        HabboItem item = room.getHabboItem(chestItemId);
        if (!(item instanceof InteractionWiredChest chest)) return;
        if (!chest.canDeposit(habbo, room)) return;

        ChestDepositSession.start(habbo.getHabboInfo().getId(), chestItemId);
    }
}

package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChestCloseEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 0;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int chestId = this.packet.readInt();
        if (com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestDepositSession.isDepositingInto(
                habbo.getHabboInfo().getId(), chestId))
            com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestDepositSession.clear(
                    habbo.getHabboInfo().getId());
        HabboItem item = room.getHabboItem(chestId);
        if (item instanceof InteractionWiredChest chest) {
            chest.removeViewer(habbo.getHabboInfo().getId(), room);
        }
    }
}

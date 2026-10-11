package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetBreedingNest;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class StopBreedingEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        var room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || itemId <= 0) return;
        HabboItem item = room.getHabboItem(itemId);

        if (item instanceof InteractionPetBreedingNest) {
            ((InteractionPetBreedingNest) item).stopBreeding(this.client.getHabbo());
        }
    }
}
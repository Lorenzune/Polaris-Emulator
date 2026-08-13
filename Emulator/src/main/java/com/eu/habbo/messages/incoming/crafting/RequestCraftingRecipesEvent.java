package com.eu.habbo.messages.incoming.crafting;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class RequestCraftingRecipesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        HabboItem item = room.getHabboItem(itemId);

        if (item != null) {
            CraftingEditorSupport.sendStateAndRecipes(this.client, itemId);
        }
    }
}

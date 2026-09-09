package com.eu.habbo.messages.incoming.inventory.cosmetics;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager.Category;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.cosmetics.UserProfileCosmeticsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

public class PurchaseProfileCosmeticEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        HabboInfo info = habbo.getHabboInfo();
        if (info == null) return;

        int itemId = this.packet.readInt();
        String typeStr = this.packet.readString();
        if (typeStr == null) return;

        Category category = Category.fromDbValue(typeStr);
        if (category == null) {
            if (typeStr.equalsIgnoreCase("backgrounds")) category = Category.BACKGROUND;
            else if (typeStr.equalsIgnoreCase("stands")) category = Category.STAND;
            else if (typeStr.equalsIgnoreCase("cards")) category = Category.CARD;
            else if (typeStr.equalsIgnoreCase("borders")) category = Category.BORDER;
        }

        if (category == null) return;

        InfostandBackgroundManager manager = Emulator.getGameEnvironment() != null ? Emulator.getGameEnvironment().getInfostandBackgroundManager() : null;
        if (manager == null) return;

        boolean success = manager.tryPurchaseAndEquip(habbo, category, itemId);
        if (success) {
            if (category == Category.BACKGROUND) {
                info.setInfostandBg(itemId);
            } else if (category == Category.STAND) {
                info.setInfostandStand(itemId);
            } else if (category == Category.CARD) {
                info.setInfostandCardBg(itemId);
            } else if (category == Category.BORDER) {
                info.setInfostandBorder(itemId);
            }
            info.run();

            if (info.getCurrentRoom() != null) {
                info.getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
            } else {
                this.client.sendResponse(new RoomUserDataComposer(habbo));
            }
        }

        this.client.sendResponse(new UserProfileCosmeticsComposer(habbo));
    }
}

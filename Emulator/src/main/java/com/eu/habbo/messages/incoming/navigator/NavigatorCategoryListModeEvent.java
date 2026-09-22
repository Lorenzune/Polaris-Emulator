package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.navigation.ListMode;
import com.eu.habbo.habbohotel.navigation.NavigatorManager;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.messages.incoming.MessageHandler;

public class NavigatorCategoryListModeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String category = this.packet.readString();
        int viewMode = this.packet.readInt();

        NavigatorManager navigatorManager = Emulator.getGameEnvironment().getNavigatorManager();
        String categoryKey = navigatorManager.getPersonalCategoryKey(category);
        RoomCategory roomCategory =
                Emulator.getGameEnvironment().getRoomManager().getCategory(categoryKey);
        this.client
                .getHabbo()
                .getHabboStats()
                .navigatorWindowSettings
                .setListMode(
                        roomCategory != null ? roomCategory.getCaptionSave() : categoryKey,
                        viewMode == 1 ? ListMode.THUMBNAILS : ListMode.LIST);
    }
}

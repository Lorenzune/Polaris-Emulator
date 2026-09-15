package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.navigation.DisplayMode;
import com.eu.habbo.messages.incoming.MessageHandler;

public class NavigatorCollapseCategoryEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String category = this.packet.readString();
        String categoryKey = Emulator.getGameEnvironment().getNavigatorManager().getPersonalCategoryKey(category);
        this.client.getHabbo().getHabboStats().navigatorWindowSettings.setDisplayMode(categoryKey, DisplayMode.COLLAPSED);
    }
}

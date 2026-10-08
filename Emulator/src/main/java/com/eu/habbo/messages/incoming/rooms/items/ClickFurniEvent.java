package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserIdleEvent;

public class ClickFurniEvent extends MessageHandler {
    private static final String CLICK_TILE_INTERACTION = "room_invisible_click_tile";

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        int itemId = this.packet.readInt();
        this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(itemId)) {
            return;
        }

        HabboItem item = room.getHabboItem(itemId);

        if (item == null) {
            return;
        }

        // A valid furniture click is activity even when the avatar stays seated.
        var activity = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.ACTION, false);
        Emulator.getPluginManager().fireEvent(activity);
        if (!activity.isCancelled() && !activity.idle) {
            room.unIdle(this.client.getHabbo());
        }

        WiredManager.queueUserClicksFurni(room, this.client.getHabbo().getRoomUnit(), item);

        if (isClickTileItem(item)) {
            WiredManager.triggerUserClicksTile(room, this.client.getHabbo().getRoomUnit(), item);
        }
    }

    private boolean isClickTileItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interaction = item.getBaseItem().getInteractionType().getName();
        return interaction != null && interaction.equalsIgnoreCase(CLICK_TILE_INTERACTION);
    }
}

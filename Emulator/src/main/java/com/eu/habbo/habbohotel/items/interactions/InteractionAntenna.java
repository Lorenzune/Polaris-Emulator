package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionAntenna extends InteractionDefault {
    public InteractionAntenna(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionAntenna(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (room == null) return;

        RoomUnit actor = (client != null && client.getHabbo() != null) ? client.getHabbo().getRoomUnit() : null;

        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .tile(room.getLayout().getTile(this.getX(), this.getY()))
                .signalChannel(this.getId())
                .signalUserCount(actor != null ? 1 : 0)
                .signalFurniCount(1)
                .sourceItem(this);

        if (actor != null) {
            builder.actor(actor);
            builder.originActor(actor);
        }

        WiredManager.handleEvent(builder.build());
    }
}

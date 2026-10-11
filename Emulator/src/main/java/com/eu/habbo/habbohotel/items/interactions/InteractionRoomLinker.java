package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.sql.ResultSet;
import java.sql.SQLException;

/** A paired room endpoint, activated only by the Wired room-teleport effect. */
public class InteractionRoomLinker extends InteractionTeleport {
    public InteractionRoomLinker(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public InteractionRoomLinker(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {}

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public boolean canWalkOn(RoomUnit unit, Room room, Object[] objects) {
        return true;
    }
}

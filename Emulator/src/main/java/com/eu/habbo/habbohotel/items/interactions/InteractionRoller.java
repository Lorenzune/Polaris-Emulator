package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.util.Pair;

public class InteractionRoller extends HabboItem {
    public static volatile boolean NO_RULES = false;
    public static int DELAY = 400;

    public InteractionRoller(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionRoller(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
    }

    @Override
    public boolean canStackAt(Room room, List<Pair<RoomTile, Set<HabboItem>>> itemsAtLocation) {
        if (NO_RULES) return true;
        if (itemsAtLocation.isEmpty()) return false;

        for (Pair<RoomTile, Set<HabboItem>> set : itemsAtLocation) {
            if (set.getValue() == null || set.getValue().isEmpty()) {
                continue;
            }

            HabboItem floorSurface = null;
            for (HabboItem item : set.getValue()) {
                if (item == this) {
                    continue;
                }

                if (floorSurface != null || !canBePlacedOn(item)) {
                    return false;
                }

                floorSurface = item;
            }
        }

        return true;
    }

    /**
     * Rollers may sit on one walkable, stackable floor furnishing. They must not be stacked on
     * ordinary furniture or on another roller.
     */
    private static boolean canBePlacedOn(HabboItem item) {
        return item != null
                && !(item instanceof InteractionRoller)
                && item.getBaseItem().allowStack()
                && (item.isWalkable() || item.getBaseItem().allowWalk());
    }
}

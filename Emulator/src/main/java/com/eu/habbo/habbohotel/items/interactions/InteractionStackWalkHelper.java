package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

public class InteractionStackWalkHelper extends HabboItem {
    private static final String MULTI_WALK_ENABLED = "1";

    public InteractionStackWalkHelper(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionStackWalkHelper(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    public boolean isMultiWalkEnabled() {
        return MULTI_WALK_ENABLED.equals(this.getExtradata());
    }

    public void setMultiWalkEnabled(boolean enabled) {
        this.setExtradata(enabled ? MULTI_WALK_ENABLED : "0");
    }

    public static boolean allowsMultipleUnits(Room room, RoomTile tile) {
        if (room == null || tile == null) {
            return false;
        }

        Set<HabboItem> items = room.getItemsAt(tile);
        return items != null && items.stream()
                .filter(InteractionStackWalkHelper.class::isInstance)
                .map(InteractionStackWalkHelper.class::cast)
                .anyMatch(InteractionStackWalkHelper::isMultiWalkEnabled);
    }

    @Override
    public boolean canOverrideTile(RoomUnit unit, Room room, RoomTile tile) {
        return this.isMultiWalkEnabled();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean isUsable() {
        return true;
    }
}

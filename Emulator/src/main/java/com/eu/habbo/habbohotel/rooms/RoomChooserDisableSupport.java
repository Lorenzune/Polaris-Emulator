package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionChooserDisableControl;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.ChooserDisabledStateComposer;

public final class RoomChooserDisableSupport {
    private RoomChooserDisableSupport() {}

    public static boolean isControllerItem(HabboItem item) {
        return item != null
                && item.getBaseItem() != null
                && (item instanceof InteractionChooserDisableControl
                        || (item.getBaseItem().getInteractionType() != null
                                && "conf_chooser_disable"
                                        .equals(item.getBaseItem()
                                                .getInteractionType()
                                                .getName())));
    }

    public static boolean isChooserDisabled(Room room) {
        if (room == null) return false;
        for (HabboItem item : room.getFloorItems()) {
            if (isControllerItem(item) && "1".equals(item.getExtradata())) return true;
        }
        return false;
    }

    public static void sendState(Room room) {
        if (room != null) room.sendComposer(new ChooserDisabledStateComposer(room).compose());
    }
}

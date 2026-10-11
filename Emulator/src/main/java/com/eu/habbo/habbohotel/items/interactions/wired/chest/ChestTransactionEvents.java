package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

/** The committed chest is the furniture source, matching contract transaction semantics. */
public final class ChestTransactionEvents {
    private ChestTransactionEvents() {}

    public static void deposited(Room room, Habbo user, InteractionWiredChest chest) {
        if (room == null || user == null || chest == null || user.getRoomUnit() == null) return;
        var event = WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETE, room)
                .sourceItem(chest)
                .actor(user.getRoomUnit())
                .actionParameter(0);
        var tile = room.getLayout() == null ? null : room.getLayout().getTile(chest.getX(), chest.getY());
        if (tile != null) event.tile(tile);
        WiredManager.dispatchEffectTriggeredEvent(event.build());
    }
}

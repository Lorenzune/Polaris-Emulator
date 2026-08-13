package com.eu.habbo.messages.incoming.crackable;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.CrackableReward;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionCrackable;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.crackable.CrackableEditorStateComposer;

final class CrackableEditorSupport {
    private CrackableEditorSupport() { }

    static boolean canEdit(GameClient client) {
        return client != null && client.getHabbo() != null
                && client.getHabbo().hasPermission(Permission.ACC_CRACKABLE_EDITOR);
    }

    static HabboItem getCrackable(GameClient client, int objectId) {
        if (client == null || client.getHabbo() == null) return null;
        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
        HabboItem item = room == null ? null : room.getHabboItem(objectId);
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null
                || item.getBaseItem().getInteractionType().getType() != InteractionCrackable.class) return null;
        return item;
    }

    static void sendState(GameClient client, int objectId) {
        HabboItem item = getCrackable(client, objectId);
        if (item == null) return;

        Item baseItem = item.getBaseItem();
        CrackableReward reward = Emulator.getGameEnvironment().getItemManager().getCrackableData(baseItem.getId());
        client.sendResponse(new CrackableEditorStateComposer(canEdit(client), objectId, baseItem, reward));
    }
}

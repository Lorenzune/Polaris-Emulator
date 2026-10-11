package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredLabel;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.WiredLabelEditorComposer;

public class RequestWiredLabelEditorEvent extends MessageHandler {
    @Override
    public void handle() {
        Room room = currentRoom();
        if (room == null || packet.bytesAvailable() != 8) return;
        int roomId = packet.readInt();
        int itemId = packet.readInt();
        if (roomId != room.getId()) return;
        HabboItem item = room.getHabboItem(itemId);
        if (item instanceof InteractionWiredLabel && room.canModifyWired(client.getHabbo(), item)) {
            client.sendResponse(new WiredLabelEditorComposer(roomId, (InteractionWiredLabel) item));
        }
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}

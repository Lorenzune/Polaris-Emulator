package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredLabel;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;

/** Text/color edits never overwrite a visibility state changed meanwhile by WIRED. */
public class SaveWiredLabelCardEvent extends MessageHandler {
    @Override
    public void handle() {
        Room room = currentRoom();
        if (room == null || packet.bytesAvailable() < 14) return;
        int roomId = packet.readInt();
        int itemId = packet.readInt();
        String text = packet.readString();
        if (packet.bytesAvailable() != 4) return;
        int color = packet.readInt();
        if (roomId != room.getId()
                || !InteractionWiredLabel.validText(text)
                || !InteractionWiredLabel.validColor(color)) return;
        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredLabel) || !room.canModifyWired(client.getHabbo(), item)) return;
        InteractionWiredLabel label = (InteractionWiredLabel) item;
        synchronized (label) {
            if (label.getLabelText().equals(text) && label.getCardColor() == color) return;
            label.setLabelText(text);
            label.setCardColor(color);
            label.needsUpdate(true);
            room.sendComposer(new FloorItemUpdateComposer(label).compose());
        }
        WiredPlatform.threading().run(label);
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}

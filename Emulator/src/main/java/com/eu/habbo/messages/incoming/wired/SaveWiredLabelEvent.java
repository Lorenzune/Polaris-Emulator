package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredLabel;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;

public class SaveWiredLabelEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null || packet.bytesAvailable() < 14) return;
        int roomId = packet.readInt();
        int itemId = packet.readInt();
        String text = packet.readString();
        if (packet.bytesAvailable() != 4) return;
        int state = packet.readInt();
        if (roomId != room.getId() || (state != 0 && state != 1) || !InteractionWiredLabel.validText(text)) return;
        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredLabel) || !room.canModifyWired(client.getHabbo(), item)) return;
        synchronized (item) {
            if (((InteractionWiredLabel) item).getLabelText().equals(text)
                    && item.getExtradata().equals(Integer.toString(state))) return;
            boolean stateChanged = !item.getExtradata().equals(Integer.toString(state));
            ((InteractionWiredLabel) item).setLabelText(text);
            if (stateChanged) item.setExtradata(Integer.toString(state));
            item.needsUpdate(true);
            if (stateChanged) room.updateItemState(item);
            room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }
        Emulator.getThreading().run(item);
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}

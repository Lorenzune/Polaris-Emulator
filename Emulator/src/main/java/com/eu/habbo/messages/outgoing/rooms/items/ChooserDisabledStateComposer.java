package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChooserDisableSupport;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChooserDisabledStateComposer extends MessageComposer {
    private final int roomId;
    private final boolean disabled;

    public ChooserDisabledStateComposer(Room room) {
        this.roomId = room == null ? 0 : room.getId();
        this.disabled = RoomChooserDisableSupport.isChooserDisabled(room);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChooserDisabledStateComposer);
        this.response.appendInt(this.roomId);
        this.response.appendBoolean(this.disabled);
        return this.response;
    }
}

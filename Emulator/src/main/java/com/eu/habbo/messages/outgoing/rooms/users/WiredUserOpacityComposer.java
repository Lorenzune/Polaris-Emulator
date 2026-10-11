package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;

public class WiredUserOpacityComposer extends MessageComposer {
    private final int roomId;
    private final Collection<RoomUnit> units;

    public WiredUserOpacityComposer(int roomId, Collection<RoomUnit> units) {
        this.roomId = roomId;
        this.units = units.stream().filter(unit -> !unit.isInvisible()).toList();
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.WiredUserOpacityComposer);
        response.appendInt(roomId);
        response.appendInt(units.size());
        for (RoomUnit unit : units) {
            response.appendInt(unit.getId());
            response.appendInt(unit.getWiredOpacity());
        }
        return response;
    }
}

package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.WiredHighlightState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public class WiredHighlightComposer extends MessageComposer {
    private final int roomId;
    private final List<WiredHighlightState> updates;

    public WiredHighlightComposer(int roomId, List<WiredHighlightState> updates) {
        this.roomId = roomId;
        this.updates = List.copyOf(updates);
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.WiredHighlightComposer);
        response.appendInt(roomId);
        response.appendInt(updates.size());
        for (var update : updates) {
            response.appendInt(update.category());
            response.appendInt(update.id());
            response.appendInt(update.style());
            response.appendInt(update.color());
        }
        return response;
    }
}

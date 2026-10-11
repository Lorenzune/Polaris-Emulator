package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredLabel;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredLabelEditorComposer extends MessageComposer {
    private final int roomId;
    private final InteractionWiredLabel item;

    public WiredLabelEditorComposer(int roomId, InteractionWiredLabel item) {
        this.roomId = roomId;
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.WiredLabelEditorComposer);
        response.appendInt(roomId);
        response.appendInt(item.getId());
        response.appendString(item.getLabelText());
        response.appendInt("1".equals(item.getExtradata()) ? 1 : 0);
        response.appendInt(item.getCardColor());
        return response;
    }
}

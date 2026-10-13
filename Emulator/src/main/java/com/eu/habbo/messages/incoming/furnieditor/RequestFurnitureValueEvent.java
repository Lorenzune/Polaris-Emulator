package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.editor.FurnitureValueRepository;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurnitureValueComposer;

public class RequestFurnitureValueEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int spriteId = this.packet.readInt();
        String type = this.packet.readString();
        if (spriteId <= 0 || !("s".equals(type) || "i".equals(type))) return;
        var value = new FurnitureValueRepository(Emulator.getDatabase().getDataSource()).find(spriteId, type);
        this.client.sendResponse(new FurnitureValueComposer(spriteId, type, value));
    }
}

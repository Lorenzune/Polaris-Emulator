package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.editor.FurniEditorRepository;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;

public class FurniEditorBySpriteEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        int spriteId = this.packet.readInt();
        String itemName = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";

        if (spriteId <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid sprite ID"));
            return;
        }

        int itemId = new FurniEditorRepository(Emulator.getDatabase().getDataSource())
                .findItemIdBySprite(spriteId, itemName)
                .orElse(-1);

        if (itemId <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No item found with sprite_id: " + spriteId));
            return;
        }

        // Delegate to the detail response builder
        FurniEditorDetailEvent.sendDetailResponse(this.client, itemId);
    }
}

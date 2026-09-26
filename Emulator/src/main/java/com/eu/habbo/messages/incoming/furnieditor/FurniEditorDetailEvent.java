package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.editor.FurniEditorRepository;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorDetailComposer;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;

public class FurniEditorDetailEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        int id = this.packet.readInt();

        if (id <= 0) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "Invalid item ID"));
            return;
        }

        sendDetailResponse(this.client, id);
    }

    /**
     * Shared method to build and send a detail response for a given item ID.
     * Used by both FurniEditorDetailEvent and FurniEditorBySpriteEvent.
     */
    public static void sendDetailResponse(com.eu.habbo.habbohotel.gameclients.GameClient client, int itemId)
            throws Exception {
        var detail = new FurniEditorRepository(Emulator.getDatabase().getDataSource()).findDetail(itemId);
        var item = detail.item();
        String furniDataJson = "{}";
        String furniDataDiagnosticJson = "{}";

        if (item == null) {
            client.sendResponse(new FurniEditorResultComposer(false, "Item not found: " + itemId));
            return;
        }

        // Furnidata is optional diagnostic metadata only. Resolve it by the
        // database sprite_id so a DB id and furnidata id can never be confused.
        try {
            Object classname = item.get("item_name");
            Object spriteId = item.get("sprite_id");
            int lookupId = spriteId instanceof Number number ? number.intValue() : itemId;
            FurniDataManager.LookupResult lookup =
                    FurniDataManager.getItemLookup(lookupId, classname != null ? classname.toString() : null);
            furniDataJson = lookup.itemJson();
            furniDataDiagnosticJson = lookup.diagnosticJson();
        } catch (Exception e) {
            furniDataJson = "{}";
            furniDataDiagnosticJson = "{}";
        }

        client.sendResponse(new FurniEditorDetailComposer(
                item, detail.usageCount(), detail.catalogItems(), furniDataJson, furniDataDiagnosticJson));
    }
}

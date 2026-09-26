package com.eu.habbo.messages.incoming.furniturereport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.editor.FurniEditorRepository;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketState;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.furnieditor.FurniEditorUpdatePayload;
import com.eu.habbo.messages.outgoing.furniturereport.FurnitureReportUpdateResultComposer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FurnitureReportUpdateEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int issueId = this.packet.readInt();
        int spriteId = this.packet.readInt();
        String fieldsJson = this.packet.readString();

        if (!this.client.getHabbo().hasPermission(Permission.ACC_MODTOOL_TICKET_Q)
                || !this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurnitureReportUpdateResultComposer(false, issueId, "No permission", "{}"));
            return;
        }

        ModToolIssue issue = Emulator.getGameEnvironment().getModToolManager().getTicket(issueId);
        if (issue == null || issue.type != ModToolTicketType.FURNITURE || issue.state == ModToolTicketState.CLOSED) {
            this.client.sendResponse(
                    new FurnitureReportUpdateResultComposer(false, issueId, "Segnalazione non disponibile", "{}"));
            return;
        }

        JsonObject fields;
        try {
            fields = JsonParser.parseString(fieldsJson).getAsJsonObject();
        } catch (Exception exception) {
            this.client.sendResponse(new FurnitureReportUpdateResultComposer(false, issueId, "Dati non validi", "{}"));
            return;
        }
        fields.remove("spriteId");

        JsonObject report = FurnitureReportSupport.parseMessage(issue.message);
        JsonObject current = report == null ? null : report.getAsJsonObject("currentValues");
        int reportItemId =
                current == null || !current.has("id") ? 0 : current.get("id").getAsInt();
        int reportSpriteId = current == null || !current.has("sprite_id")
                ? 0
                : current.get("sprite_id").getAsInt();
        String itemName = current == null || !current.has("item_name")
                ? ""
                : current.get("item_name").getAsString();
        if (report == null || reportItemId <= 0 || reportSpriteId != spriteId || itemName.isBlank()) {
            this.client.sendResponse(
                    new FurnitureReportUpdateResultComposer(false, issueId, "Sprite ID non corrispondente", "{}"));
            return;
        }

        FurniEditorUpdatePayload payload = FurniEditorUpdatePayload.validate(fields);
        if (!payload.valid()) {
            this.client.sendResponse(new FurnitureReportUpdateResultComposer(false, issueId, payload.error, "{}"));
            return;
        }

        FurniEditorRepository repository =
                new FurniEditorRepository(Emulator.getDatabase().getDataSource());
        int updatedRows = repository.updateItemByIdAndSprite(
                reportItemId, spriteId, itemName, payload.setClauses, payload.values);
        if (updatedRows <= 0) {
            this.client.sendResponse(
                    new FurnitureReportUpdateResultComposer(false, issueId, "Nessun items_base trovato", "{}"));
            return;
        }

        Emulator.getGameEnvironment().getItemManager().loadItems();

        JsonObject updated = repository
                .findFullItemBySprite(spriteId, itemName)
                .map(FurnitureReportSupport.GSON::toJsonTree)
                .map(element -> element.getAsJsonObject())
                .orElse(current);
        report.add("currentValues", updated);
        report.add("proposedValues", fields);
        report.addProperty(
                "lastUpdatedBy", this.client.getHabbo().getHabboInfo().getUsername());
        issue.message = FurnitureReportSupport.PREFIX + FurnitureReportSupport.GSON.toJson(report);
        issue.updateInDatabase();
        Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);

        this.client.sendResponse(new FurnitureReportUpdateResultComposer(
                true,
                issueId,
                "Aggiornate " + updatedRows + " righe items_base",
                FurnitureReportSupport.GSON.toJson(updated)));
    }
}

package com.eu.habbo.messages.incoming.furniturereport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furniturereport.FurnitureReportResultComposer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FurnitureReportSubmitEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        int objectId = this.packet.readInt();
        String changesJson = this.packet.readString();
        String reason = FurnitureReportSupport.limit(this.packet.readString(), 500);

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        JsonObject current = FurnitureReportSupport.readRoomFurniture(room, roomId, objectId).orElse(null);
        if (current == null) {
            this.client.sendResponse(new FurnitureReportResultComposer(false, 0, "Furni non trovato nella stanza"));
            return;
        }

        JsonObject proposed;
        try {
            proposed = JsonParser.parseString(changesJson == null || changesJson.isBlank() ? "{}" : changesJson).getAsJsonObject();
        } catch (Exception exception) {
            this.client.sendResponse(new FurnitureReportResultComposer(false, 0, "Dati della segnalazione non validi"));
            return;
        }
        if (proposed.toString().length() > 8000) {
            this.client.sendResponse(new FurnitureReportResultComposer(false, 0, "Segnalazione troppo lunga"));
            return;
        }

        int senderId = this.client.getHabbo().getHabboInfo().getId();
        String senderName = this.client.getHabbo().getHabboInfo().getUsername();
        int reportedId = room.getOwnerId() > 0 ? room.getOwnerId() : senderId;
        String reportedName = room.getOwnerName() == null || room.getOwnerName().isBlank() ? senderName : room.getOwnerName();
        String message = FurnitureReportSupport.buildMessage(current, proposed, reason);

        ModToolIssue issue = new ModToolIssue(
                senderId,
                senderName,
                reportedId,
                reportedName,
                roomId,
                message,
                ModToolTicketType.FURNITURE);

        Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
        Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
        this.client.sendResponse(new FurnitureReportResultComposer(true, issue.id, "Segnalazione inviata ai moderatori"));
    }
}

package com.eu.habbo.messages.incoming.furniturereport;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furniturereport.FurnitureReportDataComposer;
import com.eu.habbo.habbohotel.rooms.Room;

public class FurnitureReportDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();
        int objectId = this.packet.readInt();
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        var data = FurnitureReportSupport.readRoomFurniture(room, roomId, objectId);
        if (data.isEmpty()) {
            this.client.sendResponse(new FurnitureReportDataComposer(false, "Furni non trovato nella stanza", "{}"));
            return;
        }

        this.client.sendResponse(new FurnitureReportDataComposer(true, "", FurnitureReportSupport.GSON.toJson(data.get())));
    }
}

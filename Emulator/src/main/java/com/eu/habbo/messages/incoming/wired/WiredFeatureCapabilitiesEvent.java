package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredOpacityBroadcaster;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniOpacityComposer;

public class WiredFeatureCapabilitiesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null || this.packet.bytesAvailable() < 8) {
            return;
        }

        int protocolVersion = this.packet.readInt();
        int capabilities = this.packet.readInt();
        this.client.setWiredFeatureCapabilities(protocolVersion, capabilities);

        int opacityProtocolVersion = WiredOpacityBroadcaster.opacityProtocolVersion(this.client.getHabbo());
        if (opacityProtocolVersion > 0) {
            int userId = this.client.getHabbo().getHabboInfo().getId();
            this.client.sendResponse(new WiredFurniOpacityComposer(
                    opacityProtocolVersion, room.getId(), room.getWiredRuntime().opacitySnapshot(userId), 0, 0));
        }
        WiredOpacityBroadcaster.sendCustomColorSnapshot(
                this.client.getHabbo(), room.getId(), room.getWiredRuntime().customColorSnapshot());
    }

    @Override
    public int getRatelimit() {
        return 500;
    }
}

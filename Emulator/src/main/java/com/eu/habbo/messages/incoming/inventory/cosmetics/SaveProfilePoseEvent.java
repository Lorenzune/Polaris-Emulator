package com.eu.habbo.messages.incoming.inventory.cosmetics;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.cosmetics.UserProfileCosmeticsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

public class SaveProfilePoseEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        HabboInfo info = habbo.getHabboInfo();
        if (info == null) return;

        String target = this.packet.readString();
        int direction = this.packet.readInt();
        int headDirection = this.packet.readInt();
        String action = this.packet.readString();

        if (direction < 0 || direction > 7) direction = 2;
        if (headDirection < 0 || headDirection > 7) headDirection = 2;
        if (action == null || action.trim().isEmpty()) action = "std";

        if (target != null && target.equalsIgnoreCase("profile")) {
            info.setProfileDirection(direction);
            info.setProfileHeadDirection(headDirection);
            info.setProfileAction(action);
        } else {
            info.setInfostandDirection(direction);
            info.setInfostandHeadDirection(headDirection);
            info.setInfostandAction(action);
        }

        info.run();

        if (info.getCurrentRoom() != null) {
            info.getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
        } else {
            this.client.sendResponse(new RoomUserDataComposer(habbo));
        }

        this.client.sendResponse(new UserProfileCosmeticsComposer(habbo));
    }
}

package com.eu.habbo.messages.incoming.inventory.cosmetics;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager.Category;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.cosmetics.UserProfileCosmeticsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

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
        action = action.trim().toLowerCase(Locale.ROOT);

        if (!canUsePose(habbo, action)) return;

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

    private boolean canUsePose(Habbo habbo, String action) {
        if ("std".equalsIgnoreCase(action)) return true;

        InfostandBackgroundManager manager = Emulator.getGameEnvironment() != null
                ? Emulator.getGameEnvironment().getInfostandBackgroundManager()
                : null;
        if (manager == null) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM custom_profile_cosmetics_catalog WHERE enabled = 1 AND LOWER(category) = 'pose' AND image_file = ? LIMIT 1")) {
            statement.setString(1, action);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return false;
                return manager.canUse(habbo, Category.POSE, result.getInt("id"));
            }
        } catch (Exception e) {
            return false;
        }
    }
}

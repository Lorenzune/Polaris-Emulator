package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserSavedSettingsEvent;

public class SaveGamePrivacySettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();

        boolean hideOnline = !this.packet.readBoolean();
        boolean blockFollowing = !this.packet.readBoolean();
        boolean blockFriendRequests = !this.packet.readBoolean();
        boolean blockRoleplay = habbo.getHabboStats().blockRoleplay;
        boolean blockMimic = habbo.getHabboStats().blockMimic;
        boolean mentionsEnabled = habbo.getHabboStats().mentionsEnabled();

        if (this.packet.getBuffer().readableBytes() > 0) {
            blockRoleplay = !this.packet.readBoolean();
        }
        if (this.packet.getBuffer().readableBytes() > 0) {
            blockMimic = !this.packet.readBoolean();
        }
        if (this.packet.getBuffer().readableBytes() > 0) {
            mentionsEnabled = this.packet.readBoolean();
        }

        habbo.getHabboStats()
                .setGamePrivacy(
                        hideOnline, blockFollowing, blockFriendRequests, blockRoleplay, blockMimic, mentionsEnabled);
        AchievementInteractions.progress(habbo, "Profile1");

        habbo.getMessenger()
                .connectionChanged(habbo, habbo.isOnline(), habbo.getHabboInfo().getCurrentRoom() != null);

        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(habbo));
    }
}

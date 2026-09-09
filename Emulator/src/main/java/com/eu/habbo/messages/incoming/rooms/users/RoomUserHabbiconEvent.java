package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserHabbiconComposer;

public class RoomUserHabbiconEvent extends MessageHandler {
    private static final int MAX_HABBICON_ID = 1000000;

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || habbo.getRoomUnit() == null)
            return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        int habbiconId = this.packet.readInt();
        if (habbiconId <= 0 || habbiconId > MAX_HABBICON_ID)
            return;

        // Classic built-in habbicons (1..33) are free for all
        if (habbiconId > 33) {
            boolean hasAllAccess = habbo.hasPermission(Permission.ACC_ANYHABBICON) || habbo.getHabboInfo().getRank().getId() >= 6;
            boolean owns = habbo.getInventory().getHabbiconsComponent() != null
                    && habbo.getInventory().getHabbiconsComponent().hasHabbicon(habbiconId);

            if (!hasAllAccess && !owns) {
                return;
            }
        }

        room.sendComposer(new RoomUserHabbiconComposer(habbo.getRoomUnit(), habbiconId).compose());
        AchievementInteractions.progress(habbo, "HabbiconUsed");
        Emulator.getGameEnvironment().getRewardTrackManager()
                .addProgress(habbo.getHabboInfo().getId(), "use_habbicon", 1);
    }
}

package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.dailytasks.DailyTaskActionMatcher;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserIdleEvent;

public class RoomUserDanceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null)
            return;

        int danceId = this.packet.readInt();
        if (danceId >= 0 && danceId < DanceType.values().length) {
            if (this.client.getHabbo().getRoomUnit().isInRoom()) {

                Habbo habbo = this.client.getHabbo();

                if (this.client.getHabbo().getRoomUnit().getCacheable().get("control") != null) {
                    habbo = (Habbo) this.client.getHabbo().getRoomUnit().getCacheable().get("control");

                    if (habbo.getHabboInfo().getCurrentRoom() != this.client.getHabbo().getHabboInfo().getCurrentRoom()) {
                        habbo.getRoomUnit().getCacheable().remove("controller");
                        this.client.getHabbo().getRoomUnit().getCacheable().remove("control");
                        habbo = this.client.getHabbo();
                    }
                }

                UserIdleEvent event = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.DANCE, false);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (!event.idle) {
                        this.client.getHabbo().getHabboInfo().getCurrentRoom().unIdle(habbo);
                    }
                }

                this.client.getHabbo().getHabboInfo().getCurrentRoom().dance(habbo, DanceType.values()[danceId]);

                if (danceId > 0) {
                    Emulator.getGameEnvironment().getRewardTrackManager()
                            .addProgress(habbo.getHabboInfo().getId(), "dance", 1);
                    if (danceId == 2) {
                        DailyTaskActionMatcher.addProgress(habbo, DailyTaskActionMatcher.JUMP, 1);
                    }
                    if (isDancePartyHost(habbo)) {
                        DailyTaskActionMatcher.addProgress(habbo, DailyTaskActionMatcher.DANCE_PARTY_HOST, 1);
                    }
                    WiredManager.triggerUserPerformsAction(this.client.getHabbo().getHabboInfo().getCurrentRoom(), habbo.getRoomUnit(), WiredUserActionType.DANCE, danceId);
                }
            }
        }
    }

    private boolean isDancePartyHost(Habbo habbo) {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || room.getOwnerId() != habbo.getHabboInfo().getId()) return false;

        int otherDancers = 0;
        for (Habbo roomHabbo : room.getHabbos()) {
            if (roomHabbo == null || roomHabbo == habbo || roomHabbo.getRoomUnit() == null) continue;
            if (roomHabbo.getRoomUnit().getDanceType() != DanceType.NONE) otherDancers++;
        }

        return otherDancers >= 3;
    }
}

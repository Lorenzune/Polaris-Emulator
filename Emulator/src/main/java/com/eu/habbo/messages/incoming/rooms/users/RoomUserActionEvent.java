package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.dailytasks.DailyTaskActionMatcher;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomChatType;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.rooms.RoomUnitEffect;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.eu.habbo.plugin.events.users.UserIdleEvent;

public class RoomUserActionEvent extends MessageHandler {
    private static final int SIX_SEVEN_ACTION = 67;
    private static final String SIX_SEVEN_MESSAGE = "6666666... 777777777777...";

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room != null) {
            Habbo habbo = this.client.getHabbo();

            if (this.client.getHabbo().getRoomUnit().getCacheable().get("control") != null) {
                habbo = (Habbo) this.client.getHabbo().getRoomUnit().getCacheable().get("control");

                if (habbo.getHabboInfo().getCurrentRoom() != room) {
                    habbo.getRoomUnit().getCacheable().remove("controller");
                    this.client.getHabbo().getRoomUnit().getCacheable().remove("control");
                    habbo = this.client.getHabbo();
                }
            }

            int action = this.packet.readInt();
            if (!RoomUserInputGuard.isValidAction(action))
                return;

            int wiredAction = 0;

            if (action == 5) {
                UserIdleEvent event = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.ACTION, true);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (event.idle) {
                        room.idle(habbo);
                    } else {
                        room.unIdle(habbo);
                    }
                }
            } else {
                UserIdleEvent event = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.ACTION, false);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (!event.idle) {
                        room.unIdle(habbo);
                    }
                }

            }

            room.sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.fromValue(action)).compose());

            if (action == SIX_SEVEN_ACTION) {
                room.talk(
                        habbo,
                        new RoomChatMessage(SIX_SEVEN_MESSAGE, habbo, RoomChatMessageBubbles.ALERT),
                        RoomChatType.TALK,
                        true);
            }

            if (wiredAction == 0) {
                switch (action) {
                    case 1:
                        wiredAction = WiredUserActionType.WAVE;
                        Emulator.getGameEnvironment().getRewardTrackManager()
                                .addProgress(habbo.getHabboInfo().getId(), "wave", 1);
                        break;
                    case 6:
                        DailyTaskActionMatcher.addProgress(habbo, DailyTaskActionMatcher.JUMP, 1);
                        int effectId = habbo.getRoomUnit().getEffectId();
                        if (effectId == RoomUnitEffect.SKATEBOARD1.getId()
                                || effectId == RoomUnitEffect.SKATEBOARD2.getId()) {
                            AchievementInteractions.progress(habbo, "SkateBoardJump");
                        }
                        break;
                    case 2:
                        wiredAction = WiredUserActionType.BLOW_KISS;
                        break;
                    case 3:
                        wiredAction = WiredUserActionType.LAUGH;
                        break;
                    case 7:
                        wiredAction = WiredUserActionType.THUMB_UP;
                        break;
                    case SIX_SEVEN_ACTION:
                        wiredAction = WiredUserActionType.SIX_SEVEN;
                        break;
                    default:
                        break;
                }
            }

            if (wiredAction != 0) {
                WiredManager.triggerUserPerformsAction(room, habbo.getRoomUnit(), wiredAction, -1);
            }
        }
    }
}

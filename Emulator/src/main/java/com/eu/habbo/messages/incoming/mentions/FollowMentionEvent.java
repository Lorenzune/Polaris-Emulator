package com.eu.habbo.messages.incoming.mentions;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.StalkErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;

public class FollowMentionEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getHabbo() == null) return;
        int ownerId = this.client.getHabbo().getHabboInfo().getId();
        if (!com.eu.habbo.habbohotel.mentions.MentionManager.runtime().tryAcquireFollow(ownerId)) return;
        int mentionId = this.packet.readInt();
        if (mentionId <= 0) return;
        Integer senderId = com.eu.habbo.habbohotel.mentions.MentionManager.runtime()
                .mutePreferences()
                .findFollowSender(ownerId, mentionId);
        if (senderId == null) return;
        Habbo sender = com.eu.habbo.habbohotel.mentions.MentionManager.runtime().findOnlineHabbo(senderId);
        if (sender == null || !sender.isOnline()) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.FRIEND_OFFLINE));
            return;
        }
        if (sender.getHabboStats().blockFollowing && !this.client.getHabbo().hasPermission("acc_can_stalk")) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.FRIEND_BLOCKED_STALKING));
            return;
        }
        if (sender.getHabboInfo().getCurrentRoom() == null) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.FRIEND_NOT_IN_ROOM));
            return;
        }
        if (sender.getHabboInfo().getCurrentRoom()
                != this.client.getHabbo().getHabboInfo().getCurrentRoom())
            this.client.sendResponse(new ForwardToRoomComposer(
                    sender.getHabboInfo().getCurrentRoom().getId()));
    }
}

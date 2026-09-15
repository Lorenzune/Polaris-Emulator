package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.GuildChatBuddy;
import com.eu.habbo.habbohotel.messenger.Message;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.FriendChatMessageComposer;
import com.eu.habbo.plugin.events.users.friends.UserFriendChatEvent;

public class FriendPrivateMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        String message = FriendInputGuard.normalizeMessage(this.packet.readString());

        // -1 is the virtual Staff Chat buddy. userId <= -10000 are guild chat buddies.
        // They are authorized through their respective buddy handlers.
        if ((userId != -1 && !GuildChatBuddy.isGuildBuddy(userId) && !FriendInputGuard.isPositiveId(userId)) || message.isEmpty()) {
            return;
        }

        if (!this.client.getHabbo().getHabboStats().allowTalk()) {
            return;
        }

        long millis = System.currentTimeMillis();
        if (millis - this.client.getHabbo().getHabboStats().lastChat < 750) {
            return;
        }
        this.client.getHabbo().getHabboStats().lastChat = millis;

        MessengerBuddy buddy = this.client.getHabbo().getMessenger().getFriend(userId);
        if (buddy == null)
            return;

        UserFriendChatEvent event = new UserFriendChatEvent(this.client.getHabbo(), buddy, message);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled())
            return;

        if (userId <= 0) {
            buddy.onMessageReceived(this.client.getHabbo(), message);
        } else if (buddy.onMessageReceivedWithDeliveryStatus(this.client.getHabbo(), message)) {
            Message confirmation = new Message(userId, this.client.getHabbo().getHabboInfo().getId(), "");
            this.client.sendResponse(new FriendChatMessageComposer(confirmation, userId, userId, "offline-sent"));
        }
    }
}

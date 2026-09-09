package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChangeChatBubbleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int chatBubble = this.packet.readInt();

        if (!this.client.getHabbo().hasPermission(Permission.ACC_ANYCHATCOLOR)) {
            for (String s : Emulator.getConfig().getValue("commands.cmd_chatcolor.banned_numbers").split(";")) {
                try {
                    if (Integer.parseInt(s.trim()) == chatBubble) {
                        return;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        RoomChatMessageBubbles bubble = RoomChatMessageBubbles.getBubble(chatBubble);

        if (chatBubble != 0 && !this.client.getHabbo().hasPermission(Permission.ACC_ANYCHATCOLOR)) {
            boolean owns = this.client.getHabbo().getInventory().getChatBubblesComponent() != null
                    && this.client.getHabbo().getInventory().getChatBubblesComponent().ownsBubble(chatBubble);

            boolean hasPermission = bubble.getPermission().isEmpty() || this.client.getHabbo().hasPermission(bubble.getPermission());

            if (!owns && !hasPermission) {
                return;
            }
        }

        this.client.getHabbo().getHabboStats().chatColor = bubble;
        this.client.getHabbo().getHabboStats().run();
        AchievementInteractions.progressUnique(
                this.client.getHabbo(), "ChatStyleOwner", Integer.toString(bubble.getType()));

        this.client.sendResponse(new com.eu.habbo.messages.outgoing.inventory.chatbubbles.UserChatBubblesComposer(this.client.getHabbo()));
    }
}

package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.incoming.MessageHandler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ChangeChatBubbleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int chatBubble = this.packet.readInt();

        if (chatBubble < 0 || chatBubble > 100000) {
            return;
        }

        if (!this.client.getHabbo().hasPermission(Permission.ACC_ANYCHATCOLOR)) {
            for (String s : Emulator.getConfig()
                    .getValue("commands.cmd_chatcolor.banned_numbers")
                    .split(";")) {
                try {
                    if (Integer.parseInt(s.trim()) == chatBubble) {
                        return;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        RoomChatMessageBubbles bubble = RoomChatMessageBubbles.getBubble(chatBubble);

        if (chatBubble != 0 && !this.canUseBubble(chatBubble, bubble)) {
            return;
        }

        this.client.getHabbo().getHabboStats().chatColor = bubble;
        this.client.getHabbo().getHabboStats().run();
        AchievementInteractions.progressUnique(
                this.client.getHabbo(), "ChatStyleOwner", Integer.toString(bubble.getType()));

        this.client.sendResponse(new com.eu.habbo.messages.outgoing.inventory.chatbubbles.UserChatBubblesComposer(
                this.client.getHabbo()));
    }

    private boolean canUseBubble(int chatBubble, RoomChatMessageBubbles bubble) {
        boolean hasAnyChatColor = this.client.getHabbo().hasPermission(Permission.ACC_ANYCHATCOLOR);
        boolean owns = this.client.getHabbo().getInventory().getChatBubblesComponent() != null
                && this.client
                        .getHabbo()
                        .getInventory()
                        .getChatBubblesComponent()
                        .ownsBubble(chatBubble);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT enabled, is_system, is_free, is_hc_only, min_rank, permission FROM chat_bubbles WHERE type = ? LIMIT 1")) {
            statement.setInt(1, chatBubble);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return RoomChatMessageBubbles.isRegistered(chatBubble)
                            && (hasAnyChatColor
                                    || bubble.getPermission().isEmpty()
                                    || this.client.getHabbo().hasPermission(bubble.getPermission()));
                }

                if (!result.getBoolean("enabled") || result.getBoolean("is_system")) {
                    return false;
                }

                String permission = result.getString("permission");
                int minimumRank = result.getInt("min_rank");

                return hasAnyChatColor
                        || owns
                        || result.getBoolean("is_free")
                        || (result.getBoolean("is_hc_only")
                                && this.client.getHabbo().getHabboStats().hasActiveClub())
                        || (minimumRank > 0
                                && this.client
                                                .getHabbo()
                                                .getHabboInfo()
                                                .getRank()
                                                .getId()
                                        >= minimumRank)
                        || (permission != null
                                && !permission.isEmpty()
                                && this.client.getHabbo().hasPermission(permission));
            }
        } catch (SQLException ignored) {
            return RoomChatMessageBubbles.isRegistered(chatBubble)
                    && (hasAnyChatColor
                            || owns
                            || bubble.getPermission().isEmpty()
                            || this.client.getHabbo().hasPermission(bubble.getPermission()));
        }
    }
}

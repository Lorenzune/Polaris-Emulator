package com.eu.habbo.habbohotel.messenger;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.friends.FriendChatMessageComposer;

public class GuildChatBuddy extends MessengerBuddy {
    public static final int GROUP_OFFSET = -10000;
    private final Guild guild;

    public static int getBuddyId(int guildId) {
        return GROUP_OFFSET - guildId;
    }

    public static int getGuildId(int buddyId) {
        return -(buddyId - GROUP_OFFSET);
    }

    public static boolean isGuildBuddy(int buddyId) {
        return buddyId <= GROUP_OFFSET;
    }

    public static boolean isChatMember(GuildMember member) {
        return member != null && member.getRank() != null && member.getRank().type <= GuildRank.MEMBER.type;
    }

    public GuildChatBuddy(Guild guild, int userOne) {
        super(getBuddyId(guild.getId()), guild.getName(), guild.getBadge(), (short) 0, userOne);
        this.guild = guild;
        this.setOnline(true);
    }

    public Guild getGuild() {
        return this.guild;
    }

    @Override
    public void onMessageReceived(Habbo from, String message) {
        if (from == null || message == null || message.isEmpty() || this.guild == null) return;

        GuildMember senderMember = Emulator.getGameEnvironment().getGuildManager().getGuildMember(this.guild.getId(), from.getHabboInfo().getId());
        if (!isChatMember(senderMember)) return;

        if (message.charAt(0) == ':') {
            CommandHandler.handleCommand(from.getClient(), message);
            return;
        }

        int buddyId = this.getId();
        Message chatMessage = new Message(from.getHabboInfo().getId(), buddyId, message);
        String roleName = senderMember.getRank() == null ? "" : senderMember.getRank().name();

        ServerMessage response = new FriendChatMessageComposer(chatMessage, buddyId, from.getHabboInfo().getId(), roleName).compose();

        for (GuildMember member : Emulator.getGameEnvironment().getGuildManager().getGuildMembers(this.guild.getId())) {
            if (!isChatMember(member)) continue;

            Habbo memberHabbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(member.getUserId());
            if (memberHabbo != null && memberHabbo.isOnline() && memberHabbo.getClient() != null) {
                memberHabbo.getClient().sendResponse(response);
            }
        }
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendInt(this.getId());
        message.appendString(this.getUsername());
        message.appendInt(this.getGender().equals(HabboGender.M) ? 0 : 1);
        message.appendBoolean(true);  // online
        message.appendBoolean(false); // in room
        message.appendString(this.getLook());
        message.appendInt(0);         // category
        message.appendString(this.guild != null && this.guild.getDescription() != null ? this.guild.getDescription() : ""); // motto
        message.appendString("");     // last seen
        message.appendString("");     // realname
        message.appendBoolean(true);  // offline messaging supported
        message.appendBoolean(false);
        message.appendBoolean(false);
        message.appendShort(0);       // relation
    }
}

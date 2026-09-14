package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildBadgeBuilder;
import com.eu.habbo.habbohotel.guilds.GuildCustomBadgeRequestManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.guilds.GuildChangedBadgeEvent;

public class GuildChangeBadgeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();

        if (!GuildInputGuard.isPositiveId(guildId)) {
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
        if (guild != null) {
            if (guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || this.client.getHabbo().hasPermission(Permission.ACC_GUILD_ADMIN)) {
                Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());

                if (room == null || room.getId() != guild.getRoomId())
                    return;

                int count = this.packet.readInt();

                String badge = GuildBadgeBuilder.readBadge(this.packet, count);
                if (badge == null) return;

                if (badge.startsWith("custom_")) {
                    if (!GuildCustomBadgeRequestManager.badgeFilesExist(badge)
                            || (!GuildCustomBadgeRequestManager.isApprovedBadge(badge)
                            && !guild.getBadge().equalsIgnoreCase(badge))) {
                        return;
                    }
                }

                if (guild.getBadge().equalsIgnoreCase(badge))
                    return;

                GuildChangedBadgeEvent badgeEvent = new GuildChangedBadgeEvent(guild, badge);
                Emulator.getPluginManager().fireEvent(badgeEvent);

                if (badgeEvent.isCancelled())
                    return;

                guild.setBadge(badgeEvent.badge);
                guild.needsUpdate = true;

                if (Emulator.getConfig().getBoolean("imager.internal.enabled")) {
                    Emulator.getBadgeImager().generate(guild);
                }

                Room guildRoom = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());
                if (guildRoom != null) {
                    guildRoom.refreshGuild(guild);
                    guildRoom.sendComposer(new com.eu.habbo.messages.outgoing.rooms.users.RoomUsersAddGuildBadgeComposer(guild).compose());
                }

                Room currentRoom = this.client.getHabbo().getHabboInfo().getCurrentRoom();
                if (currentRoom != null) {
                    currentRoom.sendComposer(new com.eu.habbo.messages.outgoing.rooms.users.RoomUsersAddGuildBadgeComposer(guild).compose());
                    if (this.client.getHabbo().getHabboStats().guild == guild.getId() && this.client.getHabbo().getRoomUnit() != null) {
                        currentRoom.sendComposer(new com.eu.habbo.messages.outgoing.guilds.GuildFavoriteRoomUserUpdateComposer(this.client.getHabbo().getRoomUnit(), guild).compose());
                    }
                }

                Emulator.getThreading().run(guild);
            }
        }
    }
}

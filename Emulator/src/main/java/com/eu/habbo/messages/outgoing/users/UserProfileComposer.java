package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.UserCustomizationData;
import com.eu.habbo.habbohotel.users.levels.AvatarLevelPlatform;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserProfileComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserProfileComposer.class);

    private final HabboInfo habboInfo;
    private Habbo habbo;
    private GameClient viewer;

    public UserProfileComposer(HabboInfo habboInfo, GameClient viewer) {
        this.habboInfo = habboInfo;
        this.viewer = viewer;
    }

    public UserProfileComposer(Habbo habbo, GameClient viewer) {
        this.habbo = habbo;
        this.habboInfo = habbo.getHabboInfo();
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.habboInfo == null) return null;

        this.response.init(Outgoing.UserProfileComposer);

        this.response.appendInt(this.habboInfo.getId());
        this.response.appendString(this.habboInfo.getUsername());
        this.response.appendString(this.habboInfo.getLook());
        this.response.appendString(this.habboInfo.getMotto());
        this.response.appendString(
                new SimpleDateFormat("dd-MM-yyyy").format(new Date(this.habboInfo.getAccountCreated() * 1000L)));

        int achievementScore = 0;
        int accountLevel = 0;
        if (this.habbo != null) {
            achievementScore = this.habbo.getHabboStats().getAchievementScore();
            accountLevel = this.habbo.getHabboStats().citizenshipLevel;
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT achievement_score, talent_track_citizenship_level FROM users_settings WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, this.habboInfo.getId());
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        achievementScore = set.getInt("achievement_score");
                        accountLevel = set.getInt("talent_track_citizenship_level");
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
        this.response.appendInt(achievementScore);
        this.response.appendInt(Messenger.getFriendCount(this.habboInfo.getId()));
        this.response.appendBoolean(
                this.viewer.getHabbo().getMessenger().getFriends().containsKey(this.habboInfo.getId())); // Friend
        this.response.appendBoolean(Messenger.friendRequested(
                this.viewer.getHabbo().getHabboInfo().getId(), this.habboInfo.getId())); // Friend Request Send
        this.response.appendBoolean(
                this.habboInfo.isOnline() && (this.habbo == null || !this.habbo.getHabboStats().hideOnline));

        List<Guild> guilds = new ArrayList<>();
        if (this.habbo != null) {
            List<Integer> toRemove = new ArrayList<>();
            for (int index = this.habbo.getHabboStats().guilds.size(); index > 0; index--) {
                int i = this.habbo.getHabboStats().guilds.get(index - 1);
                if (i == 0) continue;

                Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(i);

                if (guild != null) {
                    guilds.add(guild);
                } else {
                    toRemove.add(i);
                }
            }

            for (int i : toRemove) {
                this.habbo.getHabboStats().removeGuild(i);
            }
        } else {
            guilds = Emulator.getGameEnvironment().getGuildManager().getGuilds(this.habboInfo.getId());
        }

        this.response.appendInt(guilds.size());
        for (Guild guild : guilds) {
            this.response.appendInt(guild.getId());
            this.response.appendString(guild.getName());
            this.response.appendString(guild.getBadge());
            this.response.appendString(
                    Emulator.getGameEnvironment().getGuildManager().getSymbolColor(guild.getColorOne()).valueA);
            this.response.appendString(
                    Emulator.getGameEnvironment().getGuildManager().getSymbolColor(guild.getColorTwo()).valueA);
            this.response.appendBoolean(this.habbo != null && guild.getId() == this.habbo.getHabboStats().guild);
            this.response.appendInt(guild.getOwnerId());
            this.response.appendBoolean(guild.getOwnerId() == this.habboInfo.getId());
        }

        this.response.appendInt(Emulator.getIntUnixTimestamp() - this.habboInfo.getLastOnline()); // Secs ago.
        this.response.appendBoolean(true);

        this.response.appendInt(this.habboInfo.getInfostandBg());
        this.response.appendInt(this.habboInfo.getInfostandStand());
        this.response.appendInt(this.habboInfo.getInfostandOverlay());
        this.response.appendInt(this.habboInfo.getInfostandCardBg());
        this.response.appendInt(this.habboInfo.getInfostandBorder());
        UserCustomizationData customizationData = (this.habbo != null)
                ? UserCustomizationData.fromHabbo(this.habbo)
                : UserCustomizationData.fromUserId(this.habboInfo.getId());
        this.response.appendString(customizationData.nickIcon);
        this.response.appendString(customizationData.prefixText);
        this.response.appendString(customizationData.prefixColor);
        this.response.appendString(customizationData.prefixIcon);
        this.response.appendString(customizationData.prefixEffect);
        this.response.appendString(customizationData.prefixFont);
        this.response.appendString(customizationData.displayOrder);
        this.response.appendInt(this.getTotalBadges());
        accountLevel = AvatarLevelPlatform.game().getAvatarLevelManager().getLevel(this.habboInfo.getId());
        this.response.appendInt(accountLevel);
        this.response.appendString(customizationData.nickColor);
        this.response.appendString(customizationData.nickEffect);
        this.response.appendString(customizationData.nickFont);
        this.response.appendInt(this.habboInfo.getProfileDirection());
        this.response.appendInt(this.habboInfo.getProfileHeadDirection());
        this.response.appendString(this.habboInfo.getProfileAction());

        int respectsReceived = 0;
        if (this.habbo != null) {
            respectsReceived = this.habbo.getHabboStats().respectPointsReceived;
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT respects_received FROM users_settings WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, this.habboInfo.getId());
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        respectsReceived = set.getInt("respects_received");
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception while loading respects for user profile", e);
            }
        }
        this.response.appendInt(respectsReceived);

        return this.response;
    }

    private int getTotalBadges() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(DISTINCT badge_code) AS total_badges FROM users_badges WHERE user_id = ?")) {
            statement.setInt(1, this.habboInfo.getId());

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt("total_badges");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while loading total badges for extended profile", e);
        }

        return 0;
    }

    public HabboInfo getHabboInfo() {
        return habboInfo;
    }

    public Habbo getHabbo() {
        return habbo;
    }

    public GameClient getViewer() {
        return viewer;
    }
}

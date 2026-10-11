package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.levels.AvatarLevelPlatform;
import com.eu.habbo.messages.outgoing.inventory.InventoryBadgesComposer;
import com.eu.habbo.messages.outgoing.users.UserBadgesComposer;
import com.eu.habbo.networking.gameserver.badges.BadgeLeaderboardHttpHandler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BadgesComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgesComponent.class);

    private final Set<HabboBadge> badges = new HashSet<>();

    public BadgesComponent(Habbo habbo) {
        this.badges.addAll(loadBadges(habbo));
    }

    private static Set<HabboBadge> loadBadges(Habbo habbo) {
        Set<HabboBadge> badgesList = new HashSet<>();
        Set<String> staffBadges =
                Emulator.getGameEnvironment().getPermissionsManager().getStaffBadges();
        try (Connection connection =
                        AvatarLevelPlatform.database().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM users_badges WHERE user_id = ?")) {
            statement.setInt(1, habbo.getHabboInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    HabboBadge badge = new HabboBadge(set, habbo);

                    if (staffBadges.contains(badge.getCode())) {
                        boolean delete = true;

                        for (Rank rank : Emulator.getGameEnvironment()
                                .getPermissionsManager()
                                .getRanksByBadgeCode(badge.getCode())) {
                            if (rank.getId() == habbo.getHabboInfo().getRank().getId()) {
                                delete = false;
                                break;
                            }
                        }

                        if (delete) {
                            deleteBadge(habbo.getHabboInfo().getId(), badge.getCode());
                            continue;
                        }
                    }

                    badgesList.add(badge);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return badgesList;
    }

    public static void resetSlots(Habbo habbo) {
        habbo.getInventory().getBadgesComponent().replaceWearingBadges(habbo, Map.of());
    }

    public boolean replaceWearingBadges(Habbo habbo, Map<Integer, HabboBadge> wearingBadges) {
        synchronized (this.badges) {
            for (Map.Entry<Integer, HabboBadge> entry : wearingBadges.entrySet()) {
                int slot = entry.getKey();
                HabboBadge badge = entry.getValue();

                if (slot < 1 || slot > 10 || badge == null || !this.badges.contains(badge)) return false;

                // A badge granted immediately before this request may still be waiting
                // for its insert task. Give it a stable database id before the atomic
                // slot update so a quick logout can never lose the equipped state.
                if (badge.getId() <= 0) badge.run();
                if (badge.getId() <= 0) return false;
            }

            if (!HabboBadge.persistWearingSlots(habbo, wearingBadges)) return false;

            for (HabboBadge badge : this.badges) badge.setSlot(0);
            for (Map.Entry<Integer, HabboBadge> entry : wearingBadges.entrySet()) {
                entry.getValue().setSlot(entry.getKey());
            }

            return true;
        }
    }

    public static void synchronizeBadgeState(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null) return;

        habbo.getClient().sendResponse(new InventoryBadgesComposer(habbo));

        UserBadgesComposer composer = new UserBadgesComposer(
                habbo.getInventory().getBadgesComponent().getWearingBadges(),
                habbo.getHabboInfo().getId());

        if (habbo.getHabboInfo().getCurrentRoom() != null) {
            habbo.getHabboInfo().getCurrentRoom().sendComposer(composer.compose());
        } else {
            habbo.getClient().sendResponse(composer);
        }
    }

    public static ArrayList<HabboBadge> getBadgesOfflineHabbo(int userId) {
        ArrayList<HabboBadge> badgesList = new ArrayList<>();
        try (Connection connection =
                        AvatarLevelPlatform.database().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM users_badges WHERE slot_id > 0 AND user_id = ? ORDER BY slot_id ASC")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    badgesList.add(new HabboBadge(set, null));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return badgesList;
    }

    public static HabboBadge createBadge(String code, Habbo habbo) {
        HabboBadge badge = new HabboBadge(0, code, 0, habbo);
        try (Connection connection =
                AvatarLevelPlatform.database().getDataSource().getConnection()) {
            badge.insert(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Badge grant was not committed: " + code, exception);
        }
        habbo.getInventory().getBadgesComponent().addBadge(badge);
        return badge;
    }

    public static void deleteBadge(int userId, String badge) {
        try (Connection connection =
                        AvatarLevelPlatform.database().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE users_badges FROM users_badges WHERE user_id = ? AND badge_code LIKE ?")) {
            statement.setInt(1, userId);
            statement.setString(2, badge);
            if (statement.executeUpdate() > 0) {
                BadgeLeaderboardHttpHandler.invalidateCache();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public ArrayList<HabboBadge> getWearingBadges() {
        synchronized (this.badges) {
            ArrayList<HabboBadge> badgesList = new ArrayList<>();
            for (HabboBadge badge : this.badges) {
                if (badge.getSlot() == 0) continue;

                badgesList.add(badge);
            }

            badgesList.sort(new Comparator<HabboBadge>() {
                @Override
                public int compare(HabboBadge o1, HabboBadge o2) {
                    return o1.getSlot() - o2.getSlot();
                }
            });
            return badgesList;
        }
    }

    public Set<HabboBadge> getBadges() {
        return this.badges;
    }

    public ArrayList<HabboBadge> getBadgesSnapshot() {
        synchronized (this.badges) {
            ArrayList<HabboBadge> snapshot = new ArrayList<>(this.badges);
            snapshot.sort(Comparator.comparingInt(HabboBadge::getId));
            return snapshot;
        }
    }

    public boolean hasBadge(String badge) {
        return this.getBadge(badge) != null;
    }

    public HabboBadge getBadge(String badgeCode) {
        synchronized (this.badges) {
            for (HabboBadge badge : this.badges) {
                if (badge.getCode().equalsIgnoreCase(badgeCode)) return badge;
            }
            return null;
        }
    }

    public void addBadge(HabboBadge badge) {
        synchronized (this.badges) {
            this.badges.add(badge);
        }
    }

    public HabboBadge removeBadge(String badge) {
        synchronized (this.badges) {
            for (HabboBadge b : this.badges) {
                if (b.getCode().equalsIgnoreCase(badge)) {
                    this.badges.remove(b);
                    return b;
                }
            }
        }

        return null;
    }

    public void removeBadge(HabboBadge badge) {
        synchronized (this.badges) {
            this.badges.remove(badge);
        }
    }

    public void dispose() {
        synchronized (this.badges) {
            this.badges.clear();
        }
    }
}

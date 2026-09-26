package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileCosmeticsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileCosmeticsComponent.class);

    private final Set<String> ownedCosmetics = new HashSet<>();
    private final Habbo habbo;

    public ProfileCosmeticsComponent(Habbo habbo) {
        this.habbo = habbo;
        this.loadCosmetics();
    }

    private void loadCosmetics() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_id, category FROM users_profile_cosmetics WHERE user_id = ?")) {
            statement.setInt(1, this.habbo.getHabboInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    String category = set.getString("category").toLowerCase();
                    int itemId = set.getInt("item_id");
                    this.ownedCosmetics.add(category + ":" + itemId);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception loading user profile cosmetics", e);
        }

        // Preserve legacy ownership when the optional table exists.
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (ResultSet tables = connection
                    .getMetaData()
                    .getTables(connection.getCatalog(), null, "users_infostand_backgrounds", new String[] {"TABLE"})) {
                if (tables.next()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT background_id FROM users_infostand_backgrounds WHERE user_id = ?")) {
                        statement.setInt(1, this.habbo.getHabboInfo().getId());
                        try (ResultSet set = statement.executeQuery()) {
                            while (set.next()) {
                                this.ownedCosmetics.add("background:" + set.getInt("background_id"));
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception loading legacy profile backgrounds", e);
        }
    }

    public boolean hasCosmetic(String category, int itemId) {
        if (itemId == 0) return true;
        synchronized (this.ownedCosmetics) {
            return this.ownedCosmetics.contains(category.toLowerCase() + ":" + itemId);
        }
    }

    public boolean hasAnyCosmetic(String category) {
        String prefix = category.toLowerCase() + ":";
        synchronized (this.ownedCosmetics) {
            for (String key : this.ownedCosmetics) {
                if (key.startsWith(prefix)) return true;
            }
            return false;
        }
    }

    public void addCosmetic(String category, int itemId) {
        String key = category.toLowerCase() + ":" + itemId;
        synchronized (this.ownedCosmetics) {
            if (this.ownedCosmetics.add(key)) {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "INSERT IGNORE INTO users_profile_cosmetics (user_id, item_id, category) VALUES (?, ?, ?)")) {
                    statement.setInt(1, this.habbo.getHabboInfo().getId());
                    statement.setInt(2, itemId);
                    statement.setString(3, category.toLowerCase());
                    statement.execute();
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception adding owned profile cosmetic", e);
                }
            }
        }
    }

    public Set<String> getOwnedCosmetics() {
        synchronized (this.ownedCosmetics) {
            return new HashSet<>(this.ownedCosmetics);
        }
    }
}

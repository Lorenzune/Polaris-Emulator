package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class HabbiconsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(HabbiconsComponent.class);

    private final Set<Integer> ownedHabbicons = new HashSet<>();
    private final Habbo habbo;

    public HabbiconsComponent(Habbo habbo) {
        this.habbo = habbo;
        this.loadHabbicons();
    }

    private void loadHabbicons() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT habbicon_id FROM users_habbicons WHERE user_id = ?")) {
            statement.setInt(1, this.habbo.getHabboInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.ownedHabbicons.add(set.getInt("habbicon_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception loading user habbicons", e);
        }
    }

    public Set<Integer> getOwnedHabbicons() {
        synchronized (this.ownedHabbicons) {
            return new HashSet<>(this.ownedHabbicons);
        }
    }

    public boolean hasHabbicon(int habbiconId) {
        synchronized (this.ownedHabbicons) {
            return this.ownedHabbicons.contains(habbiconId);
        }
    }

    public void addOwnedHabbicon(int habbiconId) {
        synchronized (this.ownedHabbicons) {
            if (this.ownedHabbicons.add(habbiconId)) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "INSERT IGNORE INTO users_habbicons (user_id, habbicon_id) VALUES (?, ?)")) {
                    statement.setInt(1, this.habbo.getHabboInfo().getId());
                    statement.setInt(2, habbiconId);
                    statement.execute();
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception adding owned habbicon", e);
                }
            }
        }
    }
}

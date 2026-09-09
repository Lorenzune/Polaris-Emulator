package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserNick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserNicksComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserNicksComponent.class);

    private final List<UserNick> nicks = new ArrayList<>();
    private final Habbo habbo;

    public UserNicksComponent(Habbo habbo) {
        this.habbo = habbo;
        this.loadNicks();
    }

    private void loadNicks() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM user_nicks WHERE user_id = ?")) {
            statement.setInt(1, this.habbo.getHabboInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.nicks.add(new UserNick(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception loading user nicks", e);
        }
    }

    public List<UserNick> getNicks() {
        synchronized (this.nicks) {
            return new ArrayList<>(this.nicks);
        }
    }

    public UserNick getActiveNick() {
        synchronized (this.nicks) {
            for (UserNick nick : this.nicks) {
                if (nick.isActive()) return nick;
            }
        }
        return null;
    }

    public UserNick getNick(int id) {
        synchronized (this.nicks) {
            for (UserNick nick : this.nicks) {
                if (nick.getId() == id) return nick;
            }
        }
        return null;
    }

    public UserNick getNickByCatalogId(int catalogNickId) {
        synchronized (this.nicks) {
            for (UserNick nick : this.nicks) {
                if (nick.getCatalogNickId() == catalogNickId) return nick;
            }
        }
        return null;
    }

    public void addNick(UserNick nick) {
        synchronized (this.nicks) {
            this.nicks.add(nick);
        }
    }

    public void removeNick(UserNick nick) {
        synchronized (this.nicks) {
            this.nicks.remove(nick);
        }
    }

    public void setActive(int nickId) {
        synchronized (this.nicks) {
            for (UserNick nick : this.nicks) {
                boolean shouldBeActive = (nick.getId() == nickId);
                nick.setActive(shouldBeActive);
            }
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE user_nicks SET active = 0 WHERE user_id = ?")) {
                statement.setInt(1, this.habbo.getHabboInfo().getId());
                statement.executeUpdate();
            }

            if (nickId > 0) {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE user_nicks SET active = 1 WHERE id = ? AND user_id = ?")) {
                    statement.setInt(1, nickId);
                    statement.setInt(2, this.habbo.getHabboInfo().getId());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while updating active nick style", e);
        }
    }

    public void deactivateAll() {
        synchronized (this.nicks) {
            for (UserNick nick : this.nicks) {
                nick.setActive(false);
            }
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE user_nicks SET active = 0 WHERE user_id = ?")) {
            statement.setInt(1, this.habbo.getHabboInfo().getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while deactivating all user nicks", e);
        }
    }

    public void dispose() {
        synchronized (this.nicks) {
            this.nicks.clear();
        }
    }
}

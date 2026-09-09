package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserNickIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NickIconsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(NickIconsComponent.class);

    private final List<UserNickIcon> nickIcons = new ArrayList<>();
    private final Habbo habbo;

    public NickIconsComponent(Habbo habbo) {
        this.habbo = habbo;
        this.loadNickIcons();
    }

    private void loadNickIcons() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM user_nick_icons WHERE user_id = ?")) {
            statement.setInt(1, this.habbo.getHabboInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.nickIcons.add(new UserNickIcon(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public List<UserNickIcon> getNickIcons() {
        synchronized (this.nickIcons) {
            return new ArrayList<>(this.nickIcons);
        }
    }

    public UserNickIcon getActiveNickIcon() {
        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                if (nickIcon.isActive()) {
                    return nickIcon;
                }
            }
        }

        return null;
    }

    public UserNickIcon getNickIcon(int id) {
        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                if (nickIcon.getId() == id) {
                    return nickIcon;
                }
            }
        }

        return null;
    }

    public UserNickIcon getNickIconByKey(String iconKey) {
        if (iconKey == null) {
            return null;
        }
        String clean = iconKey.trim();
        String base = clean;
        if (base.toLowerCase().endsWith(".gif") || base.toLowerCase().endsWith(".png")) {
            base = base.substring(0, base.length() - 4);
        }

        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                if (nickIcon.getIconKey() == null) continue;
                String ownedKey = nickIcon.getIconKey().trim();
                String ownedBase = ownedKey;
                if (ownedBase.toLowerCase().endsWith(".gif") || ownedBase.toLowerCase().endsWith(".png")) {
                    ownedBase = ownedBase.substring(0, ownedBase.length() - 4);
                }
                if (ownedKey.equalsIgnoreCase(clean) || ownedBase.equalsIgnoreCase(base)) {
                    return nickIcon;
                }
            }
        }

        return null;
    }

    public void addNickIcon(UserNickIcon nickIcon) {
        synchronized (this.nickIcons) {
            this.nickIcons.add(nickIcon);
        }
    }

    public void removeNickIcon(UserNickIcon nickIcon) {
        synchronized (this.nickIcons) {
            this.nickIcons.remove(nickIcon);
        }
    }

    public void deleteNickIcon(UserNickIcon nickIcon) {
        this.removeNickIcon(nickIcon);
        nickIcon.needsDelete(true);
        persist(nickIcon);
    }

    public void setActive(int nickIconId) {
        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                boolean shouldBeActive = (nickIcon.getId() == nickIconId);

                if (nickIcon.isActive() != shouldBeActive) {
                    nickIcon.setActive(shouldBeActive);
                    persist(nickIcon);
                }
            }
        }
    }

    public void deactivateAll() {
        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                if (nickIcon.isActive()) {
                    nickIcon.setActive(false);
                    persist(nickIcon);
                }
            }
        }
    }

    public void dispose() {
        synchronized (this.nickIcons) {
            this.nickIcons.clear();
        }
    }

    private static void persist(UserNickIcon nickIcon) {
        Emulator.getThreading().run(nickIcon);
    }
}

package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class UserNick implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserNick.class);

    private int id;
    private final int userId;
    private String color;
    private String effect;
    private String font;
    private int catalogNickId;
    private boolean active;
    private boolean needsInsert;
    private boolean needsUpdate;
    private boolean needsDelete;

    public UserNick(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.userId = set.getInt("user_id");
        this.color = set.getString("color");
        if (this.color == null) this.color = "#000000";
        this.effect = set.getString("effect");
        if (this.effect == null) this.effect = "";
        this.font = set.getString("font");
        if (this.font == null) this.font = "";
        this.catalogNickId = set.getInt("catalog_nick_id");
        this.active = set.getBoolean("active");
        this.needsInsert = false;
        this.needsUpdate = false;
        this.needsDelete = false;
    }

    public UserNick(int userId, String color, String effect, String font, int catalogNickId) {
        this.id = 0;
        this.userId = userId;
        this.color = color != null ? color : "#000000";
        this.effect = effect != null ? effect : "";
        this.font = font != null ? font : "";
        this.catalogNickId = catalogNickId;
        this.active = false;
        this.needsInsert = true;
        this.needsUpdate = false;
        this.needsDelete = false;
    }

    @Override
    public void run() {
        try {
            if (this.needsInsert) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO user_nicks (user_id, color, effect, font, active, catalog_nick_id) VALUES (?, ?, ?, ?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, this.userId);
                    statement.setString(2, this.color);
                    statement.setString(3, this.effect);
                    statement.setString(4, this.font);
                    statement.setBoolean(5, this.active);
                    statement.setInt(6, this.catalogNickId);
                    statement.execute();
                    try (ResultSet set = statement.getGeneratedKeys()) {
                        if (set.next()) {
                            this.id = set.getInt(1);
                        }
                    }
                }
                this.needsInsert = false;
            }

            if (this.needsUpdate && this.id > 0) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                         "UPDATE user_nicks SET color = ?, effect = ?, font = ?, active = ? WHERE id = ? AND user_id = ?")) {
                    statement.setString(1, this.color);
                    statement.setString(2, this.effect);
                    statement.setString(3, this.font);
                    statement.setBoolean(4, this.active);
                    statement.setInt(5, this.id);
                    statement.setInt(6, this.userId);
                    statement.execute();
                }
                this.needsUpdate = false;
            } else if (this.needsDelete && this.id > 0) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement("DELETE FROM user_nicks WHERE id = ? AND user_id = ?")) {
                    statement.setInt(1, this.id);
                    statement.setInt(2, this.userId);
                    statement.execute();
                }
                this.needsDelete = false;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception in UserNick", e);
        }
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
        this.needsUpdate = true;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
        this.needsUpdate = true;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
        this.needsUpdate = true;
    }

    public int getCatalogNickId() {
        return catalogNickId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.needsUpdate = true;
    }

    public void delete() {
        this.needsDelete = true;
        Emulator.getThreading().run(this);
    }
}

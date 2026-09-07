package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RoomChatBubbleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomChatBubbleManager.class);

    public RoomChatBubbleManager() {
        this.reload();
    }

    public void reload() {
        // Remove any previously DB-loaded dynamic bubbles (type > 53) before re-loading
        RoomChatMessageBubbles.removeDynamicBubbles();

        int loaded = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `type`, `name`, `permission`, `enabled` FROM `chat_bubbles` WHERE `enabled` = 1 AND `type` > 53")) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int type = resultSet.getInt("type");
                    String name = resultSet.getString("name");
                    String permission = resultSet.getString("permission");

                    // Dynamic DB bubbles: always overridable, never trigger talking furniture by default
                    RoomChatMessageBubbles.addDynamicBubble(type, name, permission != null ? permission : "", true, false);
                    loaded++;
                }
            }

            LOGGER.info("Loaded {} dynamic chat bubble(s) from database.", loaded);
        } catch (SQLException e) {
            LOGGER.error("Failed to load dynamic chat bubbles from database.", e);
        }
    }
}
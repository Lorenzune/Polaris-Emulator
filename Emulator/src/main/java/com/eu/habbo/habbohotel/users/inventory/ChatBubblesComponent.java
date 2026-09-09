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

public class ChatBubblesComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatBubblesComponent.class);

    private final Set<Integer> ownedBubbles = new HashSet<>();
    private final Habbo habbo;

    public ChatBubblesComponent(Habbo habbo) {
        this.habbo = habbo;
        this.loadBubbles();
    }

    private void loadBubbles() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT bubble_id FROM users_chat_bubbles WHERE user_id = ?")) {
            statement.setInt(1, this.habbo.getHabboInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.ownedBubbles.add(set.getInt("bubble_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception loading user chat bubbles", e);
        }
    }

    public Set<Integer> getOwnedBubbles() {
        synchronized (this.ownedBubbles) {
            return new HashSet<>(this.ownedBubbles);
        }
    }

    public boolean ownsBubble(int bubbleId) {
        synchronized (this.ownedBubbles) {
            return this.ownedBubbles.contains(bubbleId);
        }
    }

    public void addOwnedBubble(int bubbleId) {
        synchronized (this.ownedBubbles) {
            if (this.ownedBubbles.add(bubbleId)) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "INSERT IGNORE INTO users_chat_bubbles (user_id, bubble_id) VALUES (?, ?)")) {
                    statement.setInt(1, this.habbo.getHabboInfo().getId());
                    statement.setInt(2, bubbleId);
                    statement.execute();
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception adding owned chat bubble", e);
                }
            }
        }
    }
}

package com.eu.habbo.messages.incoming.inventory.chatbubbles;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.chatbubbles.UserChatBubblesComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PurchaseChatBubbleEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseChatBubbleEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        int bubbleId = this.packet.readInt();
        if (bubbleId <= 0) {
            this.fail("Fumetto chat non valido.");
            return;
        }

        if (habbo.getInventory().getChatBubblesComponent().ownsBubble(bubbleId)) {
            this.fail("Possiedi già questo fumetto chat!");
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT type, name, is_purchasable, price_credits, price_duckets, price_diamonds " +
                             "FROM chat_bubbles WHERE type = ? AND enabled = 1 LIMIT 1")) {
            statement.setInt(1, bubbleId);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    this.fail("Fumetto chat non disponibile per l'acquisto.");
                    return;
                }

                boolean isPurchasable = set.getBoolean("is_purchasable");
                int priceCredits = set.getInt("price_credits");
                int priceDuckets = set.getInt("price_duckets");
                int priceDiamonds = set.getInt("price_diamonds");

                if (!isPurchasable) {
                    this.fail("Questo fumetto chat non è in vendita.");
                    return;
                }

                // Check balances
                if (priceCredits > 0 && habbo.getHabboInfo().getCredits() < priceCredits) {
                    this.fail("Non hai abbastanza Crediti!");
                    return;
                }
                if (priceDuckets > 0 && habbo.getHabboInfo().getCurrencyAmount(0) < priceDuckets) {
                    this.fail("Non hai abbastanza Duckets!");
                    return;
                }
                if (priceDiamonds > 0 && habbo.getHabboInfo().getCurrencyAmount(5) < priceDiamonds) {
                    this.fail("Non hai abbastanza Diamanti!");
                    return;
                }

                // Deduct balances
                if (priceCredits > 0) {
                    habbo.giveCredits(-priceCredits);
                }
                if (priceDuckets > 0) {
                    habbo.givePoints(0, -priceDuckets);
                }
                if (priceDiamonds > 0) {
                    habbo.givePoints(5, -priceDiamonds);
                }

                // Save ownership
                habbo.getInventory().getChatBubblesComponent().addOwnedBubble(bubbleId);

                // Automatically activate purchased bubble
                RoomChatMessageBubbles bubbleObj = RoomChatMessageBubbles.getBubble(bubbleId);
                if (bubbleObj != null) {
                    habbo.getHabboStats().chatColor = bubbleObj;
                }

                AchievementInteractions.progressUnique(habbo, "ChatStyleOwner", Integer.toString(bubbleId));

                // Send updates to client
                this.client.sendResponse(new UserCurrencyComposer(habbo));
                this.client.sendResponse(new UserChatBubblesComposer(habbo));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception purchasing chat bubble {}", bubbleId, e);
            this.fail("Si è verificato un errore durante l'acquisto del fumetto.");
        }
    }

    private void fail(String message) {
        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, message));
    }
}

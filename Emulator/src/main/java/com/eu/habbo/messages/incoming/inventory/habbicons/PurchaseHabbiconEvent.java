package com.eu.habbo.messages.incoming.inventory.habbicons;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.habbicons.UserHabbiconsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PurchaseHabbiconEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseHabbiconEvent.class);

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

        int habbiconId = this.packet.readInt();
        if (habbiconId <= 0) {
            this.fail("Habbicon non valida.");
            return;
        }

        if (habbo.getInventory().getHabbiconsComponent().hasHabbicon(habbiconId)) {
            this.fail("Possiedi già questa Habbicon!");
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, display_name, points, points_type, enabled, is_free " +
                             "FROM custom_habbicons_catalog WHERE id = ? AND enabled = 1 LIMIT 1")) {
            statement.setInt(1, habbiconId);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    if (habbiconId <= 60) {
                        int defaultCost = (habbiconId <= 33) ? 0 : 10;
                        if (defaultCost > 0) {
                            if (habbo.getHabboInfo().getCurrencyAmount(0) < defaultCost) {
                                this.fail("Non hai abbastanza Duckets!");
                                return;
                            }
                            habbo.givePoints(0, -defaultCost);
                        }
                        habbo.getInventory().getHabbiconsComponent().addOwnedHabbicon(habbiconId);
                        AchievementInteractions.progress(habbo, "HabbiconCollected");
                        this.client.sendResponse(new UserCurrencyComposer(habbo));
                        this.client.sendResponse(new UserHabbiconsComposer(habbo));
                        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Hai sbloccato la Habbicon!"));
                        return;
                    }

                    this.fail("Habbicon non disponibile per l'acquisto.");
                    return;
                }

                String displayName = set.getString("display_name");
                int points = set.getInt("points");
                int pointsType = set.getInt("points_type");
                boolean isFree = set.getBoolean("is_free") || (points <= 0);

                if (!isFree && points > 0) {
                    if (pointsType == -1) {
                        if (habbo.getHabboInfo().getCredits() < points) {
                            this.fail("Non hai abbastanza Crediti!");
                            return;
                        }
                        habbo.giveCredits(-points);
                    } else if (pointsType == 0 || pointsType == 103) {
                        if (habbo.getHabboInfo().getCurrencyAmount(0) < points) {
                            this.fail("Non hai abbastanza Duckets!");
                            return;
                        }
                        habbo.givePoints(0, -points);
                    } else {
                        if (habbo.getHabboInfo().getCurrencyAmount(pointsType) < points) {
                            this.fail("Non hai abbastanza valuta per questo acquisto!");
                            return;
                        }
                        habbo.givePoints(pointsType, -points);
                    }
                }

                habbo.getInventory().getHabbiconsComponent().addOwnedHabbicon(habbiconId);
                AchievementInteractions.progress(habbo, "HabbiconCollected");

                this.client.sendResponse(new UserCurrencyComposer(habbo));
                this.client.sendResponse(new UserHabbiconsComposer(habbo));
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Hai sbloccato '" + displayName + "'!"));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception purchasing habbicon {}", habbiconId, e);
            this.fail("Si è verificato un errore durante l'acquisto dell'Habbicon.");
        }
    }

    private void fail(String message) {
        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, message));
    }
}

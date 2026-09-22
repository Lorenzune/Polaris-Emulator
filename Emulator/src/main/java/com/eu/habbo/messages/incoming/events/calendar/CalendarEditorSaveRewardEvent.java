package com.eu.habbo.messages.incoming.events.calendar;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarCampaign;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.events.calendar.CalendarEditorResultComposer;
import com.eu.habbo.messages.outgoing.events.calendar.CalendarEditorStateComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalendarEditorSaveRewardEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarEditorSaveRewardEvent.class);

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null
                || (!this.client.getHabbo().hasPermission(Permission.ACC_CALENDAR_EDITOR)
                        && !this.client.getHabbo().hasPermission(Permission.ACC_CALENDAR_FORCE))) {
            return;
        }

        int id = this.packet.readInt();
        String campaignName = this.packet.readString();
        String productName = this.packet.readString();
        String customImage = this.packet.readString();
        int credits = this.packet.readInt();
        int pixels = this.packet.readInt();
        int points = this.packet.readInt();
        int pointsType = this.packet.readInt();
        String badge = this.packet.readString();
        int itemId = this.packet.readInt();
        String subscriptionType = this.packet.readString();
        int subscriptionDays = this.packet.readInt();

        if (campaignName == null || campaignName.trim().isEmpty()) {
            campaignName = Emulator.getConfig().getValue("hotel.calendar.default", "test");
        }

        CalendarCampaign campaign =
                Emulator.getGameEnvironment().getCalendarManager().getCalendarCampaign(campaignName);
        int campaignId = campaign != null ? campaign.getId() : 1;

        try {
            if (id <= 0) {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO calendar_rewards (campaign_id, product_name, custom_image, credits, pixels, points, points_type, badge, item_id, subscription_type, subscription_days) "
                                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, campaignId);
                    statement.setString(2, productName != null ? productName : "");
                    statement.setString(3, customImage != null ? customImage : "");
                    statement.setInt(4, credits);
                    statement.setInt(5, pixels);
                    statement.setInt(6, points);
                    statement.setInt(7, pointsType);
                    statement.setString(8, badge != null ? badge : "");
                    statement.setInt(9, itemId);
                    statement.setString(10, subscriptionType != null ? subscriptionType : "");
                    statement.setInt(11, subscriptionDays);
                    statement.executeUpdate();
                }
            } else {
                SqlQueries.update(
                        "UPDATE calendar_rewards SET campaign_id = ?, product_name = ?, custom_image = ?, credits = ?, pixels = ?, points = ?, points_type = ?, badge = ?, item_id = ?, subscription_type = ?, subscription_days = ? WHERE id = ?",
                        campaignId,
                        productName != null ? productName : "",
                        customImage != null ? customImage : "",
                        credits,
                        pixels,
                        points,
                        pointsType,
                        badge != null ? badge : "",
                        itemId,
                        subscriptionType != null ? subscriptionType : "",
                        subscriptionDays,
                        id);
            }

            Emulator.getGameEnvironment().getCalendarManager().reload();

            this.client.sendResponse(new CalendarEditorResultComposer(true, "Reward saved successfully!"));

            CalendarCampaign updatedCampaign =
                    Emulator.getGameEnvironment().getCalendarManager().getCalendarCampaign(campaignName);
            if (updatedCampaign != null) {
                this.client.sendResponse(new CalendarEditorStateComposer(
                        updatedCampaign.getId(),
                        updatedCampaign.getName(),
                        updatedCampaign.getRewards().values()));
            }
        } catch (Exception e) {
            LOGGER.error("Error saving calendar reward", e);
            this.client.sendResponse(
                    new CalendarEditorResultComposer(false, "Failed to save reward: " + e.getMessage()));
        }
    }
}

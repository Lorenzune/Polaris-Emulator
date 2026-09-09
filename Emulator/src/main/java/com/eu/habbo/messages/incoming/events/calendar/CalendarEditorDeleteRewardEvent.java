package com.eu.habbo.messages.incoming.events.calendar;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarCampaign;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.events.calendar.CalendarEditorResultComposer;
import com.eu.habbo.messages.outgoing.events.calendar.CalendarEditorStateComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalendarEditorDeleteRewardEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarEditorDeleteRewardEvent.class);

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null || (!this.client.getHabbo().hasPermission(Permission.ACC_CALENDAR_EDITOR) && !this.client.getHabbo().hasPermission(Permission.ACC_CALENDAR_FORCE))) {
            return;
        }

        int id = this.packet.readInt();
        String campaignName = this.packet.readString();

        if (campaignName == null || campaignName.trim().isEmpty()) {
            campaignName = Emulator.getConfig().getValue("hotel.calendar.default", "test");
        }

        try {
            SqlQueries.update("DELETE FROM calendar_rewards WHERE id = ? LIMIT 1", id);
            Emulator.getGameEnvironment().getCalendarManager().reload();

            this.client.sendResponse(new CalendarEditorResultComposer(true, "Reward deleted successfully!"));

            CalendarCampaign updatedCampaign = Emulator.getGameEnvironment().getCalendarManager().getCalendarCampaign(campaignName);
            if (updatedCampaign != null) {
                this.client.sendResponse(new CalendarEditorStateComposer(
                        updatedCampaign.getId(),
                        updatedCampaign.getName(),
                        updatedCampaign.getRewards().values()));
            }
        } catch (Exception e) {
            LOGGER.error("Error deleting calendar reward", e);
            this.client.sendResponse(new CalendarEditorResultComposer(false, "Failed to delete reward: " + e.getMessage()));
        }
    }
}

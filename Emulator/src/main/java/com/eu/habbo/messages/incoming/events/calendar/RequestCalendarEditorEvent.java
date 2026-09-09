package com.eu.habbo.messages.incoming.events.calendar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarCampaign;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.events.calendar.CalendarEditorStateComposer;

import java.util.Collections;

public class RequestCalendarEditorEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null || (!this.client.getHabbo().hasPermission(Permission.ACC_CALENDAR_EDITOR) && !this.client.getHabbo().hasPermission(Permission.ACC_CALENDAR_FORCE))) {
            return;
        }

        String campaignName = this.packet.readString();
        if (campaignName == null || campaignName.trim().isEmpty()) {
            campaignName = Emulator.getConfig().getValue("hotel.calendar.default", "test");
        }

        CalendarCampaign campaign = Emulator.getGameEnvironment().getCalendarManager().getCalendarCampaign(campaignName);
        if (campaign != null) {
            this.client.sendResponse(new CalendarEditorStateComposer(
                    campaign.getId(),
                    campaign.getName(),
                    campaign.getRewards().values()));
        } else {
            this.client.sendResponse(new CalendarEditorStateComposer(0, campaignName, Collections.emptyList()));
        }
    }
}

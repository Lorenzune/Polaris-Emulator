package com.eu.habbo.messages.outgoing.events.calendar;

import com.eu.habbo.habbohotel.campaign.calendar.CalendarRewardObject;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;

public class CalendarEditorStateComposer extends MessageComposer {
    private final int campaignId;
    private final String campaignName;
    private final Collection<CalendarRewardObject> rewards;

    public CalendarEditorStateComposer(int campaignId, String campaignName, Collection<CalendarRewardObject> rewards) {
        this.campaignId = campaignId;
        this.campaignName = campaignName;
        this.rewards = rewards;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CalendarEditorStateComposer);
        this.response.appendInt(this.campaignId);
        this.response.appendString(this.campaignName != null ? this.campaignName : "");
        this.response.appendInt(this.rewards != null ? this.rewards.size() : 0);

        if (this.rewards != null) {
            for (CalendarRewardObject reward : this.rewards) {
                this.response.appendInt(reward.getId());
                this.response.appendString(reward.getProductName() != null ? reward.getProductName() : "");
                this.response.appendString(reward.getCustomImage() != null ? reward.getCustomImage() : "");
                this.response.appendInt(reward.getCredits());
                this.response.appendInt(reward.getPixels());
                this.response.appendInt(reward.getPoints());
                this.response.appendInt(reward.getPointsType());
                this.response.appendString(reward.getBadge() != null ? reward.getBadge() : "");
                this.response.appendInt(
                        reward.getItem() != null ? reward.getItem().getId() : 0);
                this.response.appendString(reward.getSubscriptionType() != null ? reward.getSubscriptionType() : "");
                this.response.appendInt(reward.getSubscriptionDays());
            }
        }

        return this.response;
    }
}

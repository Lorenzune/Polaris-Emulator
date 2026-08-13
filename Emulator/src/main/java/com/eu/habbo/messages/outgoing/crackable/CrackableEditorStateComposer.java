package com.eu.habbo.messages.outgoing.crackable;

import com.eu.habbo.habbohotel.items.CrackableReward;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Rank-gated crackable configuration sent to the infostand editor. */
public class CrackableEditorStateComposer extends MessageComposer {
    private final boolean canEdit;
    private final int objectId;
    private final Item item;
    private final CrackableReward reward;

    public CrackableEditorStateComposer(boolean canEdit, int objectId, Item item, CrackableReward reward) {
        this.canEdit = canEdit;
        this.objectId = objectId;
        this.item = item;
        this.reward = reward;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CrackableEditorStateComposer);
        this.response.appendBoolean(this.canEdit);
        this.response.appendInt(this.objectId);
        this.response.appendInt(this.item == null ? 0 : this.item.getId());
        this.response.appendString(this.item == null ? "" : this.item.getName());
        this.response.appendBoolean(this.reward != null);
        if (!this.canEdit || this.item == null) return this.response;

        this.response.appendInt(this.reward == null ? 0 : this.reward.count);
        this.response.appendString(this.reward == null ? "" : serializePrizes(this.reward));
        this.response.appendString(this.reward == null ? "" : this.reward.achievementTick);
        this.response.appendString(this.reward == null ? "" : this.reward.achievementCracked);
        this.response.appendInt(this.reward == null ? 0 : this.reward.requiredEffect);
        this.response.appendInt(this.reward == null ? 0 : this.reward.subscriptionDuration);
        this.response.appendString(this.reward == null || this.reward.subscriptionType == null ? "" : this.reward.subscriptionType.name());
        return this.response;
    }

    private static String serializePrizes(CrackableReward reward) {
        StringBuilder value = new StringBuilder();
        reward.prizes.forEach((itemId, range) -> {
            if (value.length() > 0) value.append(';');
            value.append(itemId).append(':').append(Math.max(1, range.getValue() - range.getKey()));
        });
        return value.toString();
    }
}

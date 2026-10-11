package com.eu.habbo.messages.outgoing.quests;

import com.eu.habbo.habbohotel.quests.QuestManager;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredQuestStateComposer extends MessageComposer {
    private final QuestManager.State state;

    public WiredQuestStateComposer(QuestManager.State state) {
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.WiredQuestStateComposer);
        response.appendString(WiredManager.getGson().toJson(state));
        return response;
    }
}

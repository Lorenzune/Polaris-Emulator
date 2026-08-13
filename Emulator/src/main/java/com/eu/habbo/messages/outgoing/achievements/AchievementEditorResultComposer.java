package com.eu.habbo.messages.outgoing.achievements;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AchievementEditorResultComposer extends MessageComposer {
    private final boolean success;
    private final String message;
    private final int achievementId;

    public AchievementEditorResultComposer(boolean success, String message, int achievementId) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.achievementId = achievementId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AchievementEditorResultComposer);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.message);
        this.response.appendInt(this.achievementId);
        return this.response;
    }
}

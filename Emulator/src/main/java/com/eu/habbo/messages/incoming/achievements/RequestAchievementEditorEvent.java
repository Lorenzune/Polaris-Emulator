package com.eu.habbo.messages.incoming.achievements;

import com.eu.habbo.messages.incoming.MessageHandler;

public class RequestAchievementEditorEvent extends MessageHandler {
    @Override
    public void handle() {
        AchievementEditorSupport.sendState(this.client);
    }
}

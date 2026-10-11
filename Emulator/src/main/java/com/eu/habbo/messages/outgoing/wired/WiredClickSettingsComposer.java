package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredClickSettingsComposer extends MessageComposer {
    private final int roomId, userOption, furniOption;
    private final boolean canModify;

    public WiredClickSettingsComposer(int roomId, int userOption, int furniOption, boolean canModify) {
        this.roomId = roomId;
        this.userOption = userOption;
        this.furniOption = furniOption;
        this.canModify = canModify;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredClickSettingsComposer);
        this.response.appendInt(roomId);
        this.response.appendInt(userOption);
        this.response.appendInt(furniOption);
        this.response.appendBoolean(canModify);
        return this.response;
    }
}

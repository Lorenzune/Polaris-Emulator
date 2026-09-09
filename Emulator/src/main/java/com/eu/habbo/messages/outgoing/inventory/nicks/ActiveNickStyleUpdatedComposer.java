package com.eu.habbo.messages.outgoing.inventory.nicks;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ActiveNickStyleUpdatedComposer extends MessageComposer {
    private final int nickId;
    private final String color;
    private final String effect;
    private final String font;

    public ActiveNickStyleUpdatedComposer(int nickId, String color, String effect, String font) {
        this.nickId = nickId;
        this.color = color != null ? color : "";
        this.effect = effect != null ? effect : "";
        this.font = font != null ? font : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ActiveNickStyleUpdatedComposer);
        this.response.appendInt(this.nickId);
        this.response.appendString(this.color);
        this.response.appendString(this.effect);
        this.response.appendString(this.font);
        return this.response;
    }
}

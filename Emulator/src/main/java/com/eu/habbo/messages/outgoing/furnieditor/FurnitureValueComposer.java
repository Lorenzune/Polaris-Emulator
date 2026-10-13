package com.eu.habbo.messages.outgoing.furnieditor;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.google.gson.Gson;
import java.util.Map;

public class FurnitureValueComposer extends MessageComposer {
    private final int spriteId;
    private final String type;
    private final Map<String, Object> value;

    public FurnitureValueComposer(int spriteId, String type, Map<String, Object> value) {
        this.spriteId = spriteId;
        this.type = type;
        this.value = value;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(com.eu.habbo.messages.outgoing.Outgoing.FurnitureValueComposer);
        this.response.appendInt(spriteId);
        this.response.appendString(type);
        this.response.appendString(new Gson().toJson(value));
        return this.response;
    }
}

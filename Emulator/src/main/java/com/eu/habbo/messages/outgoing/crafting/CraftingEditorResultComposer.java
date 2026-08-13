package com.eu.habbo.messages.outgoing.crafting;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CraftingEditorResultComposer extends MessageComposer {
    private final boolean success;
    private final String message;
    private final int recipeId;

    public CraftingEditorResultComposer(boolean success, String message, int recipeId) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.recipeId = recipeId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CraftingEditorResultComposer);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.message);
        this.response.appendInt(this.recipeId);
        return this.response;
    }
}

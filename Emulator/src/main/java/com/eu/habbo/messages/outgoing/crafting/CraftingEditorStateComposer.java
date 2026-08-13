package com.eu.habbo.messages.outgoing.crafting;

import com.eu.habbo.habbohotel.crafting.CraftingRecipe;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Collection;

/** Rank-gated metadata used by the in-client crafting brush/editor. */
public class CraftingEditorStateComposer extends MessageComposer {
    private final boolean canEdit;
    private final int stationId;
    private final Collection<CraftingRecipe> recipes;

    public CraftingEditorStateComposer(boolean canEdit, int stationId, Collection<CraftingRecipe> recipes) {
        this.canEdit = canEdit;
        this.stationId = stationId;
        this.recipes = recipes;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CraftingEditorStateComposer);
        this.response.appendBoolean(this.canEdit);
        this.response.appendInt(this.stationId);
        this.response.appendInt(this.canEdit && this.recipes != null ? this.recipes.size() : 0);

        if (!this.canEdit || this.recipes == null) return this.response;

        for (CraftingRecipe recipe : this.recipes) {
            this.response.appendInt(recipe.getId());
            this.response.appendString(recipe.getName());
            this.response.appendString(recipe.getReward() == null ? "" : recipe.getReward().getName());
            this.response.appendString(recipe.getAchievement() == null ? "" : recipe.getAchievement());
            this.response.appendBoolean(recipe.isSecret());
            this.response.appendBoolean(recipe.isLimited());
            this.response.appendInt(recipe.getRemaining());
            this.response.appendInt(recipe.getIngredients().size());
            for (var ingredient : recipe.getIngredients().entrySet()) {
                Item item = ingredient.getKey();
                this.response.appendString(item.getName());
                this.response.appendInt(ingredient.getValue());
            }
        }

        return this.response;
    }
}

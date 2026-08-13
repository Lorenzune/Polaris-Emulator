package com.eu.habbo.messages.incoming.crafting;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.crafting.CraftingAltar;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.outgoing.crafting.CraftableProductsComposer;
import com.eu.habbo.messages.outgoing.crafting.CraftingEditorStateComposer;

import java.util.Collections;

/** Shared permission and refresh path for the rank-7 crafting brush. */
final class CraftingEditorSupport {
    private CraftingEditorSupport() { }

    static boolean canEdit(GameClient client) {
        return client != null && client.getHabbo() != null
                && client.getHabbo().hasPermission(Permission.ACC_CRAFTING_EDITOR);
    }

    static void sendStateAndRecipes(GameClient client, int objectId) {
        if (client == null || client.getHabbo() == null) return;

        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        HabboItem item = room.getHabboItem(objectId);
        if (item == null) return;

        Item baseItem = item.getBaseItem();
        CraftingAltar altar = Emulator.getGameEnvironment().getCraftingManager().getAltar(baseItem);
        boolean canEdit = canEdit(client);

        client.sendResponse(new CraftingEditorStateComposer(
                canEdit,
                baseItem.getId(),
                altar == null ? Collections.emptyList() : altar.getRecipes()));

        if (altar != null) {
            client.sendResponse(new CraftableProductsComposer(
                    altar.getRecipesForHabbo(client.getHabbo()),
                    altar.getIngredients()));
        } else if (canEdit) {
            // Keep the editor open for an empty/unmapped table so rank 7 can
            // create the first recipe directly from the client.
            client.sendResponse(new CraftableProductsComposer(
                    Collections.emptyList(), Collections.emptyList()));
        }
    }
}

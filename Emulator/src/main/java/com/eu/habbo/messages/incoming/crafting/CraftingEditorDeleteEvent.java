package com.eu.habbo.messages.incoming.crafting;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.crafting.CraftingEditorResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class CraftingEditorDeleteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CRAFTING_EDITOR)) return;

        int objectId = this.packet.readInt();
        int recipeId = this.packet.readInt();
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        HabboItem station = room == null ? null : room.getHabboItem(objectId);
        if (station == null || recipeId <= 0) {
            this.client.sendResponse(new CraftingEditorResultComposer(false, "Dati non validi", recipeId));
            return;
        }

        String pattern = "(^|[^0-9])" + station.getBaseItem().getId() + "([^0-9]|$)";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM crafting_recipes WHERE id = ? AND station_ids REGEXP ?")) {
            statement.setInt(1, recipeId);
            statement.setString(2, pattern);
            if (statement.executeUpdate() != 1) {
                this.client.sendResponse(new CraftingEditorResultComposer(false, "Ricetta non trovata per questo tavolo", recipeId));
                return;
            }
        }

        Emulator.getGameEnvironment().getCraftingManager().reload();
        this.client.sendResponse(new CraftingEditorResultComposer(true, "Ricetta eliminata", recipeId));
        CraftingEditorSupport.sendStateAndRecipes(this.client, objectId);
    }
}

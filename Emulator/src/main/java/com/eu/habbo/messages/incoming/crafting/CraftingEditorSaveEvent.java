package com.eu.habbo.messages.incoming.crafting;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.crafting.CraftingEditorResultComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class CraftingEditorSaveEvent extends MessageHandler {
    private static final int MAX_INGREDIENTS = 50;
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.:*\\-]{1,64}");

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CRAFTING_EDITOR)) return;

        int objectId = this.packet.readInt();
        int recipeId = this.packet.readInt();
        String productName = this.packet.readString();
        String rewardName = this.packet.readString();
        boolean secret = this.packet.readBoolean();
        boolean limited = this.packet.readBoolean();
        int remaining = this.packet.readInt();
        String achievement = this.packet.readString();
        int ingredientCount = this.packet.readInt();

        if (ingredientCount < 1 || ingredientCount > MAX_INGREDIENTS
                || !validName(productName) || !validName(rewardName)
                || (achievement != null && !achievement.isEmpty() && !validName(achievement))) {
            fail("Dati ricetta non validi", recipeId);
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        HabboItem station = room == null ? null : room.getHabboItem(objectId);
        if (station == null) {
            fail("Tavolo non trovato", recipeId);
            return;
        }

        Item reward = Emulator.getGameEnvironment().getItemManager().getItem(rewardName);
        if (reward == null) {
            fail("Reward non trovato", recipeId);
            return;
        }

        JsonArray ingredients = new JsonArray();
        Set<String> ingredientNames = new HashSet<>();
        for (int i = 0; i < ingredientCount; i++) {
            String name = this.packet.readString();
            int amount = this.packet.readInt();
            if (!validName(name) || amount <= 0 || amount > 999 || !ingredientNames.add(name)) {
                fail("Ingredienti non validi", recipeId);
                return;
            }

            Item item = Emulator.getGameEnvironment().getItemManager().getItem(name);
            if (item == null) {
                fail("Ingrediente non trovato: " + name, recipeId);
                return;
            }

            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("classname", name);
            ingredient.addProperty("amount", amount);
            ingredients.add(ingredient);
        }

        int normalizedRemaining = limited ? Math.max(0, remaining) : 0;
        String stationIds = "[" + station.getBaseItem().getId() + "]";
        String stationPattern = "(^|[^0-9])" + station.getBaseItem().getId() + "([^0-9]|$)";
        int savedId = recipeId;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            if (recipeId > 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE crafting_recipes SET product_name = ?, reward_name = ?, station_ids = ?, ingredients = ?, enabled = '1', achievement = ?, secret = ?, limited = ?, remaining = ? WHERE id = ? AND station_ids REGEXP ?")) {
                    bindRecipe(statement, productName, rewardName, stationIds, ingredients.toString(), achievement, secret, limited, normalizedRemaining);
                    statement.setInt(9, recipeId);
                    statement.setString(10, stationPattern);
                    if (statement.executeUpdate() != 1) {
                        fail("Ricetta non trovata per questo tavolo", recipeId);
                        return;
                    }
                }
            } else {
                try (PreparedStatement nextId = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) + 1 FROM crafting_recipes");
                     ResultSet result = nextId.executeQuery()) {
                    if (!result.next()) {
                        fail("Impossibile assegnare un ID", recipeId);
                        return;
                    }
                    savedId = result.getInt(1);
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO crafting_recipes (id, product_name, reward_name, station_ids, ingredients, enabled, achievement, secret, limited, remaining) VALUES (?, ?, ?, ?, ?, '1', ?, ?, ?, ?)")) {
                    statement.setInt(1, savedId);
                    bindRecipe(statement, productName, rewardName, stationIds, ingredients.toString(), achievement, secret, limited, normalizedRemaining, 2);
                    statement.executeUpdate();
                }
            }
        } catch (Exception exception) {
            fail("Salvataggio fallito: classname già esistente o dati non validi", recipeId);
            return;
        }

        Emulator.getGameEnvironment().getCraftingManager().reload();
        this.client.sendResponse(new CraftingEditorResultComposer(true, "Ricetta salvata", savedId));
        CraftingEditorSupport.sendStateAndRecipes(this.client, objectId);
    }

    private void bindRecipe(PreparedStatement statement, String productName, String rewardName, String stationIds,
                            String ingredients, String achievement, boolean secret, boolean limited, int remaining) throws Exception {
        bindRecipe(statement, productName, rewardName, stationIds, ingredients, achievement, secret, limited, remaining, 1);
    }

    private void bindRecipe(PreparedStatement statement, String productName, String rewardName, String stationIds,
                            String ingredients, String achievement, boolean secret, boolean limited, int remaining,
                            int offset) throws Exception {
        statement.setString(offset, productName);
        statement.setString(offset + 1, rewardName);
        statement.setString(offset + 2, stationIds);
        statement.setString(offset + 3, ingredients);
        statement.setString(offset + 4, achievement == null ? "" : achievement.trim());
        statement.setString(offset + 5, secret ? "1" : "0");
        statement.setString(offset + 6, limited ? "1" : "0");
        statement.setInt(offset + 7, remaining);
    }

    private static boolean validName(String value) {
        return value != null && SAFE_NAME.matcher(value.trim()).matches();
    }

    private void fail(String message, int recipeId) {
        this.client.sendResponse(new CraftingEditorResultComposer(false, message, recipeId));
    }
}

package com.eu.habbo.habbohotel.crafting;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CraftingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingManager.class);

    private final Map<Item, CraftingAltar> altars;

    public CraftingManager() {
        this.altars = new HashMap<>();

        this.reload();
    }

    public void reload() {
        this.dispose();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM crafting_recipes WHERE enabled = ? ORDER BY id ASC")) {
            statement.setString(1, "1");
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    final int recipeId = set.getInt("id");
                    final String productName = set.getString("product_name");
                    final CraftingRecipe recipe;

                    try {
                        recipe = new CraftingRecipe(set);
                        if (recipe.getReward() == null) {
                            LOGGER.warn("Skipping crafting recipe {} ({}): reward item {} is not loaded", recipeId, productName, set.getString("reward_name"));
                            continue;
                        }

                        JsonArray ingredients = JsonParser.parseString(set.getString("ingredients")).getAsJsonArray();
                        if (ingredients.size() == 0) {
                            LOGGER.warn("Skipping crafting recipe {} ({}): it has no ingredients", recipeId, productName);
                            continue;
                        }

                        for (JsonElement element : ingredients) {
                            JsonObject ingredient = element.getAsJsonObject();
                            String classname = ingredient.get("classname").getAsString();
                            int amount = ingredient.get("amount").getAsInt();
                            Item ingredientItem = Emulator.getGameEnvironment().getItemManager().getItem(classname);

                            if (ingredientItem == null || amount <= 0) {
                                LOGGER.warn("Skipping crafting recipe {} ({}): unknown/invalid ingredient {} x{}", recipeId, productName, classname, amount);
                                continue;
                            }

                            recipe.addIngredient(ingredientItem, amount);
                        }

                        if (recipe.getIngredients().size() != ingredients.size()) {
                            continue;
                        }

                        JsonArray stations = JsonParser.parseString(set.getString("station_ids")).getAsJsonArray();
                        if (stations.size() == 0) {
                            LOGGER.warn("Crafting recipe {} ({}) has no mapped station", recipeId, productName);
                            continue;
                        }

                        List<Item> altarItems = new ArrayList<>(stations.size());
                        for (JsonElement station : stations) {
                            Item altarItem = Emulator.getGameEnvironment().getItemManager().getItem(station.getAsInt());
                            if (altarItem != null) {
                                altarItems.add(altarItem);
                            } else {
                                LOGGER.warn("Unknown crafting station {} for recipe {} ({})", station.getAsInt(), recipeId, productName);
                            }
                        }

                        if (altarItems.isEmpty()) {
                            continue;
                        }

                        for (Item altarItem : altarItems) {
                            CraftingAltar altar = this.altars.computeIfAbsent(altarItem, CraftingAltar::new);
                            altar.addRecipe(recipe);
                            for (Item ingredientItem : recipe.getIngredients().keySet()) {
                                altar.addIngredient(ingredientItem);
                            }
                        }
                    } catch (RuntimeException e) {
                        LOGGER.warn("Skipping malformed crafting recipe {} ({})", recipeId, productName, e);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int getRecipesWithItemCount(final Item item) {
        final int[] i = {0};

        synchronized (this.altars) {
            for (CraftingAltar altar : this.altars.values()) {
                if (altar.hasIngredient(item)) {
                    i[0]++;
                }
            }
        }

        return i[0];
    }

    public CraftingRecipe getRecipe(String recipeName) {
        CraftingRecipe recipe;
        for (CraftingAltar altar : this.altars.values()) {
            recipe = altar.getRecipe(recipeName);

            if (recipe != null) {
                return recipe;
            }
        }

        return null;
    }

    public CraftingAltar getAltar(Item item) {
        return this.altars.get(item);
    }

    public void dispose() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE crafting_recipes SET remaining = ? WHERE id = ? LIMIT 1")) {
            for (CraftingAltar altar : this.altars.values()) {
                for (CraftingRecipe recipe : altar.getRecipes()) {
                    if (recipe.isLimited()) {
                        statement.setInt(1, recipe.getRemaining());
                        statement.setInt(2, recipe.getId());

                        statement.addBatch();
                    }
                }
            }
            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.altars.clear();
    }
}

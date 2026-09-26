package com.eu.habbo.habbohotel.rooms;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Validates and canonicalizes the small per-part color payload stored with furniture. */
public final class WiredFurnitureCustomColor {
    static final int MAXIMUM_BYTES_PER_ITEM = 4_096;
    static final int MAXIMUM_BYTES_PER_ROOM = 512 * 1_024;
    static final int MAXIMUM_LAYERS = 26;

    private static final Gson GSON = new Gson();
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private WiredFurnitureCustomColor() {}

    /** Returns null for an empty/reset value; malformed or oversized values throw. */
    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BYTES_PER_ITEM) {
            throw new IllegalArgumentException("Furniture color configuration exceeds its size limit");
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(raw);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid furniture color JSON", exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Furniture color config must be an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        if (root.size() != 2 || !root.has("v") || !root.get("v").isJsonPrimitive()
                || !root.get("v").getAsJsonPrimitive().isNumber() || root.get("v").getAsDouble() != 1.0
                || !root.has("layers") || !root.get("layers").isJsonObject()) {
            throw new IllegalArgumentException("Unsupported furniture color config");
        }

        JsonObject inputLayers = root.getAsJsonObject("layers");
        if (inputLayers.size() > MAXIMUM_LAYERS) {
            throw new IllegalArgumentException("Too many furniture layers");
        }
        if (inputLayers.isEmpty()) {
            return null;
        }

        List<Map.Entry<String, JsonElement>> ordered = new ArrayList<>(inputLayers.entrySet());
        ordered.sort(Comparator.comparingInt(entry -> parseLayerId(entry.getKey())));
        JsonObject layers = new JsonObject();

        for (Map.Entry<String, JsonElement> entry : ordered) {
            int layerId = parseLayerId(entry.getKey());
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Furniture layer color must be an object");
            }
            JsonObject layer = entry.getValue().getAsJsonObject();
            String mode = string(layer, "mode");
            boolean hasIntensity = layer.has("intensity");
            boolean hasFill = layer.has("fill");
            if (hasIntensity != hasFill) {
                throw new IllegalArgumentException("Furniture color intensity and fill must be supplied together");
            }

            int intensity = hasIntensity ? integer(layer, "intensity") : 100;
            boolean fill = hasFill && bool(layer, "fill");
            if (intensity < 0 || intensity > 100) {
                throw new IllegalArgumentException("Furniture color intensity is out of range");
            }

            JsonObject normalized = new JsonObject();
            normalized.addProperty("mode", mode);

            if ("solid".equals(mode) && layer.size() == (hasIntensity ? 4 : 2)) {
                normalized.addProperty("color", color(layer, "color"));
                normalized.addProperty("intensity", intensity);
                normalized.addProperty("fill", fill);
            } else if ("gradient".equals(mode) && layer.size() == (hasIntensity ? 6 : 4)) {
                normalized.addProperty("start", color(layer, "start"));
                normalized.addProperty("end", color(layer, "end"));
                int angle = integer(layer, "angle");
                if (angle < 0 || angle > 359) {
                    throw new IllegalArgumentException("Furniture gradient angle is out of range");
                }
                normalized.addProperty("angle", angle);
                normalized.addProperty("intensity", intensity);
                normalized.addProperty("fill", fill);
            } else {
                throw new IllegalArgumentException("Invalid furniture layer color mode");
            }
            layers.add(Integer.toString(layerId), normalized);
        }

        JsonObject canonical = new JsonObject();
        canonical.addProperty("v", 1);
        canonical.add("layers", layers);
        String result = GSON.toJson(canonical);
        if (result.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BYTES_PER_ITEM) {
            throw new IllegalArgumentException("Furniture color configuration exceeds its size limit");
        }
        return result;
    }

    private static int parseLayerId(String key) {
        try {
            int value = Integer.parseInt(key);
            if (value < 0 || value >= MAXIMUM_LAYERS || !Integer.toString(value).equals(key)) {
                throw new IllegalArgumentException("Invalid furniture layer id");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid furniture layer id", exception);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing furniture color field: " + key);
        }
        return value.getAsString();
    }

    private static String color(JsonObject object, String key) {
        String value = string(object, key);
        if (!HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid furniture layer color");
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || value.getAsDouble() != Math.rint(value.getAsDouble())) {
            throw new IllegalArgumentException("Invalid furniture gradient angle");
        }
        return value.getAsInt();
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Invalid furniture color fill option");
        }
        return value.getAsBoolean();
    }
}

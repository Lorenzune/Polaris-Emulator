package com.eu.habbo.messages.incoming.furniturereport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.editor.FurniEditorRepository;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

final class FurnitureReportSupport {
    static final String PREFIX = "FURNITURE_REPORT_V1:";
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private FurnitureReportSupport() {}

    static Optional<JsonObject> readRoomFurniture(Room room, int roomId, int objectId) throws SQLException {
        if (room == null || room.getId() != roomId) return Optional.empty();

        HabboItem item = room.getHabboItem(objectId);
        if (item == null || item.getBaseItem() == null) return Optional.empty();

        FurniEditorRepository repository =
                new FurniEditorRepository(Emulator.getDatabase().getDataSource());
        Optional<Map<String, Object>> fullItem = repository.findFullItemBySprite(
                item.getBaseItem().getSpriteId(), item.getBaseItem().getName());
        if (fullItem.isEmpty()) {
            fullItem = repository.findFullItemBySprite(item.getBaseItem().getSpriteId(), "");
        }
        if (fullItem.isEmpty()) return Optional.empty();

        JsonObject data = GSON.toJsonTree(fullItem.get()).getAsJsonObject();
        data.addProperty("room_id", roomId);
        data.addProperty("object_id", objectId);
        data.addProperty("room_x", item.getX());
        data.addProperty("room_y", item.getY());
        data.addProperty("room_z", item.getZ());
        data.addProperty("room_rotation", item.getRotation());
        data.addProperty("owner_id", item.getUserId() > 0 ? item.getUserId() : room.getOwnerId());
        data.addProperty("owner_name", room.getOwnerName() == null ? "" : room.getOwnerName());
        return Optional.of(data);
    }

    static String buildMessage(JsonObject currentValues, JsonObject proposedValues, String reason) {
        JsonObject report = new JsonObject();
        report.addProperty("kind", "furniture_report");
        report.addProperty("version", 1);
        report.addProperty("reason", limit(reason, 500));
        report.add("currentValues", currentValues == null ? new JsonObject() : currentValues);
        report.add("proposedValues", proposedValues == null ? new JsonObject() : proposedValues);
        return PREFIX + GSON.toJson(report);
    }

    static JsonObject parseMessage(String message) {
        if (message == null || !message.startsWith(PREFIX)) return null;
        try {
            return JsonParser.parseString(message.substring(PREFIX.length())).getAsJsonObject();
        } catch (Exception exception) {
            return null;
        }
    }

    static String limit(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}

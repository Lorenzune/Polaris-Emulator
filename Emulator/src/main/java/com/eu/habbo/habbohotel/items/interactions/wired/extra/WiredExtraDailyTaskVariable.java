package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/** SWF type 8 fields; exposes today's local daily-task progress without duplicating its state. */
public final class WiredExtraDailyTaskVariable extends WiredExtraUserVariable {
    public static final int CODE = 115;
    private String name = "";
    private String taskCode = "";
    private final ConcurrentHashMap<Integer, CachedProgress> progress = new ConcurrentHashMap<>();

    public WiredExtraDailyTaskVariable(ResultSet set, Item item) throws SQLException {
        super(set, item);
    }

    public WiredExtraDailyTaskVariable(int id, int owner, Item item, String data, int stack, int sells) {
        super(id, owner, item, data, stack, sells);
    }

    @Override
    public String getVariableName() {
        return name;
    }

    @Override
    public boolean hasValue() {
        return true;
    }

    @Override
    public int getAvailability() {
        return AVAILABILITY_ROOM;
    }

    public String getTaskCode() {
        return taskCode;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient client) throws WiredSaveException {
        String[] fields = settings.getStringParam().split("\t", -1);
        if (fields.length != 2
                || settings.getIntParams().length != 0
                || fields[1].isBlank()
                || fields[1].length() > 100)
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        try {
            if (Integer.parseInt(fields[1]) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            throw new WiredSaveException("wiredfurni.params.variables.validation.invalid_variable");
        }
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        String nextName = WiredVariableNameValidator.normalizeForSave(fields[0]);
        WiredVariableNameValidator.validateDefinitionName(room, getId(), nextName);
        name = nextName;
        taskCode = fields[1];
        progress.clear();
        if (room != null) room.getUserVariableManager().requestBroadcast();
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new Data(name, taskCode));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        onPickUp();
        Data data = WiredExtraPayloadGuard.fromJson(set.getString("wired_data"), Data.class);
        if (data != null) {
            name = WiredVariableNameValidator.normalizeLegacy(data.variableName);
            taskCode = data.taskCode == null ? "" : data.taskCode;
        }
    }

    @Override
    public void onPickUp() {
        name = "";
        taskCode = "";
        if (progress != null) progress.clear();
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(getBaseItem().getSpriteId());
        message.appendInt(getId());
        message.appendString(name + "\t" + taskCode);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    public com.eu.habbo.habbohotel.dailytasks.DailyTaskManager.WiredProgress readProgress(int userId) {
        var cached = progress.get(userId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now) return cached.value;
        int id;
        try {
            id = Integer.parseInt(taskCode);
        } catch (NumberFormatException invalid) {
            return new com.eu.habbo.habbohotel.dailytasks.DailyTaskManager.WiredProgress(false, 0, 0, 0);
        }
        var value = Emulator.getGameEnvironment().getDailyTaskManager().getWiredProgress(userId, id);
        if (progress.size() > 1024) progress.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        progress.put(userId, new CachedProgress(now + 1000, value));
        return value;
    }

    public void invalidateProgress(int userId) {
        progress.remove(userId);
    }

    public com.eu.habbo.habbohotel.dailytasks.DailyTaskManager.WiredProgress cachedProgress(int userId) {
        var cached = progress.get(userId);
        return cached == null ? null : cached.value;
    }

    private record CachedProgress(
            long expiresAt, com.eu.habbo.habbohotel.dailytasks.DailyTaskManager.WiredProgress value) {}

    private record Data(String variableName, String taskCode) {}
}

package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoomLinker;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Transfers users through the normal teleport room-entry flow. The selected endpoint resolves its paired destination at execution time. */
public class WiredEffectForwardUserToRoom extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.TELEPORT_TO_ROOM;

    private List<Integer> linkerIds = new ArrayList<>();
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectForwardUserToRoom(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectForwardUserToRoom(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(1);
        message.appendInt(this.linkerIds.size());
        for (int id : this.linkerIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] selected = settings.getFurniIds();
        if (selected == null || selected.length != 1 || selected[0] <= 0) return false;
        if (gameClient != null) {
            Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
            if (room == null || !(room.getHabboItem(selected[0]) instanceof InteractionRoomLinker)) return false;
        }
        this.linkerIds = new ArrayList<>(List.of(selected[0]));

        int[] params = settings.getIntParams();
        this.userSource = (params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_TRIGGER;

        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || Emulator.getGameEnvironment() == null || linkerIds.size() != 1) return;
        if (!(room.getHabboItem(linkerIds.getFirst()) instanceof InteractionRoomLinker source)) return;
        var environment = Emulator.getGameEnvironment();
        int[] pair = environment.getItemManager().getTargetTeleportRoomId(source);
        if (pair == null || pair.length < 2 || pair[0] <= 0 || pair[1] <= 0) return;
        int targetRoomId = pair[0];
        RoomManager roomManager = environment.getRoomManager();
        Room targetRoom = targetRoomId == room.getId() ? room : roomManager.loadRoom(targetRoomId, true);
        if (targetRoom == null || !targetRoom.isLoaded()) return;
        if (!(targetRoom.getHabboItem(pair[1]) instanceof InteractionRoomLinker destination)) return;
        if (targetRoom.getLayout() == null) return;
        var tile = targetRoom.getLayout().getTile(destination.getX(), destination.getY());
        if (tile == null) return;
        var transferred = new java.util.HashSet<Integer>();
        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null || habbo.getClient() == null || !transferred.add(habbo.getHabboInfo().getId())) continue;
            synchronized (habbo) {
                Room currentRoom = habbo.getHabboInfo().getCurrentRoom();
                // A previous effect or an in-flight transfer already owns this user's room change.
                if (currentRoom != room || habbo.getHabboInfo().getLoadingRoom() != 0 || (habbo.getRoomUnit() != null && habbo.getRoomUnit().isTeleporting)) continue;
                if (currentRoom == targetRoom) {
                    currentRoom.teleportHabboToItem(habbo, destination);
                    continue;
                }
                RoomLinkerTransfer.start(roomManager, habbo, currentRoom, targetRoom, tile,
                        recovery -> Emulator.getThreading().run(recovery, 15000));
            }
        }
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.linkerIds, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.linkerIds = data.linkerIds == null ? new ArrayList<>() : new ArrayList<>(data.linkerIds.stream().filter(id -> id != null && id > 0).distinct().limit(1).toList());
            this.setDelay(data.delay);
            this.userSource = data.userSource;
        } else {
            this.linkerIds.clear();
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.setDelay(0);
        }
    }

    @Override
    public void onPickUp() {
        this.linkerIds.clear();
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        List<Integer> linkerIds;
        int delay;
        int userSource;

        public JsonData(List<Integer> linkerIds, int delay, int userSource) {
            this.linkerIds = linkerIds;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}

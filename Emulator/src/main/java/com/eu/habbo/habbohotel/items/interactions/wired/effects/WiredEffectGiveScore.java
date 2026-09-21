package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredHighscore;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WiredEffectGiveScore extends InteractionWiredEffect {
    private static final int OPERATION_ADD = 0;
    private static final int OPERATION_REMOVE = 1;
    public static final WiredEffectType type = WiredEffectType.GIVE_SCORE;

    private int score;
    private int operation = OPERATION_ADD;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int directMode = 0;
    private final List<HabboItem> items = new ArrayList<>();

    public WiredEffectGiveScore(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveScore(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        int applied = this.getAppliedAmount();

        if (this.directMode == 1) {
            Set<InteractionWiredHighscore> targetHighscores = new LinkedHashSet<>();
            for (HabboItem item : this.items) {
                if (item instanceof InteractionWiredHighscore highscore && item.getRoomId() == room.getId() && room.getHabboItem(item.getId()) != null) {
                    targetHighscores.add(highscore);
                }
            }
            if (targetHighscores.isEmpty()) {
                for (HabboItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class)) {
                    if (item instanceof InteractionWiredHighscore highscore) {
                        targetHighscores.add(highscore);
                    }
                }
            }

            for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
                Habbo habbo = room.getHabbo(unit);
                if (habbo == null) continue;

                for (InteractionWiredHighscore highscore : targetHighscores) {
                    Emulator.getGameEnvironment().getItemManager().getHighscoreManager().addOrUpdateUserScore(
                            highscore.getId(),
                            habbo.getHabboInfo().getId(),
                            applied,
                            highscore.clearType
                    );
                    highscore.reloadData();
                    room.updateItem(highscore);
                }
            }
            return;
        }

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo == null || habbo.getHabboInfo().getCurrentGame() == null) continue;

            Game game = room.getGame(habbo.getHabboInfo().getCurrentGame());

            if (game == null)
                continue;

            if (habbo.getHabboInfo().getGamePlayer() != null) {
                habbo.getHabboInfo().getGamePlayer().addScore(applied, true);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<Integer> itemIds = new ArrayList<>();
        for (HabboItem item : this.items) {
            if (item != null) itemIds.add(item.getId());
        }
        return WiredManager.getGson().toJson(new JsonData(this.score, this.operation, this.getDelay(), this.userSource, this.directMode, itemIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.score = data.score;
            this.operation = this.normalizeOperation(data.operation);
            this.setDelay(data.delay);
            this.userSource = data.userSource;
            this.directMode = data.directMode;
            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    if (id == null) continue;
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
        }
        else {
            String[] data = wiredData.split(";");

            if (data.length >= 3) {
                this.score = Integer.parseInt(data[0]);
                this.operation = OPERATION_ADD;
                this.setDelay(Integer.parseInt(data[2]));
            }

            this.needsUpdate(true);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.directMode = 0;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.score = 0;
        this.operation = OPERATION_ADD;
        this.setDelay(0);
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.directMode = 0;
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectGiveScore.type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        itemsSnapshot.removeIf(item -> item == null || item.getRoomId() != room.getId() || room.getHabboItem(item.getId()) == null);
        this.items.clear();
        this.items.addAll(itemsSnapshot);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.score);
        message.appendInt(this.operation);
        message.appendInt(this.userSource);
        message.appendInt(this.directMode);
        message.appendInt(0);
        message.appendInt(this.getType().code);
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
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 3) throw new WiredSaveException("Invalid data");

        int score = settings.getIntParams()[0];

        if(score < 1 || score > 1000)
            throw new WiredSaveException("Score is invalid");

        int operation = this.normalizeOperation(settings.getIntParams()[1]);

        this.userSource = settings.getIntParams()[2];
        if (settings.getIntParams().length > 3) {
            this.directMode = (settings.getIntParams()[3] == 1) ? 1 : 0;
        } else {
            this.directMode = 0;
        }

        int itemsCount = settings.getFurniIds().length;
        if (itemsCount > WiredManager.MAXIMUM_FURNI_SELECTION) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newItems = new ArrayList<>();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room != null && this.directMode == 1) {
            for (int i = 0; i < itemsCount; i++) {
                int itemId = settings.getFurniIds()[i];
                HabboItem it = room.getHabboItem(itemId);
                if (it != null) {
                    newItems.add(it);
                }
            }
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        if (this.directMode == 1) {
            this.items.addAll(newItems);
        }
        this.score = score;
        this.operation = operation;
        this.setDelay(delay);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    private int normalizeOperation(int value) {
        return (value == OPERATION_REMOVE) ? OPERATION_REMOVE : OPERATION_ADD;
    }

    private int getAppliedAmount() {
        return (this.operation == OPERATION_REMOVE) ? -this.score : this.score;
    }

    static class JsonData {
        int score;
        int operation;
        int delay;
        int userSource;
        int directMode;
        List<Integer> itemIds;

        public JsonData(int score, int operation, int delay, int userSource, int directMode, List<Integer> itemIds) {
            this.score = score;
            this.operation = operation;
            this.delay = delay;
            this.userSource = userSource;
            this.directMode = directMode;
            this.itemIds = itemIds;
        }
    }
}

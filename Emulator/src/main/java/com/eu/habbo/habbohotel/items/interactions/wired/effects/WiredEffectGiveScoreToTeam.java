package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredHighscore;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WiredEffectGiveScoreToTeam extends InteractionWiredEffect {
    private static final int OPERATION_ADD = 0;
    private static final int OPERATION_REMOVE = 1;
    public static final WiredEffectType type = WiredEffectType.GIVE_SCORE_TEAM;

    private int points;
    private int operation = OPERATION_ADD;
    private GameTeamColors teamColor = GameTeamColors.RED;
    private int directMode = 0;
    private final List<HabboItem> items = new ArrayList<>();

    public WiredEffectGiveScoreToTeam(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public WiredEffectGiveScoreToTeam(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (this.directMode == 1) {
            List<Integer> userIds = new ArrayList<>();
            for (Game game : room.getGames()) {
                GameTeam team = game.getTeam(this.teamColor);
                if (team != null) {
                    for (GamePlayer player : team.getMembers()) {
                        if (player.getHabbo() != null
                                && !userIds.contains(
                                        player.getHabbo().getHabboInfo().getId())) {
                            userIds.add(player.getHabbo().getHabboInfo().getId());
                        }
                    }
                }
            }
            for (Habbo habbo : room.getHabbos()) {
                if (habbo.getHabboInfo().getGamePlayer() != null
                        && habbo.getHabboInfo().getGamePlayer().getTeamColor() == this.teamColor) {
                    int uid = habbo.getHabboInfo().getId();
                    if (!userIds.contains(uid)) {
                        userIds.add(uid);
                    }
                }
            }

            int applied = (this.operation == OPERATION_REMOVE) ? -this.points : this.points;
            Set<InteractionWiredHighscore> targetHighscores = new LinkedHashSet<>();
            for (HabboItem item : this.items) {
                if (item instanceof InteractionWiredHighscore highscore
                        && item.getRoomId() == room.getId()
                        && room.getHabboItem(item.getId()) != null) {
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

            for (InteractionWiredHighscore highscore : targetHighscores) {
                if (!userIds.isEmpty()) {
                    Emulator.getGameEnvironment()
                            .getItemManager()
                            .getHighscoreManager()
                            .addOrUpdateTeamScore(highscore.getId(), userIds, applied, highscore.clearType);
                }
                highscore.reloadData();
                room.updateItem(highscore);
            }
            return;
        }

        for (Game game : room.getGames()) {
            if (game != null && game.getState() == GameState.RUNNING) {
                GameTeam team = game.getTeam(this.teamColor);

                if (team != null) {
                    team.addTeamScore(this.getAppliedAmount(team));
                }
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
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.points, this.operation, this.teamColor, this.getDelay(), this.directMode, itemIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.points = data.score;
            this.operation = this.normalizeOperation(data.operation);
            this.teamColor = data.team;
            this.setDelay(data.delay);
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
        } else {
            String[] data = set.getString("wired_data").split(";");

            if (data.length >= 4) {
                this.points = Integer.parseInt(data[0]);
                this.operation = OPERATION_ADD;
                this.teamColor = GameTeamColors.fromType(Integer.parseInt(data[2]));
                this.setDelay(Integer.parseInt(data[3]));
            }

            this.needsUpdate(true);
            this.directMode = 0;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.points = 0;
        this.operation = OPERATION_ADD;
        this.teamColor = GameTeamColors.RED;
        this.setDelay(0);
        this.directMode = 0;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        itemsSnapshot.removeIf(
                item -> item == null || item.getRoomId() != room.getId() || room.getHabboItem(item.getId()) == null);
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
        message.appendInt(this.points);
        message.appendInt(this.operation);
        message.appendInt(this.teamColor.type);
        message.appendInt(this.directMode);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (settings.getIntParams().length < 3) throw new WiredSaveException("Invalid data");

        int points = settings.getIntParams()[0];

        if (points < 1 || points > 1000) throw new WiredSaveException("Points is invalid");

        int operation = this.normalizeOperation(settings.getIntParams()[1]);

        int team = settings.getIntParams()[2];

        if (team < 1 || team > 4) throw new WiredSaveException("Team is invalid");

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

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        if (this.directMode == 1) {
            this.items.addAll(newItems);
        }
        this.points = points;
        this.operation = operation;
        this.teamColor = GameTeamColors.fromType(team);
        this.setDelay(delay);

        return true;
    }

    private int normalizeOperation(int value) {
        return (value == OPERATION_REMOVE) ? OPERATION_REMOVE : OPERATION_ADD;
    }

    private int getAppliedAmount(GameTeam team) {
        if (this.operation != OPERATION_REMOVE) {
            return this.points;
        }

        return -Math.min(this.points, team.getTeamScore());
    }

    static class JsonData {
        int score;
        int operation;
        GameTeamColors team;
        int delay;
        int directMode;
        List<Integer> itemIds;

        public JsonData(
                int score, int operation, GameTeamColors team, int delay, int directMode, List<Integer> itemIds) {
            this.score = score;
            this.operation = operation;
            this.team = team;
            this.delay = delay;
            this.directMode = directMode;
            this.itemIds = itemIds;
        }
    }
}

package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreClearType;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreRow;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreScoreType;
import com.eu.habbo.messages.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InteractionWiredHighscore extends HabboItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionWiredHighscore.class);

    public WiredHighscoreScoreType scoreType;
    public WiredHighscoreClearType clearType;

    private List<WiredHighscoreRow> data;
    private String scoreBoardName = "";
    private int scoreBoardX = -1;
    private int scoreBoardY = -1;
    private String scoreBoardColor = "";
    private int scoreBoardOpacity = -1;
    private String scoreBoardPodium = "";

    public InteractionWiredHighscore(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        this.scoreType = WiredHighscoreScoreType.CLASSIC;
        this.clearType = WiredHighscoreClearType.ALLTIME;

        try {
            String name = this.getBaseItem().getName().split("_")[1].toUpperCase().split("\\*")[0];
            int ctype = Integer.parseInt(this.getBaseItem().getName().split("\\*")[1]) - 1;
            this.scoreType = WiredHighscoreScoreType.valueOf(name);
            this.clearType = WiredHighscoreClearType.values()[ctype];
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        this.parseScoreBoardData(this.getExtradata());
        this.reloadData();
    }

    public InteractionWiredHighscore(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.scoreType = WiredHighscoreScoreType.CLASSIC;
        this.clearType = WiredHighscoreClearType.ALLTIME;

        try {
            String name = this.getBaseItem().getName().split("_")[1].toUpperCase().split("\\*")[0];
            int ctype = Integer.parseInt(this.getBaseItem().getName().split("\\*")[1]) - 1;
            this.scoreType = WiredHighscoreScoreType.valueOf(name);
            this.clearType = WiredHighscoreClearType.values()[ctype];
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        this.parseScoreBoardData(extradata);
        this.reloadData();
    }

    private void parseScoreBoardData(String data) {
        if (data == null || data.isEmpty()) {
            this.scoreBoardName = "";
            this.scoreBoardX = -1;
            this.scoreBoardY = -1;
            this.scoreBoardColor = "";
            this.scoreBoardOpacity = -1;
            this.scoreBoardPodium = "";
            this.setExtradata("0");
            return;
        }

        if (data.contains("\t")) {
            String[] parts = data.split("\t", -1);
            this.setExtradata(parts[0]);
            this.scoreBoardName = parts.length > 1 ? parts[1] : "";
            if (parts.length > 2 && !parts[2].isEmpty() && parts[2].contains(":")) {
                try {
                    String[] posParts = parts[2].split(":");
                    this.scoreBoardX = Integer.parseInt(posParts[0]);
                    this.scoreBoardY = Integer.parseInt(posParts[1]);
                } catch (Exception ignored) {
                    this.scoreBoardX = -1;
                    this.scoreBoardY = -1;
                }
            } else {
                this.scoreBoardX = -1;
                this.scoreBoardY = -1;
            }
            this.scoreBoardColor = parts.length > 3 ? parts[3] : "";
            if (parts.length > 4 && !parts[4].isEmpty()) {
                try {
                    this.scoreBoardOpacity = Integer.parseInt(parts[4]);
                } catch (Exception ignored) {
                    this.scoreBoardOpacity = -1;
                }
            } else {
                this.scoreBoardOpacity = -1;
            }
            this.scoreBoardPodium = parts.length > 5 ? parts[5] : "";
        } else {
            this.scoreBoardName = "";
            this.scoreBoardX = -1;
            this.scoreBoardY = -1;
            this.scoreBoardColor = "";
            this.scoreBoardOpacity = -1;
            this.scoreBoardPodium = "";
        }
    }

    public String getScoreBoardName() {
        return this.scoreBoardName;
    }

    public void setScoreBoardName(String scoreBoardName) {
        this.scoreBoardName = scoreBoardName != null ? scoreBoardName.replace("\t", " ").trim() : "";
    }

    public int getScoreBoardX() {
        return this.scoreBoardX;
    }

    public int getScoreBoardY() {
        return this.scoreBoardY;
    }

    public void setScoreBoardPos(int x, int y) {
        this.scoreBoardX = x;
        this.scoreBoardY = y;
    }

    public String getScoreBoardColor() {
        return this.scoreBoardColor;
    }

    public void setScoreBoardColor(String scoreBoardColor) {
        this.scoreBoardColor = scoreBoardColor != null ? scoreBoardColor.replace("\t", "").trim() : "";
    }

    public int getScoreBoardOpacity() {
        return this.scoreBoardOpacity;
    }

    public void setScoreBoardOpacity(int scoreBoardOpacity) {
        this.scoreBoardOpacity = scoreBoardOpacity;
    }

    public String getScoreBoardPodium() {
        return this.scoreBoardPodium;
    }

    public void setScoreBoardPodium(String scoreBoardPodium) {
        this.scoreBoardPodium = scoreBoardPodium != null ? scoreBoardPodium.replace("\t", "").trim() : "";
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (room == null || !((client != null && room.hasRights(client.getHabbo())) || (objects.length >= 2 && objects[1] instanceof WiredEffectType)))
            return;

        if (this.getExtradata() == null || this.getExtradata().isEmpty() || this.getExtradata().length() == 0) {
            this.setExtradata("0");
        }

        try {
            int state = Integer.parseInt(this.getExtradata());
            this.setExtradata(Math.abs(state - 1) + "");
            this.needsUpdate(true);
            room.updateItem(this);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        if(client != null && !(objects.length >= 2 && objects[1] instanceof WiredEffectType)) {
            WiredManager.triggerFurniStateChanged(room, client.getHabbo().getRoomUnit(), this);
        }
    }


    @Override
    public String getDatabaseExtraData() {
        String state = (this.getExtradata() == null || this.getExtradata().isEmpty()) ? "0" : this.getExtradata();
        String name = (this.scoreBoardName == null) ? "" : this.scoreBoardName;
        String pos = (this.scoreBoardX >= 0 && this.scoreBoardY >= 0) ? (this.scoreBoardX + ":" + this.scoreBoardY) : "";
        String color = (this.scoreBoardColor == null) ? "" : this.scoreBoardColor;
        String opacity = (this.scoreBoardOpacity >= 0) ? String.valueOf(this.scoreBoardOpacity) : "";
        String podium = (this.scoreBoardPodium == null) ? "" : this.scoreBoardPodium;

        if (pos.isEmpty() && name.isEmpty() && color.isEmpty() && opacity.isEmpty() && podium.isEmpty()) {
            return state;
        }

        StringBuilder sb = new StringBuilder(state);
        sb.append("\t").append(name);
        sb.append("\t").append(pos);
        sb.append("\t").append(color);
        sb.append("\t").append(opacity);
        sb.append("\t").append(podium);

        return sb.toString().replaceAll("(\t)+$", "");
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(6);
        String combined = this.getDatabaseExtraData();
        serverMessage.appendString(combined);
        serverMessage.appendInt(this.scoreType.type);
        serverMessage.appendInt(this.clearType.type);

        if (this.data != null) {
            int size = this.data.size();
            if(size > 50) {
                size = 50;
            }
            serverMessage.appendInt(size);

            int count = 0;
            for (WiredHighscoreRow row : this.data) {
                if(count < 50) {
                    serverMessage.appendInt(row.getValue());

                    serverMessage.appendInt(row.getUsers().size());
                    for (String username : row.getUsers()) {
                        serverMessage.appendString(username);
                    }
                }
                count++;
            }
        } else {
            serverMessage.appendInt(0);
        }

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onPlace(Room room) {
        this.reloadData();
        super.onPlace(room);
    }

    @Override
    public void onPickUp(Room room) {
        if (this.data != null) {
            this.data.clear();
        }
    }

    public void reloadData() {
        this.data = Emulator.getGameEnvironment().getItemManager().getHighscoreManager().getHighscoreRowsForItem(this.getId(), this.clearType, this.scoreType);
    }
}
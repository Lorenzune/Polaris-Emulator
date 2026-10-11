package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Documentation furniture: its numeric state remains available to ordinary WIRED effects. */
public class InteractionWiredLabel extends InteractionRemoteSwitchControl {
    public static final int MAX_TEXT_LENGTH = 2000;
    public static final int MAX_TEXT_LINES = 32;
    public static final int DEFAULT_CARD_COLOR = 0x5865F2;
    private String labelText = "";
    private int cardColor = DEFAULT_CARD_COLOR;

    public InteractionWiredLabel(ResultSet set, Item item) throws SQLException {
        super(set, item);
        loadDocumentation();
    }

    public InteractionWiredLabel(int id, int userId, Item item, String data, int stack, int sells) {
        super(id, userId, item, data, stack, sells);
        loadDocumentation();
    }

    private void loadDocumentation() {
        String data = getExtradata();
        if (!data.startsWith("{")) return;
        try {
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            String text = json.get("text").getAsString();
            labelText = validText(text) ? text : "";
            if (json.has("color")) {
                int color = json.get("color").getAsInt();
                if (validColor(color)) cardColor = color;
            }
            super.setExtradata(
                    Integer.toString(Integer.parseInt(json.get("state").getAsString())));
            consumeWiredStateChange();
        } catch (RuntimeException exception) {
            labelText = "";
            super.setExtradata("0");
            consumeWiredStateChange();
        }
    }

    public static boolean validText(String text) {
        return text != null
                && text.length() <= MAX_TEXT_LENGTH
                && text.lines().limit(MAX_TEXT_LINES + 1L).count() <= MAX_TEXT_LINES
                && text.chars().noneMatch(c -> (c < 32 && c != '\n' && c != '\r' && c != '\t') || c == 127);
    }

    public static boolean validColor(int color) {
        return color >= 0 && color <= 0xFFFFFF;
    }

    public synchronized int getCardColor() {
        return cardColor;
    }

    public synchronized void setCardColor(int color) {
        if (!validColor(color)) throw new IllegalArgumentException("Invalid label color");
        cardColor = color;
    }

    public synchronized String getLabelText() {
        return labelText;
    }

    @Override
    public synchronized String getExtradata() {
        return super.getExtradata();
    }

    @Override
    public synchronized void setExtradata(String state) {
        super.setExtradata(state);
    }

    public synchronized void setLabelText(String text) {
        if (!validText(text)) throw new IllegalArgumentException("Invalid WIRED documentation");
        labelText = text;
    }

    @Override
    public synchronized String getDatabaseExtraData() {
        JsonObject json = new JsonObject();
        json.addProperty("state", getExtradata());
        json.addProperty("text", labelText);
        json.addProperty("color", cardColor);
        return json.toString();
    }

    @Override
    public synchronized void serializeExtradata(ServerMessage message) {
        message.appendInt(1 + (isLimited() ? 256 : 0));
        message.appendInt(3);
        message.appendString("state");
        message.appendString(getExtradata());
        message.appendString("text");
        message.appendString(labelText);
        message.appendString("color");
        message.appendString(Integer.toString(cardColor));
        if (isLimited()) {
            message.appendInt(getLimitedSells());
            message.appendInt(getLimitedStack());
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        // User "Use" and the inline checkbox set visibility. WIRED effects keep their original path.
        if (client != null && (objects == null || objects.length < 2)) {
            if (room == null
                    || !room.canModifyWired(client.getHabbo(), this)
                    || objects == null
                    || objects.length == 0
                    || !(objects[0] instanceof Integer)) return;
            int state = (Integer) objects[0];
            if ((state != 0 && state != 1) || getExtradata().equals(Integer.toString(state))) return;
            synchronized (this) {
                setExtradata(Integer.toString(state));
                needsUpdate(true);
                room.updateItemState(this);
            }
            com.eu.habbo.WiredPlatform.threading().run(this);
            return;
        }
        super.onClick(client, room, objects);
    }
}

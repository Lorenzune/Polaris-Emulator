package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionCustomValues;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoomAds;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredHighscore;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import java.util.HashMap;
import java.util.Map;

public class AdvertisingSaveEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        if (!room.hasRights(this.client.getHabbo())) return;

        int itemId = this.packet.readInt();
        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        HabboItem item = room.getHabboItem(itemId);
        if (item == null) return;

        if (item instanceof InteractionWiredHighscore) {
            InteractionWiredHighscore highscore = (InteractionWiredHighscore) item;
            int count = this.packet.readInt();
            if (!RoomItemInputGuard.isValidCustomValueCount(count)) return;

            String newName = null;
            String newPos = null;
            String newColor = null;
            Integer newOpacity = null;
            String newPodium = null;
            for (int i = 0; i < count / 2; i++) {
                String key = RoomItemInputGuard.trimToMax(
                        this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_KEY_LENGTH);
                String value = RoomItemInputGuard.trimToMax(
                        this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_VALUE_LENGTH);

                if (key.equalsIgnoreCase("name") || key.equalsIgnoreCase("title") || key.equalsIgnoreCase("caption")) {
                    newName = value;
                } else if (key.equalsIgnoreCase("pos") || key.equalsIgnoreCase("position")) {
                    newPos = value;
                } else if (key.equalsIgnoreCase("color")
                        || key.equalsIgnoreCase("bgcolor")
                        || key.equalsIgnoreCase("background")) {
                    newColor = value;
                } else if (key.equalsIgnoreCase("opacity") || key.equalsIgnoreCase("alpha")) {
                    try {
                        newOpacity = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                } else if (key.equalsIgnoreCase("podium")
                        || key.equalsIgnoreCase("podio")
                        || key.equalsIgnoreCase("podium_style")) {
                    newPodium = value;
                }
            }

            boolean updated = false;
            if (newName != null) {
                highscore.setScoreBoardName(newName);
                updated = true;
            }
            if (newPos != null && newPos.contains(":")) {
                try {
                    String[] coords = newPos.split(":");
                    highscore.setScoreBoardPos(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]));
                    updated = true;
                } catch (Exception ignored) {
                }
            }
            if (newColor != null) {
                highscore.setScoreBoardColor(newColor);
                updated = true;
            }
            if (newOpacity != null) {
                highscore.setScoreBoardOpacity(newOpacity);
                updated = true;
            }
            if (newPodium != null) {
                highscore.setScoreBoardPodium(newPodium);
                updated = true;
            }

            if (updated) {
                highscore.needsUpdate(true);
                Emulator.getThreading().run(highscore);
                room.updateItem(highscore);
            }
            return;
        }

        if (item instanceof InteractionRoomAds && !this.client.getHabbo().hasPermission("acc_ads_background")) {
            this.client.getHabbo().alert(Emulator.getTexts().getValue("hotel.error.roomads.nopermission"));
            return;
        }
        if (item instanceof InteractionCustomValues) {
            Map<String, String> oldValues = new HashMap<>(((InteractionCustomValues) item).values);
            int count = this.packet.readInt();
            if (!RoomItemInputGuard.isValidCustomValueCount(count)) return;

            for (int i = 0; i < count / 2; i++) {
                String key = RoomItemInputGuard.trimToMax(
                        this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_KEY_LENGTH);
                String value = RoomItemInputGuard.trimToMax(
                        this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_VALUE_LENGTH);

                if (key.isEmpty()) continue;

                if (!Emulator.getConfig().getBoolean("camera.use.https")) {
                    value = value.replace("https://", "http://");
                }

                ((InteractionCustomValues) item).values.put(key, value);
            }

            item.setExtradata(((InteractionCustomValues) item).toExtraData());
            item.needsUpdate(true);
            Emulator.getThreading().run(item);
            room.updateItem(item);
            ((InteractionCustomValues) item).onCustomValuesSaved(room, this.client, oldValues);
        }
    }
}

package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTagsComposer;

import java.util.ArrayList;
import java.util.List;

public class SaveUserTagsEvent extends MessageHandler {
    private static final int MAX_TAGS = 5;
    private static final int MAX_TOTAL_CHARS = 38;

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || habbo.getHabboStats() == null) return;

        int count = this.packet.readInt();
        if (count < 0 || count > 10) count = 0;

        List<String> validTags = new ArrayList<>();
        int totalChars = 0;

        for (int i = 0; i < count; i++) {
            if (this.packet.bytesAvailable() <= 0) break;
            String tag = this.packet.readString();
            if (tag == null) continue;
            tag = tag.replace("#", "").replaceAll("[\\p{Cntrl}]", "").replaceAll("\\s+", "").trim();
            if (tag.isEmpty()) continue;

            String filteredTag = Emulator.getGameEnvironment().getWordFilter().filter(tag, habbo);
            if (!tag.equals(filteredTag)) continue;

            boolean duplicate = false;
            for (String existing : validTags) {
                if (existing.equalsIgnoreCase(tag)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;

            if (validTags.size() >= MAX_TAGS) continue;
            if (totalChars + tag.length() + 1 > MAX_TOTAL_CHARS) continue;

            validTags.add(tag);
            totalChars += tag.length() + 1;
        }

        habbo.getHabboStats().tags = validTags.toArray(new String[0]);
        habbo.getHabboStats().persistTags();

        if (habbo.getHabboInfo().getCurrentRoom() != null && habbo.getRoomUnit() != null) {
            habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserTagsComposer(habbo).compose());
        } else {
            this.client.sendResponse(new RoomUserTagsComposer(habbo));
        }
    }
}

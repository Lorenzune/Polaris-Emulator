package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import java.util.ArrayList;
import java.util.List;

public class UpdateNavigatorRoomOrderEvent extends MessageHandler {
    private static final int MAX_ROOM_COUNT = 2000;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        String scope = this.packet.readString();
        int categoryId = this.packet.readInt();
        int count = this.packet.readInt();
        if (count < 0 || count > MAX_ROOM_COUNT) return;

        List<Integer> roomIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) roomIds.add(this.packet.readInt());

        Emulator.getGameEnvironment().getRoomManager().saveNavigatorRoomOrder(habbo, scope, categoryId, roomIds);
    }
}

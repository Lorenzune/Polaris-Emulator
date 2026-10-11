package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredContractTransactions;
import com.eu.habbo.messages.incoming.MessageHandler;

public final class WiredContractTransactionEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 300;
    }

    @Override
    public void handle() {
        String token = packet.readString();
        int action = packet.readInt(),
                choice = packet.readInt(),
                donation = packet.readInt(),
                currency = packet.readInt();
        WiredContractTransactions.respond(client.getHabbo(), token, action, choice, donation, currency);
    }
}

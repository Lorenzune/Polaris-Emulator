package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomVoiceSignalComposer extends MessageComposer {
    private final int senderUserId;
    private final String signalData;

    public RoomVoiceSignalComposer(int senderUserId, String signalData) {
        this.senderUserId = senderUserId;
        this.signalData = signalData;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomVoiceSignalComposer);
        this.response.appendInt(this.senderUserId);
        this.response.appendString(this.signalData != null ? this.signalData : "");
        return this.response;
    }
}

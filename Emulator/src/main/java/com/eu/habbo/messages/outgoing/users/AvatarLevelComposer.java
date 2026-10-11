package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.habbohotel.users.levels.AvatarLevelSnapshot;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AvatarLevelComposer extends MessageComposer {
    private final AvatarLevelSnapshot snapshot;

    public AvatarLevelComposer(AvatarLevelSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.AvatarLevelComposer);
        response.appendInt(snapshot.userId());
        response.appendInt(snapshot.level());
        response.appendInt(snapshot.score().total());
        response.appendInt(snapshot.highestScore());
        int[] raw = snapshot.score().raw();
        int[] counted = snapshot.score().counted();
        response.appendInt(raw[0]);
        response.appendInt(raw[1]);
        response.appendInt(raw[2]);
        response.appendInt(raw[3]);
        response.appendInt(counted[0]);
        response.appendInt(counted[1]);
        response.appendInt(counted[2]);
        response.appendInt(counted[3]);
        response.appendInt(snapshot.nextLevel());
        response.appendInt(snapshot.nextScore());
        response.appendInt(snapshot.nextReward());
        return response;
    }
}

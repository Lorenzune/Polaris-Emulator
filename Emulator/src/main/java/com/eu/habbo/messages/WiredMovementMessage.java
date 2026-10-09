package com.eu.habbo.messages;

import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer.MovementData;
import io.netty.buffer.ByteBuf;
import java.util.List;

/** Rechecks reset epochs in the channel's encoder, immediately before publishing the frame. */
public final class WiredMovementMessage extends ServerMessage {
    private final List<MovementData> movements;

    public WiredMovementMessage(List<MovementData> movements) {
        super(Outgoing.WiredMovementsComposer);
        this.movements = List.copyOf(movements);
        appendMovements(this, this.movements);
    }

    @Override
    public ByteBuf get() {
        boolean invalidated = false;
        for (MovementData movement : this.movements) {
            if (!movement.isCurrent()) { invalidated = true; break; }
        }
        if (!invalidated) return super.get();

        // Never rewrite a prepared/shared packet. Each recipient gets its own filtered copy.
        List<MovementData> current = this.movements.stream().filter(MovementData::isCurrent).toList();
        ServerMessage filtered = new ServerMessage(Outgoing.WiredMovementsComposer);
        appendMovements(filtered, current);
        return filtered.get();
    }

    private static void appendMovements(ServerMessage message, List<MovementData> movements) {
        message.appendInt(movements.size());
        for (MovementData movement : movements) {
            message.appendInt(movement.getType());
            movement.append(message);
        }
    }
}

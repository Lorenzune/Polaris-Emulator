package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer.MovementData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-owned animation list with cross-thread invalidation of pending furniture moves. */
final class WiredMovementCollection extends ArrayList<MovementData> {
    // Only unpublished moves are registered. Furniture IDs are unique across rooms.
    private static final ConcurrentHashMap<Integer, Set<Ticket>> PENDING = new ConcurrentHashMap<>();
    private final Map<MovementData, Ticket> tickets = new IdentityHashMap<>();

    private static final class Ticket {
        final int itemId;
        volatile boolean invalidated;

        Ticket(int itemId) {
            this.itemId = itemId;
        }
    }

    static void addFurniture(List<MovementData> target, int itemId, MovementData movement) {
        if (target instanceof WiredMovementCollection collection) {
            Ticket ticket = new Ticket(itemId);
            PENDING.compute(itemId, (id, pending) -> {
                if (pending == null) pending = new HashSet<>();
                pending.add(ticket);
                return pending;
            });
            collection.tickets.put(movement, ticket);
        }
        target.add(movement);
    }

    static void invalidateFurniture(int itemId) {
        PENDING.computeIfPresent(itemId, (id, pending) -> {
            // Never mutate another thread's animation list. Its owner prunes revoked tickets.
            for (Ticket ticket : pending) ticket.invalidated = true;
            return null;
        });
    }

    void discardInvalidated() {
        removeIf(movement -> {
            Ticket ticket = this.tickets.get(movement);
            return ticket != null && ticket.invalidated;
        });
    }

    void release() {
        for (Ticket ticket : this.tickets.values()) {
            PENDING.computeIfPresent(ticket.itemId, (id, pending) -> {
                pending.remove(ticket);
                return pending.isEmpty() ? null : pending;
            });
        }
        this.tickets.clear();
    }
}

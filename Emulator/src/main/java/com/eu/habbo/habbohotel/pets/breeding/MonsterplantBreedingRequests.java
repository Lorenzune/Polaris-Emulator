package com.eu.habbo.habbohotel.pets.breeding;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded, expiring requests. The room id prevents acceptance after either pet has moved. */
public final class MonsterplantBreedingRequests {
    private static final MonsterplantBreedingRequests INSTANCE =
            new MonsterplantBreedingRequests(System::currentTimeMillis);
    private final Map<Integer, Request> requests = new HashMap<>();
    private final LongSupplier clock;

    public record Request(int requesterId, int requesterPetId, int askedPetId, int roomId, long expiresAt) {}

    public MonsterplantBreedingRequests(LongSupplier clock) {
        this.clock = clock;
    }

    public static MonsterplantBreedingRequests getInstance() {
        return INSTANCE;
    }

    private void prune() {
        long now = clock.getAsLong();
        requests.values().removeIf(r -> r.expiresAt() <= now);
    }

    public synchronized boolean open(int owner, int mine, int theirs, int room) {
        prune();
        if (owner <= 0 || mine <= 0 || theirs <= 0 || mine == theirs || room <= 0 || requests.size() >= 4096)
            return false;
        if (requests.values().stream()
                .anyMatch(r -> r.requesterPetId() == mine
                        || r.askedPetId() == mine
                        || r.requesterPetId() == theirs
                        || r.askedPetId() == theirs)) return false;
        requests.put(theirs, new Request(owner, mine, theirs, room, clock.getAsLong() + 60_000));
        return true;
    }

    public synchronized Request on(int pet) {
        prune();
        return requests.get(pet);
    }

    public synchronized boolean close(Request request) {
        prune();
        return requests.remove(request.askedPetId(), request);
    }

    public synchronized java.util.List<Request> cancelOwner(
            int owner, int room, java.util.function.IntUnaryOperator petOwner) {
        prune();
        var cancelled = requests.values().stream()
                .filter(r -> r.roomId() == room
                        && (r.requesterId() == owner || petOwner.applyAsInt(r.askedPetId()) == owner))
                .toList();
        cancelled.forEach(r -> requests.remove(r.askedPetId(), r));
        return cancelled;
    }
}

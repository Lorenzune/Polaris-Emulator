package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal owner of WIRED stack lookup and source-trigger cache state.
 *
 * <p>The public {@link WiredStackIndex} remains unchanged and is still exposed by the engine for
 * plugin compatibility. Entries are scoped to both room lifecycle and wired mutation generations;
 * publication retries if invalidation overlaps a build.
 */
final class WiredStackRepository {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(WiredStackRepository.class);

    private final WiredStackIndex index;
    private final ConcurrentHashMap<SourceCacheKey, CachedStacks> sourceStacksByTriggerKey;
    private final AtomicLong publicationEpoch;
    private final ConcurrentHashMap<SignalCacheKey, SignalRoutes> signalRoutes = new ConcurrentHashMap<>();

    WiredStackRepository(WiredStackIndex index) {
        this.index = Objects.requireNonNull(index, "index");
        this.sourceStacksByTriggerKey = new ConcurrentHashMap<>();
        this.publicationEpoch = new AtomicLong();
    }

    List<WiredStack> getStacks(Room room, WiredEvent.Type eventType) {
        return this.index.getStacks(room, eventType);
    }

    List<WiredStack> getSignalStacks(Room room, int channel) {
        while (true) {
            SignalCacheKey key = SignalCacheKey.capture(room);
            long epoch = publicationEpoch.get();
            SignalRoutes routes = signalRoutes.get(key);
            if (routes != null && routes.epoch != epoch) {
                signalRoutes.remove(key, routes);
                continue;
            }
            if (routes == null) {
                signalRoutes.keySet().removeIf(old -> old.roomId == key.roomId && !old.equals(key));
                SignalRoutes built = new SignalRoutes(index.getStacks(room, WiredEvent.Type.SIGNAL_RECEIVED), epoch);
                if (!key.equals(SignalCacheKey.capture(room)) || epoch != publicationEpoch.get()) continue;
                SignalRoutes previous = signalRoutes.putIfAbsent(key, built);
                routes = previous != null ? previous : built;
            }
            List<WiredStack> result = routes.forChannel(channel);
            if (key.equals(SignalCacheKey.capture(room))
                    && epoch == publicationEpoch.get()
                    && routes.epoch == epoch
                    && signalRoutes.get(key) == routes) return result;
            signalRoutes.remove(key, routes);
        }
    }

    WiredStackIndex eventIndex() {
        return this.index;
    }

    /** Multiple stacks can legally share one source trigger item. */
    List<WiredStack> getStacksForSourceItem(Room room, WiredEvent.Type eventType, int sourceItemId) {
        while (true) {
            SourceCacheKey cacheKey = SourceCacheKey.capture(room, eventType, sourceItemId);
            long epoch = this.publicationEpoch.get();

            CachedStacks cached = this.sourceStacksByTriggerKey.get(cacheKey);
            if (cached != null) {
                if (this.canReturn(room, cacheKey, cached, epoch)) {
                    return cached.stacks();
                }
                continue;
            }

            this.removeStaleRoomEntries(cacheKey);
            List<WiredStack> allStacks = this.index.getStacks(room, eventType);
            List<WiredStack> matching = new ArrayList<>();
            for (WiredStack stack : allStacks) {
                if (stack != null
                        && stack.triggerItem() != null
                        && stack.triggerItem().getId() == sourceItemId) {
                    matching.add(stack);
                }
            }

            List<WiredStack> result =
                    matching.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(matching);
            if (!cacheKey.matches(room) || epoch != this.publicationEpoch.get()) {
                continue;
            }

            CachedStacks candidate = new CachedStacks(result);
            CachedStacks previous = this.sourceStacksByTriggerKey.putIfAbsent(cacheKey, candidate);
            CachedStacks published = previous != null ? previous : candidate;
            if (this.canReturn(room, cacheKey, published, epoch)) {
                return published.stacks();
            }
            if (previous == null) {
                this.sourceStacksByTriggerKey.remove(cacheKey, candidate);
            }
        }
    }

    void clearRoomSourceStackCache(int roomId) {
        this.publicationEpoch.incrementAndGet();
        this.sourceStacksByTriggerKey.keySet().removeIf(key -> key.roomId() == roomId);
        this.signalRoutes.keySet().removeIf(key -> key.roomId() == roomId);
    }

    void clearAllSourceStackCache() {
        this.publicationEpoch.incrementAndGet();
        this.sourceStacksByTriggerKey.clear();
        this.signalRoutes.clear();
    }

    int sourceStackCacheSize() {
        return this.sourceStacksByTriggerKey.size();
    }

    private boolean canReturn(Room room, SourceCacheKey key, CachedStacks cached, long epoch) {
        return key.matches(room)
                && epoch == this.publicationEpoch.get()
                && this.sourceStacksByTriggerKey.get(key) == cached;
    }

    private void removeStaleRoomEntries(SourceCacheKey currentKey) {
        this.sourceStacksByTriggerKey
                .keySet()
                .removeIf(key -> key.roomId() == currentKey.roomId()
                        && (key.lifecycleGeneration() != currentKey.lifecycleGeneration()
                                || key.wiredGeneration() != currentKey.wiredGeneration()));
    }

    private record CachedStacks(List<WiredStack> stacks) {}

    private record SignalCacheKey(int roomId, long lifecycle, long mutation) {
        static SignalCacheKey capture(Room room) {
            return new SignalCacheKey(room.getId(), room.getLifecycleGeneration(), room.getWiredCacheGeneration());
        }
    }

    private record PositionedStack(int position, WiredStack stack) {}

    private static final class SignalRoutes {
        private final long epoch;
        private final Map<Integer, List<PositionedStack>> channels = new java.util.HashMap<>();
        private final List<PositionedStack> dynamic = new ArrayList<>();
        private final List<WiredStack> fallback;
        // Cache only configured channels, so arbitrary signal IDs cannot grow it.
        private final ConcurrentHashMap<Integer, List<WiredStack>> merged = new ConcurrentHashMap<>();

        SignalRoutes(List<WiredStack> stacks, long epoch) {
            this.epoch = epoch;
            for (int position = 0; position < stacks.size(); position++) {
                WiredStack stack = stacks.get(position);
                if (stack == null) continue;
                var entry = new PositionedStack(position, stack);
                java.util.Set<Integer> ids = null;
                if (stack.triggerItem() != null
                        && stack.triggerItem().getClass() == WiredTriggerReceiveSignal.class
                        && stack.trigger() == stack.triggerItem()) {
                    try {
                        ids = ((WiredTriggerReceiveSignal) stack.triggerItem()).getStaticSignalChannels();
                    } catch (java.util.ConcurrentModificationException concurrentEdit) {
                        LOGGER.debug(
                                "WIRED signal routing uses dynamic fallback for receiver {} during an edit",
                                stack.triggerItem().getId());
                    }
                }
                if (ids == null) dynamic.add(entry);
                else
                    for (int id : ids)
                        channels.computeIfAbsent(id, unused -> new ArrayList<>())
                                .add(entry);
            }
            fallback = dynamic.stream().map(PositionedStack::stack).toList();
        }

        List<WiredStack> forChannel(int channel) {
            List<PositionedStack> fixed = channels.get(channel);
            if (fixed == null) return fallback;
            return merged.computeIfAbsent(channel, unused -> {
                List<WiredStack> result = new ArrayList<>(fixed.size() + dynamic.size());
                int a = 0, b = 0;
                while (a < fixed.size() || b < dynamic.size()) {
                    if (b == dynamic.size() || (a < fixed.size() && fixed.get(a).position < dynamic.get(b).position))
                        result.add(fixed.get(a++).stack);
                    else result.add(dynamic.get(b++).stack);
                }
                return List.copyOf(result);
            });
        }
    }

    private record SourceCacheKey(
            int roomId, long lifecycleGeneration, long wiredGeneration, WiredEvent.Type eventType, int sourceItemId) {

        static SourceCacheKey capture(Room room, WiredEvent.Type eventType, int sourceItemId) {
            return new SourceCacheKey(
                    room.getId(),
                    room.getLifecycleGeneration(),
                    room.getWiredCacheGeneration(),
                    eventType,
                    sourceItemId);
        }

        boolean matches(Room room) {
            return room != null
                    && room.getId() == this.roomId
                    && room.getLifecycleGeneration() == this.lifecycleGeneration
                    && room.getWiredCacheGeneration() == this.wiredGeneration;
        }
    }
}

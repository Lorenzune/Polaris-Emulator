package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClientFlushBatch;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.levels.AvatarLevelPlatform;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.RoomAccessDeniedComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemsDataUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomCycleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomCycleManager.class);

    private final Room room;
    // Accessed under the room monitor, shared with the normal room cycle.
    private final java.util.Map<RoomUnit, UserWalk> userWalks = new java.util.IdentityHashMap<>();
    private final BiFunction<Runnable, Long, ScheduledFuture<?>> walkScheduler;
    private boolean cycleOdd;
    private long cycleTimestamp;
    private final ConcurrentHashMap<Integer, Long> hostingVisitorLastCountedAt;
    private final ConcurrentHashMap<Integer, TimeSpentSession> timeSpentSessions;
    private final ConcurrentHashMap<Integer, Long> avatarHostingLastCountedAt = new ConcurrentHashMap<>();
    private final java.util.Map<Integer, AvatarHostingCheckpoint> avatarHostingCheckpoints = new java.util.HashMap<>();

    private static final class AvatarHostingCheckpoint {
        final String sessionId = java.util.UUID.randomUUID().toString();
        long cumulativeMillis;
        long queuedMillis;
    }

    private long rollerCycle = System.currentTimeMillis();

    public RoomCycleManager(Room room) {
        this(
                room,
                (task, delay) ->
                        Emulator.getGameEnvironment().getRoomCycleService().scheduleWalk(task, delay));
    }

    RoomCycleManager(Room room, BiFunction<Runnable, Long, ScheduledFuture<?>> walkScheduler) {
        this.room = room;
        this.walkScheduler = walkScheduler;
        this.cycleOdd = false;
        this.cycleTimestamp = 0;
        this.hostingVisitorLastCountedAt = new ConcurrentHashMap<>();
        this.timeSpentSessions = new ConcurrentHashMap<>();
    }

    public long getCycleTimestamp() {
        return this.cycleTimestamp;
    }

    public void resetIdleCycles() {
        this.room.resetIdleCycles();
    }

    public void cycle() {
        try (GameClientFlushBatch ignored = GameClientFlushBatch.open()) {
            this.cycleWithCoalescedFlushes();
            if (room.isLoaded() && room.getWiredRuntime() != null)
                room.getWiredRuntime().getVariableMonitor().poll(room);
        }
    }

    private void cycleWithCoalescedFlushes() {
        this.cycleOdd = !this.cycleOdd;
        this.cycleTimestamp = System.currentTimeMillis();
        final boolean[] foundRightHolder = {false};

        boolean loaded = this.room.isLoaded();

        if (loaded) {
            processScheduledTasks();
            processCycleTasks();
            processDecoHosting();
            processAvatarHosting();
            processTimeSpentRoomAchievements();

            if (!this.room.getCurrentHabbos().isEmpty()) {
                this.advanceIdleUnload(false);

                Set<RoomUnit> updatedUnit = new HashSet<>();
                ArrayList<Habbo> toKick = new ArrayList<>();

                final long millis = System.currentTimeMillis();

                for (Habbo habbo : this.room.getCurrentHabbos().values()) {
                    if (!foundRightHolder[0]) {
                        foundRightHolder[0] = habbo.getRoomUnit().getRightsLevel() != RoomRightLevels.NONE;
                    }

                    processHabboHandItem(habbo, millis);
                    processHabboEffect(habbo, millis);
                    processHabboKick(habbo);
                    processHabboIdle(habbo, toKick);
                    processHabboMute(habbo);
                    processHabboChatCounter(habbo);

                    if (this.cycleRoomUnit(habbo.getRoomUnit(), RoomUnitType.USER, habbo)) {
                        updatedUnit.add(habbo.getRoomUnit());
                    }
                }

                for (Habbo habbo : toKick) {
                    Emulator.getGameEnvironment().getRoomManager().leaveRoom(habbo, this.room);
                }

                processBots(updatedUnit);
                processPets(updatedUnit);
                processRollers(updatedUnit);

                if (!updatedUnit.isEmpty()) {
                    this.publishUnitUpdates(updatedUnit);
                }

                if (this.room.getTraxManager() != null) {
                    this.room.getTraxManager().cycle();
                }
            } else if (this.advanceIdleUnload(true)) {
                this.room.dispose();
            }
        }

        processHabboQueue(foundRightHolder[0]);
        processScheduledComposers();
    }

    boolean advanceIdleUnload(boolean empty) {
        return this.room.advanceIdleUnload(empty);
    }

    private void processScheduledTasks() {
        Runnable task;
        while ((task = this.room.scheduledTasks.poll()) != null) {
            Emulator.getThreading().run(task);
        }
    }

    private void processCycleTasks() {
        if (this.room.getRoomSpecialTypes() != null) {
            for (ICycleable task : this.room.getRoomSpecialTypes().getCycleTasks()) {
                task.cycle(this.room);
            }
        }
    }

    private void processDecoHosting() {
        if (!Emulator.getConfig().getBoolean("hotel.rooms.deco_hosting")) {
            this.hostingVisitorLastCountedAt.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Set<Integer> activeVisitors = ConcurrentHashMap.newKeySet();
        for (Habbo visitor : this.room.getCurrentHabbos().values()) {
            if (visitor.getHabboInfo().getId() == this.room.getOwnerId()) continue;

            int visitorId = visitor.getHabboInfo().getId();
            activeVisitors.add(visitorId);
            long enteredAt = visitor.getHabboStats().roomEnterTimestamp * 1000L;
            long lastCountedAt = this.hostingVisitorLastCountedAt.computeIfAbsent(
                    visitorId, ignored -> enteredAt > 0 ? enteredAt : now);
            long minutes = (now - lastCountedAt) / 60_000L;
            if (minutes <= 0) continue;

            AchievementManager.progressAchievement(
                    this.room.getOwnerId(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoHosting"),
                    Math.toIntExact(Math.min(minutes, Integer.MAX_VALUE)));
            this.hostingVisitorLastCountedAt.put(visitorId, lastCountedAt + (minutes * 60_000L));
        }

        this.hostingVisitorLastCountedAt.keySet().removeIf(visitorId -> !activeVisitors.contains(visitorId));
    }

    /** Flushes the exact completed minutes for a visitor before the room removes them. */
    void flushDecoHostingVisitor(Habbo visitor) {
        if (visitor != null) {
            countAvatarHostingVisitor(visitor, System.nanoTime() / 1_000_000L);
            this.avatarHostingLastCountedAt.remove(visitor.getHabboInfo().getId());
            flushAvatarHosting();
        }
        if (!Emulator.getConfig().getBoolean("hotel.rooms.deco_hosting")
                || visitor == null
                || visitor.getHabboInfo().getId() == this.room.getOwnerId()) return;

        long now = System.currentTimeMillis();
        int visitorId = visitor.getHabboInfo().getId();
        long enteredAt = visitor.getHabboStats().roomEnterTimestamp * 1000L;
        long lastCountedAt = this.hostingVisitorLastCountedAt.getOrDefault(visitorId, enteredAt > 0 ? enteredAt : now);
        long minutes = (now - lastCountedAt) / 60_000L;
        if (minutes > 0) {
            AchievementManager.progressAchievement(
                    this.room.getOwnerId(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoHosting"),
                    Math.toIntExact(Math.min(minutes, Integer.MAX_VALUE)));
        }
        this.hostingVisitorLastCountedAt.remove(visitorId);
    }

    private synchronized void processAvatarHosting() {
        long now = System.nanoTime() / 1_000_000L;
        Set<Integer> visitors = new HashSet<>();
        for (Habbo visitor : this.room.getCurrentHabbos().values()) {
            visitors.add(visitor.getHabboInfo().getId());
            countAvatarHostingVisitor(visitor, now);
        }
        this.avatarHostingLastCountedAt.keySet().removeIf(id -> !visitors.contains(id));
        flushAvatarHosting();
    }

    synchronized void countAvatarHostingVisitor(Habbo visitor, long now) {
        int visitorId = visitor.getHabboInfo().getId();
        if (visitorId == this.room.getOwnerId() || !visitor.isOnline()) return;
        var manager = AvatarLevelPlatform.game().getAvatarLevelManager();
        if (manager == null) return;
        Long previous = this.avatarHostingLastCountedAt.put(visitorId, now);
        if (previous != null && now > previous) {
            var checkpoint = avatarHostingCheckpoints.computeIfAbsent(
                    this.room.getOwnerId(), ignored -> new AvatarHostingCheckpoint());
            checkpoint.cumulativeMillis += (now - previous) * manager.getCachedLevel(visitorId);
        }
    }

    synchronized void flushAvatarHosting() {
        var manager = AvatarLevelPlatform.game().getAvatarLevelManager();
        if (manager == null) return;
        for (var entry : avatarHostingCheckpoints.entrySet()) {
            var checkpoint = entry.getValue();
            if (checkpoint.cumulativeMillis > checkpoint.queuedMillis
                    && manager.recordHosting(entry.getKey(), checkpoint.sessionId, checkpoint.cumulativeMillis)) {
                checkpoint.queuedMillis = checkpoint.cumulativeMillis;
            }
        }
    }

    private void processTimeSpentRoomAchievements() {
        long now = System.currentTimeMillis();
        String achievementName = this.roomTimeAchievement();
        Set<Integer> activeUsers = ConcurrentHashMap.newKeySet();

        for (Habbo habbo : this.room.getCurrentHabbos().values()) {
            int userId = habbo.getHabboInfo().getId();
            activeUsers.add(userId);

            if (achievementName == null) {
                this.timeSpentSessions.remove(userId);
                continue;
            }

            long enteredAt = habbo.getHabboStats().roomEnterTimestamp * 1000L;
            TimeSpentSession session = this.timeSpentSessions.get(userId);
            if (session == null) {
                session = new TimeSpentSession(achievementName, enteredAt > 0 ? enteredAt : now);
            } else if (!session.achievementName.equals(achievementName)) {
                this.progressElapsedMinutes(habbo, session, now);
                session = new TimeSpentSession(achievementName, now);
            }

            this.progressElapsedMinutes(habbo, session, now);
            this.timeSpentSessions.put(userId, session);
        }

        this.timeSpentSessions.keySet().removeIf(userId -> !activeUsers.contains(userId));
    }

    /** Flushes the exact completed minutes for a user before the room removes them. */
    void flushTimeSpentVisitor(Habbo visitor) {
        if (visitor == null) return;

        int userId = visitor.getHabboInfo().getId();
        TimeSpentSession session = this.timeSpentSessions.remove(userId);
        if (session == null) {
            String achievementName = this.roomTimeAchievement();
            if (achievementName == null) return;
            long enteredAt = visitor.getHabboStats().roomEnterTimestamp * 1000L;
            session = new TimeSpentSession(achievementName, enteredAt > 0 ? enteredAt : System.currentTimeMillis());
        }
        this.progressElapsedMinutes(visitor, session, System.currentTimeMillis());
    }

    private void progressElapsedMinutes(Habbo habbo, TimeSpentSession session, long now) {
        long minutes = (now - session.lastCountedAt) / 60_000L;
        if (minutes <= 0) return;

        AchievementInteractions.progress(
                habbo, session.achievementName, Math.toIntExact(Math.min(minutes, Integer.MAX_VALUE)));
        session.lastCountedAt += minutes * 60_000L;
    }

    private String roomTimeAchievement() {
        if (this.room.isPublicRoom()) return "TimeSpentRoomTypeChat";

        RoomCategory category = Emulator.getGameEnvironment().getRoomManager().getCategory(this.room.getCategory());
        if (category == null) return null;

        String safeCaption = category.getCaptionSave() == null
                ? ""
                : category.getCaptionSave().toLowerCase(Locale.ROOT);
        String caption =
                category.getCaption() == null ? "" : category.getCaption().toLowerCase(Locale.ROOT);

        if (matchesConfiguredCategory(
                "achievements.room_time.games.categories", "caption_save_games", safeCaption, caption)) {
            return "TimeSpentRoomTypeGames";
        }
        if (matchesConfiguredCategory(
                "achievements.room_time.trade.categories",
                "caption_save_trade,caption_save_marketplace",
                safeCaption,
                caption)) {
            return "TimeSpentRoomTypeTrade";
        }
        if (matchesConfiguredCategory(
                "achievements.room_time.rpg.categories",
                "caption_save_rpg,caption_save_agency,caption_save_roleplay",
                safeCaption,
                caption)) {
            return "TimeSpentRoomTypeRPG";
        }
        if (matchesConfiguredCategory(
                "achievements.room_time.chat.categories",
                "caption_save_chat,caption_save_discussion",
                safeCaption,
                caption)) {
            return "TimeSpentRoomTypeChat";
        }
        return null;
    }

    private boolean matchesConfiguredCategory(String key, String fallback, String safeCaption, String caption) {
        String configured = Emulator.getConfig().getValue(key, fallback);
        if (configured == null || configured.isBlank()) configured = fallback;
        for (String token : configured.toLowerCase(Locale.ROOT).split("[,;]")) {
            String normalized = token.trim();
            if (!normalized.isEmpty() && (safeCaption.equals(normalized) || caption.equals(normalized))) return true;
        }
        return false;
    }

    private static final class TimeSpentSession {
        private final String achievementName;
        private long lastCountedAt;

        private TimeSpentSession(String achievementName, long lastCountedAt) {
            this.achievementName = achievementName;
            this.lastCountedAt = lastCountedAt;
        }
    }

    private void processHabboHandItem(Habbo habbo, long millis) {
        if (Room.HAND_ITEM_TIME > 0
                && habbo.getRoomUnit().getHandItem() > 0
                && millis - habbo.getRoomUnit().getHandItemTimestamp() > (Room.HAND_ITEM_TIME * 1000L)) {
            this.room.giveHandItem(habbo, 0);
        }
    }

    private void processHabboEffect(Habbo habbo, long millis) {
        if (habbo.getRoomUnit().getEffectId() > 0
                && millis / 1000 > habbo.getRoomUnit().getEffectEndTimestamp()) {
            this.room.giveEffect(habbo, 0, -1);
        }
    }

    private void processHabboKick(Habbo habbo) {
        if (habbo.getRoomUnit().isKicked) {
            habbo.getRoomUnit().kickCount++;

            if (habbo.getRoomUnit().kickCount >= 5) {
                final Room room = this.room;
                this.room.scheduledTasks.add(
                        () -> Emulator.getGameEnvironment().getRoomManager().leaveRoom(habbo, room));
            }
        }
    }

    private void processHabboIdle(Habbo habbo, ArrayList<Habbo> toKick) {
        boolean roomIdleBehaviorEnabled = this.room.isIdleSleepEnabled() || this.room.isIdleAutokickEnabled();

        if (Emulator.getConfig().getBoolean("hotel.rooms.auto.idle") || roomIdleBehaviorEnabled) {
            int sleepCycles =
                    this.room.isIdleSleepEnabled() ? this.room.getIdleSleepTimeoutSeconds() * 2 : Room.IDLE_CYCLES;
            int kickCycles = this.room.isIdleAutokickEnabled()
                    ? this.room.getIdleAutokickTimeoutSeconds() * 2
                    : Room.IDLE_CYCLES_KICK;
            int previousIdleCycles = habbo.getRoomUnit().getIdleTimer();
            habbo.getRoomUnit().increaseIdleTimer();

            if (previousIdleCycles <= sleepCycles && habbo.getRoomUnit().getIdleTimer() > sleepCycles) {
                boolean danceIsNone = (habbo.getRoomUnit().getDanceType() == DanceType.NONE);
                if (danceIsNone) {
                    this.room.getUnitManager().idle(habbo);
                }
            }

            if (!this.room.isOwner(habbo) && habbo.getRoomUnit().getIdleTimer() >= kickCycles) {
                UserExitRoomEvent event =
                        new UserExitRoomEvent(habbo, UserExitRoomEvent.UserExitRoomReason.KICKED_IDLE);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    toKick.add(habbo);
                }
            }
        }
    }

    private void processHabboMute(Habbo habbo) {
        if (habbo.getHabboStats().mutedBubbleTracker && habbo.getHabboStats().allowTalk()) {
            habbo.getHabboStats().mutedBubbleTracker = false;
            this.room.sendComposer(new RoomUserIgnoredComposer(habbo, RoomUserIgnoredComposer.UNIGNORED).compose());
        }
    }

    private void processHabboChatCounter(Habbo habbo) {
        if (this.cycleOdd && habbo.getHabboStats().chatCounter.get() > 0) {
            habbo.getHabboStats().chatCounter.decrementAndGet();
        }
    }

    private void processBots(Set<RoomUnit> updatedUnit) {
        Int2ObjectMap<Bot> currentBots = this.room.getCurrentBots();
        if (currentBots.isEmpty()) {
            return;
        }

        final ArrayList<Bot> bots;
        synchronized (currentBots) {
            bots = new ArrayList<>(currentBots.values());
        }

        for (Bot bot : bots) {
            try {
                if (bot == null || bot.getRoomUnit() == null) {
                    continue;
                }

                if (!this.room.isAllowBotsWalk() && bot.getRoomUnit().isWalking()) {
                    bot.getRoomUnit().stopWalking();
                    updatedUnit.add(bot.getRoomUnit());
                    continue;
                }

                bot.cycle(this.room.isAllowBotsWalk());

                if (this.cycleRoomUnit(bot.getRoomUnit(), RoomUnitType.BOT, null)) {
                    updatedUnit.add(bot.getRoomUnit());
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    private void processPets(Set<RoomUnit> updatedUnit) {
        Int2ObjectMap<Pet> currentPets = this.room.getCurrentPets();
        if (currentPets.isEmpty() || !this.room.isAllowBotsWalk()) {
            return;
        }

        final ArrayList<Pet> pets;
        synchronized (currentPets) {
            pets = new ArrayList<>(currentPets.values());
        }

        for (Pet pet : pets) {
            try {
                if (pet == null || pet.getRoomUnit() == null) {
                    continue;
                }

                if (this.cycleRoomUnit(pet.getRoomUnit(), RoomUnitType.PET, null)) {
                    updatedUnit.add(pet.getRoomUnit());
                }

                pet.cycle();

                if (pet.packetUpdate) {
                    updatedUnit.add(pet.getRoomUnit());
                    pet.packetUpdate = false;
                }

                if (pet.getRoomUnit().isWalking()
                        && pet.getRoomUnit().getPath().size() == 1
                        && pet.getRoomUnit().hasStatus(RoomUnitStatus.GESTURE)) {
                    pet.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
                    updatedUnit.add(pet.getRoomUnit());
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    private void processRollers(Set<RoomUnit> updatedUnit) {
        Integer transientRollerSpeed = this.room.getTransientRollerSpeedOverride();
        Integer controlledRollerSpeed = RoomQueueSpeedControlSupport.getEffectiveRollerSpeed(this.room);
        int rollerSpeed = transientRollerSpeed != null
                ? transientRollerSpeed
                : (controlledRollerSpeed != null) ? controlledRollerSpeed : this.room.getRollerSpeed();
        if (rollerSpeed != -1 && this.rollerCycle >= rollerSpeed) {
            this.rollerCycle = 0;
            this.room.getRollerManager().processRollerCycle(updatedUnit, this.cycleTimestamp);
        } else {
            this.rollerCycle++;
            if (this.room.getRollerManager().hasPendingRollRequests()) {
                this.room.getRollerManager().processPendingRollRequests(updatedUnit);
            }
        }
    }

    private void processHabboQueue(boolean foundRightHolder) {
        Int2ObjectMap<Habbo> habboQueue = this.room.getHabboQueue();
        synchronized (habboQueue) {
            if (!habboQueue.isEmpty() && !foundRightHolder) {
                final Room room = this.room;
                for (Habbo queuedHabbo : habboQueue.values()) {
                    if (queuedHabbo.isOnline()) {
                        if (queuedHabbo.getHabboInfo().getRoomQueueId() == room.getId()) {
                            queuedHabbo.getClient().sendResponse(new RoomAccessDeniedComposer(""));
                        }
                    }
                }
                habboQueue.clear();
            }
        }
    }

    /**
     * Flushes visual updates accumulated by one wired execution as a single
     * response batch. This is also used outside the room scheduler: wired
     * signal chains run on the wired tick thread and must not wait for the
     * 500ms room cycle before the client sees their state changes.
     */
    public void flushScheduledComposersNow() {
        if (!this.room.hasScheduledComposers() && this.room.scheduledItemDataUpdates.isEmpty()) {
            return;
        }

        ArrayList<ServerMessage> messages = new ArrayList<>();
        LinkedHashMap<Integer, HabboItem> itemDataUpdates = new LinkedHashMap<>();
        HabboItem item;

        while ((item = this.room.scheduledItemDataUpdates.poll()) != null) {
            if (item.getRoomId() != this.room.getId()
                    || item.getBaseItem() == null
                    || item.getBaseItem().getType() != com.eu.habbo.habbohotel.items.FurnitureType.FLOOR) {
                continue;
            }

            itemDataUpdates.put(item.getId(), item);
        }

        if (!itemDataUpdates.isEmpty()) {
            messages.add(new ItemsDataUpdateComposer(new LinkedHashSet<>(itemDataUpdates.values())).compose());
        }

        ServerMessage message;

        while ((message = this.room.pollScheduledComposer()) != null) {
            messages.add(message);
        }

        if (!messages.isEmpty()) {
            this.room.sendComposers(messages);
        }
    }

    private void processScheduledComposers() {
        this.flushScheduledComposersNow();
    }

    public boolean cycleRoomUnit(RoomUnit unit, RoomUnitType type, Habbo habbo) {
        synchronized (this.room) {
            // Click-driven walking has its own 500ms phase. The room heartbeat
            // must not take an extra step or replay a partial movement.
            if (this.userWalks.containsKey(unit)) return false;
            return this.cycleRoomUnitInternal(unit, type, habbo);
        }
    }

    /** Start a validated click immediately; subsequent clicks only replace its path. */
    public void startUserWalk(Habbo habbo, RoomTile target) {
        synchronized (this.room) {
            RoomUnit unit = habbo.getRoomUnit();
            if (!this.room.isLoaded() || habbo.getHabboInfo().getCurrentRoom() != this.room || !unit.isInRoom()) return;
            unit.setGoalLocation(target);
            if (!target.equals(unit.getGoal()) || this.userWalks.containsKey(unit) || !unit.isWalking()) return;
            UserWalk walk = new UserWalk(habbo, unit);
            this.userWalks.put(unit, walk);
            walk.run();
        }
    }

    protected void publishUnitUpdates(Set<RoomUnit> updatedUnit) {
        ServerMessage statusComposer = new RoomUserStatusComposer(updatedUnit, true).compose();
        WiredMoveCarryHelper.beginMovementCollection();
        WiredMoveCarryHelper.processUserFollowers(this.room, updatedUnit);
        ServerMessage wiredMovementsComposer = WiredMoveCarryHelper.finishMovementCollection();
        if (wiredMovementsComposer != null) {
            this.room.sendComposers(java.util.List.of(statusComposer, wiredMovementsComposer));
        } else {
            this.room.sendComposer(statusComposer);
        }
    }

    private final class UserWalk implements Runnable {
        private final Habbo habbo;
        private final RoomUnit unit;

        private UserWalk(Habbo habbo, RoomUnit unit) {
            this.habbo = habbo;
            this.unit = unit;
        }

        @Override
        public void run() {
            synchronized (room) {
                if (userWalks.get(this.unit) != this) return;
                if (!room.isLoaded()
                        || !this.unit.isInRoom()
                        || this.habbo.getRoomUnit() != this.unit
                        || this.habbo.getHabboInfo().getCurrentRoom() != room
                        || WiredUserMovementHelper.shouldSuppressStatusComposer(this.unit)
                        || WiredMoveCarryHelper.shouldSuppressStatusComposer(this.unit)) {
                    userWalks.remove(this.unit);
                    return;
                }
                try (GameClientFlushBatch ignored = GameClientFlushBatch.open()) {
                    if (cycleRoomUnitInternal(this.unit, RoomUnitType.USER, this.habbo)) {
                        Set<RoomUnit> updated = new HashSet<>();
                        updated.add(this.unit);
                        publishUnitUpdates(updated);
                    }
                    // Keep ownership until the final animation settles, including
                    // a one-tile walk. This prevents the heartbeat clearing mv early.
                    boolean hasPath = this.unit.isWalking()
                            && this.unit.getPath() != null
                            && !this.unit.getPath().isEmpty();
                    if ((hasPath || this.unit.hasStatus(RoomUnitStatus.MOVE))
                            && walkScheduler.apply(this, (long) RoomCycleService.CYCLE_MS) != null) return;
                } catch (Exception e) {
                    LOGGER.error("Click-driven walk failed in room {}", room.getId(), e);
                }
                userWalks.remove(this.unit);
            }
        }
    }

    protected boolean cycleRoomUnitInternal(RoomUnit unit, RoomUnitType type, Habbo habbo) {
        boolean update = unit.needsStatusUpdate();
        boolean isRiding = type == RoomUnitType.USER
                && habbo != null
                && habbo.getHabboInfo() != null
                && habbo.getHabboInfo().getRiding() != null;

        if (unit.hasStatus(RoomUnitStatus.SIGN)) {
            this.room.sendComposer(new RoomUserStatusComposer(unit).compose());
            unit.removeStatus(RoomUnitStatus.SIGN);
        }

        if (unit.isWalking() && unit.getPath() != null && !unit.getPath().isEmpty()) {
            RoomTile previousLocation = unit.getCurrentLocation();
            boolean cycleResult = unit.cycle(this.room);
            RoomTile currentLocation = unit.getCurrentLocation();
            if (type == RoomUnitType.USER
                    && habbo != null
                    && previousLocation != null
                    && currentLocation != null
                    && (previousLocation.x != currentLocation.x || previousLocation.y != currentLocation.y)) {
                AchievementInteractions.progress(habbo, "LegDay");
                int effectId = unit.getEffectId();
                if (effectId == RoomUnitEffect.SKATEBOARD1.getId() || effectId == RoomUnitEffect.SKATEBOARD2.getId()) {
                    AchievementInteractions.progress(habbo, "SkateBoardSlide");
                }
            }
            if (!cycleResult) {
                return true;
            }
        } else {
            if (unit.hasStatus(RoomUnitStatus.MOVE) && !unit.animateWalk) {
                unit.removeStatus(RoomUnitStatus.MOVE);
                update = true;
            }

            if (isRiding) {
                RoomUnit ridingUnit = habbo.getHabboInfo().getRiding().getRoomUnit();

                if (ridingUnit != null && unit.getCurrentLocation() != null) {
                    boolean horseMoving = ridingUnit.hasStatus(RoomUnitStatus.MOVE);
                    RoomTile horseTile = ridingUnit.getCurrentLocation();
                    boolean horseMisplaced =
                            horseTile == null || horseTile.x != unit.getX() || horseTile.y != unit.getY();

                    if (horseMoving || horseMisplaced) {
                        ridingUnit.setPreviousLocation(horseTile != null ? horseTile : unit.getCurrentLocation());
                        ridingUnit.setCurrentLocation(unit.getCurrentLocation());
                        ridingUnit.setGoalLocation(unit.getCurrentLocation());
                        ridingUnit.setZ(unit.getZ() - 1.0);
                        ridingUnit.animateWalk = false;

                        if (horseMoving) ridingUnit.removeStatus(RoomUnitStatus.MOVE);

                        this.room.sendComposer(new RoomUserStatusComposer(ridingUnit).compose());
                    }
                }
            }

            if (!unit.isWalking() && !unit.cmdSit) {
                boolean hasSpecialPetStatus = unit.hasStatus(RoomUnitStatus.HANG)
                        || unit.hasStatus(RoomUnitStatus.SWING)
                        || unit.hasStatus(RoomUnitStatus.FLAME)
                        || unit.hasStatus(RoomUnitStatus.PLAY);

                RoomTile thisTile = this.room.getLayout().getTile(unit.getX(), unit.getY());
                HabboItem topItem = this.room.getTallestChair(thisTile);

                if (isRiding || topItem == null || !topItem.getBaseItem().allowSit()) {
                    if (unit.hasStatus(RoomUnitStatus.SIT)) {
                        unit.removeStatus(RoomUnitStatus.SIT);
                        unit.setZ(thisTile.getStackHeight());
                        unit.setPreviousLocationZ(thisTile.getStackHeight());
                        update = true;
                    }
                } else if (!hasSpecialPetStatus
                        && thisTile.state == RoomTileState.SIT
                        && (!unit.hasStatus(RoomUnitStatus.SIT) || unit.sitUpdate)) {
                    this.room.dance(unit, DanceType.NONE);
                    unit.setStatus(RoomUnitStatus.SIT, (Item.getCurrentHeight(topItem) * 1.0D) + "");
                    unit.setZ(topItem.getZ());
                    unit.setPreviousLocationZ(topItem.getZ());
                    unit.setRotation(RoomUserRotation.values()[topItem.getRotation()]);
                    unit.sitUpdate = false;
                    return true;
                }
            }
        }

        if (!unit.isWalking() && !unit.cmdLay) {
            HabboItem topItem = this.room.getTopItemAt(unit.getX(), unit.getY());

            if (isRiding || topItem == null || !topItem.getBaseItem().allowLay()) {
                if (unit.hasStatus(RoomUnitStatus.LAY)) {
                    unit.removeStatus(RoomUnitStatus.LAY);
                    RoomTile thisTile = this.room.getLayout().getTile(unit.getX(), unit.getY());
                    if (thisTile != null) {
                        unit.setZ(thisTile.getStackHeight());
                        unit.setPreviousLocationZ(thisTile.getStackHeight());
                    }
                    update = true;
                }
            } else {
                if (!unit.hasStatus(RoomUnitStatus.LAY)) {
                    BedProfile bedProfile = new BedProfile(topItem);
                    double layHeight = Item.getCurrentHeight(topItem) * 1.0D + bedProfile.getLayZOffset();
                    LOGGER.debug(
                            "[BedProfile] item={} stackHeight={} isFlat={} isDouble={} X={} Y={} Z={}",
                            topItem.getBaseItem().getName(),
                            topItem.getBaseItem().getHeight(),
                            bedProfile.isFlat(),
                            bedProfile.isDouble(),
                            bedProfile.getLayXOffset(),
                            bedProfile.getLayYOffset(),
                            bedProfile.getLayZOffset());
                    unit.setStatus(
                            RoomUnitStatus.LAY,
                            layHeight + ";" + bedProfile.getLayXOffset() + ";" + bedProfile.getLayYOffset());
                    unit.setRotation(RoomUserRotation.values()[topItem.getRotation() % 4]);
                    unit.setLocation(bedProfile.snapToLay(this.room, topItem, unit.getX(), unit.getY()));

                    this.room.getUnitManager().checkBedLoveEffect(topItem);

                    update = true;
                }
            }
        }

        if (update) {
            unit.statusUpdate(false);
        }

        return update;
    }
}

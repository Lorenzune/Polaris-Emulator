package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomUpdateScope;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiredEffectSendSignal extends InteractionWiredEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectSendSignal.class);

    public static final WiredEffectType type = WiredEffectType.SEND_SIGNAL;

    /** Zero disables the signal-chain depth limit. */
    public static volatile int MAX_SIGNAL_DEPTH = 0;

    private static final int ANTENNA_PICKED = 0;
    private static final int ANTENNA_TRIGGER = 1;
    private static final int SWF_FURNI_SOURCE_PICKS_2 = 101;
    private static final String ANTENNA_INTERACTION = "antenna";
    private static final String FORWARD_ITEM_SPLIT_REGEX = "[;,\\t]";

    private Set<HabboItem> items;
    private Set<HabboItem> forwardItems;
    private int antennaSource = ANTENNA_PICKED;
    private int furniForward = WiredSourceUtil.SOURCE_TRIGGER;
    private int userForward = WiredSourceUtil.SOURCE_TRIGGER;
    private boolean signalPerFurni = false;
    private boolean signalPerUser = false;
    private int channel = 0;
    private boolean nativeSignal;
    private int nativeAntennaSource = WiredSourceUtil.SOURCE_SELECTED;

    public WiredEffectSendSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
        this.forwardItems = new LinkedHashSet<>();
    }

    public WiredEffectSendSignal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
        this.forwardItems = new LinkedHashSet<>();
    }

    public boolean isNativeSelfFeedback(WiredContext ctx) {
        return this.nativeSignal
                && this.nativeAntennaSource == WiredSourceUtil.SOURCE_SELECTED
                && ctx.triggerItem()
                        instanceof
                        com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal receiver
                && this.items.stream().anyMatch(receiver::listensToPickedAntenna);
    }

    public boolean isNativeSignal() {
        return this.nativeSignal;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                    "[SendSignal] execute() called, itemId={}, antennaSource={}, pickedItems={}, configuredDelay={}",
                    this.getId(),
                    antennaSource,
                    this.items.size(),
                    this.getDelay());
        }

        int currentDepth = ctx.event().getCallStackDepth();
        if (hasReachedMaxSignalDepth(currentDepth)) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[SendSignal] Max signal depth reached ({}), aborting", currentDepth);
            }
            return;
        }

        if (nativeSignal) {
            executeNativeSignal(ctx);
            return;
        }

        Collection<HabboItem> antennas;

        if (antennaSource == ANTENNA_TRIGGER) {
            antennas = ctx.sourceItem().map(Collections::<HabboItem>singleton).orElse(Collections.emptySet());
        } else {
            Collection<HabboItem> baseAntennas = new ArrayList<>(this.items);

            if (baseAntennas.isEmpty() && antennaSource > ANTENNA_TRIGGER) {
                HabboItem antenna = room.getHabboItem(antennaSource);
                antennas = antenna != null ? Collections.singleton(antenna) : Collections.emptySet();
            } else {
                antennas = baseAntennas;
            }
        }

        List<HabboItem> resolvedAntennas = antennas.stream()
                .filter(Objects::nonNull)
                .filter(this::isAntennaItem)
                .collect(Collectors.toList());

        if (resolvedAntennas.isEmpty()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "[SendSignal] No antennas resolved, aborting. antennaSource={}, selectorModified={}",
                        antennaSource,
                        ctx.targets().isItemsModifiedBySelector());
            }
            return;
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[SendSignal] Resolved {} antenna(s), firing signals", resolvedAntennas.size());
        }

        RoomUnit triggeringUser =
                ctx.event().getOriginActor().orElseGet(() -> ctx.actor().orElse(null));

        List<RoomUnit> forwardedUsers = WiredSourceUtil.resolveUsersRaw(ctx, this.userForward);

        List<HabboItem> forwardedFurni = WiredSourceUtil.resolveItemsRaw(ctx, this.furniForward, this.forwardItems);

        /*
         * Debug fondamentale:
         *
         * Se furniForward == SOURCE_SELECTOR, selectorTargets e forwardedFurni
         * dovrebbero contenere gli stessi furni selezionati dalla pila.
         */
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                    "[SendSignal] furniForward={}, signalPerFurni={}, selectorTargets={}, forwardedFurni={}",
                    this.furniForward,
                    this.signalPerFurni,
                    ctx.targets().items().stream()
                            .filter(Objects::nonNull)
                            .map(HabboItem::getId)
                            .collect(Collectors.toList()),
                    forwardedFurni.stream()
                            .filter(Objects::nonNull)
                            .map(HabboItem::getId)
                            .collect(Collectors.toList()));
        }

        List<RoomUnit> usersToSend;

        if (signalPerUser) {
            LinkedHashMap<Integer, RoomUnit> mergedUsers = new LinkedHashMap<>();

            if (triggeringUser != null) {
                mergedUsers.put(triggeringUser.getId(), triggeringUser);
            }

            for (RoomUnit forwardedUser : forwardedUsers) {
                if (forwardedUser == null) {
                    continue;
                }

                mergedUsers.put(forwardedUser.getId(), forwardedUser);
            }

            usersToSend =
                    mergedUsers.isEmpty() ? Collections.singletonList(null) : new ArrayList<>(mergedUsers.values());
        } else {
            usersToSend = Collections.singletonList(triggeringUser);
        }

        List<RoomUnit> signalSourceUsers;
        if (signalPerUser) {
            signalSourceUsers = usersToSend.stream().filter(Objects::nonNull).collect(Collectors.toList());
        } else if (!forwardedUsers.isEmpty()) {
            signalSourceUsers = new ArrayList<>(forwardedUsers.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        } else if (triggeringUser != null) {
            signalSourceUsers = Collections.singletonList(triggeringUser);
        } else {
            signalSourceUsers = List.of();
        }

        /*
         * FIX:
         *
         * signalPerFurni = true
         *   -> un SIGNAL_RECEIVED indipendente per ogni furni risolto.
         *
         * signalPerFurni = false
         *   -> un solo SIGNAL_RECEIVED.
         *
         * LinkedHashSet elimina eventuali duplicati mantenendo l'ordine.
         */
        List<HabboItem> signalSourceItems =
                forwardedFurni.isEmpty() ? List.of() : new ArrayList<>(new LinkedHashSet<>(forwardedFurni));

        // Keep one immutable snapshot for all events emitted by this effect. Each
        // WiredEvent can then safely share these forwarded-source lists.
        signalSourceUsers = List.copyOf(signalSourceUsers);
        signalSourceItems =
                List.copyOf(signalSourceItems.stream().filter(Objects::nonNull).toList());

        Collection<HabboItem> furniToSend;

        if (signalPerFurni) {
            furniToSend = signalSourceItems.isEmpty() ? Collections.singletonList(null) : signalSourceItems;
        } else {
            HabboItem sourceItem = signalSourceItems.isEmpty() ? null : signalSourceItems.get(0);

            furniToSend = Collections.singletonList(sourceItem);
        }

        int nextDepth = currentDepth + 1;
        long signalCreatedAtMs = System.currentTimeMillis();

        int signalUserCount = signalPerUser
                ? (int) usersToSend.stream().filter(Objects::nonNull).count()
                : (!forwardedUsers.isEmpty() ? forwardedUsers.size() : (triggeringUser != null ? 1 : 0));

        signalEmissionLoop:
        try (WiredRoomUpdateScope ignored = WiredRoomUpdateScope.batchVisualUpdates()) {
            for (RoomUnit user : usersToSend) {
                for (HabboItem sourceItem : furniToSend) {
                    for (HabboItem antenna : resolvedAntennas) {

                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace(
                                    "[SendSignal] sending event: antenna={}, sourceItem={}, user={}, depth={}",
                                    antenna != null ? antenna.getId() : null,
                                    sourceItem != null ? sourceItem.getId() : null,
                                    user != null ? user.getId() : null,
                                    nextDepth);
                        }

                        boolean admitted = fireSignalAtAntenna(
                                ctx,
                                room,
                                antenna,
                                user,
                                triggeringUser,
                                sourceItem,
                                signalSourceUsers,
                                signalSourceItems,
                                signalPerUser,
                                signalPerFurni,
                                signalUserCount,
                                nextDepth,
                                signalCreatedAtMs);

                        if (!admitted) {
                            break signalEmissionLoop;
                        }
                    }
                }
            }
        }

        // If this Send Signal was not already part of a larger SIGNAL_RECEIVED
        // chain, flush its grouped visual updates immediately. Nested signal
        // chains remain grouped and are flushed by WiredManager at the root.
        if (!WiredRoomUpdateScope.shouldDeferVisualUpdates()) {
            room.getCycleManager().flushScheduledComposersNow();
        }
    }

    private void executeNativeSignal(WiredContext ctx) {
        Room room = ctx.room();
        List<HabboItem> antennas = WiredSourceUtil.resolveItemsRaw(ctx, nativeAntennaSource, this.items).stream()
                .filter(this::isAntennaItem)
                .filter(item -> room.getHabboItem(item.getId()) == item)
                .filter(item -> room.getLayout().getTile(item.getX(), item.getY()) != null)
                .distinct()
                .toList();
        if (antennas.isEmpty()) return;
        List<RoomUnit> users = List.copyOf(new LinkedHashSet<>(WiredSourceUtil.resolveUsersRaw(ctx, this.userForward)));
        List<HabboItem> furni = List.copyOf(
                new LinkedHashSet<>(WiredSourceUtil.resolveItemsRaw(ctx, this.furniForward, this.forwardItems).stream()
                        .filter(item -> room.getHabboItem(item.getId()) == item)
                        .toList()));
        if ((signalPerUser && users.isEmpty()) || (signalPerFurni && furni.isEmpty())) return;
        RoomUnit origin =
                ctx.event().getOriginActor().orElseGet(() -> ctx.actor().orElse(null));
        List<RoomUnit> actors =
                signalPerUser ? users : Collections.singletonList(ctx.actor().orElse(null));
        List<HabboItem> sources = signalPerFurni
                ? furni
                : Collections.singletonList(ctx.sourceItem().orElse(null));
        long createdAt = System.currentTimeMillis();
        signalEmissionLoop:
        try (WiredRoomUpdateScope ignored = WiredRoomUpdateScope.batchVisualUpdates()) {
            for (RoomUnit actor : actors)
                for (HabboItem source : sources)
                    for (HabboItem antenna : antennas) {
                        List<RoomUnit> payloadUsers = signalPerUser ? List.of(actor) : users;
                        List<HabboItem> payloadItems = signalPerFurni ? List.of(source) : furni;
                        if (!fireSignalAtAntenna(
                                ctx,
                                room,
                                antenna,
                                actor,
                                origin,
                                source,
                                payloadUsers,
                                payloadItems,
                                signalPerUser,
                                signalPerFurni,
                                payloadUsers.size(),
                                ctx.event().getCallStackDepth() + 1,
                                createdAt)) break signalEmissionLoop;
                    }
        }
        if (!WiredRoomUpdateScope.shouldDeferVisualUpdates())
            room.getCycleManager().flushScheduledComposersNow();
    }

    private boolean fireSignalAtAntenna(
            WiredContext ctx,
            Room room,
            HabboItem antenna,
            RoomUnit actor,
            RoomUnit originActor,
            HabboItem sourceItem,
            List<RoomUnit> signalSourceUsers,
            List<HabboItem> signalSourceItems,
            boolean signalPerUser,
            boolean signalPerFurni,
            int signalUserCount,
            int depth,
            long createdAtMs) {

        if (antenna == null) return false;

        RoomTile tile = room.getLayout().getTile(antenna.getX(), antenna.getY());
        if (tile == null) return false;

        pulseAntenna(room, antenna);

        /*
         * Il canale del segnale resta l'ID dell'antenna.
         * Il furni trasportato dal segnale viene invece salvato come sourceItem.
         */
        int signalChannel = antenna.getId();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                    "[SendSignal] fireSignalAtAntenna: antennaId={} tile={},{} depth={} channel={} actor={} sourceItem={}",
                    signalChannel,
                    tile.x,
                    tile.y,
                    depth,
                    signalChannel,
                    actor != null ? actor.getId() : "null",
                    sourceItem != null ? sourceItem.getId() : "null");
        }

        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .tile(tile)
                // Native delivery is queued; it does not nest a synchronous Call Stacks invocation.
                // The signal chain keeps its own delivery budget across these queue boundaries.
                .callStackDepth(this.nativeSignal ? 0 : depth)
                .signalChannel(signalChannel)
                .signalUserCount(signalUserCount)
                .signalFurniCount(signalSourceItems != null ? signalSourceItems.size() : (sourceItem != null ? 1 : 0))
                .signalSourceUsers(signalSourceUsers)
                .signalSourceItems(signalSourceItems)
                .signalPerUser(signalPerUser)
                .signalPerFurni(signalPerFurni)
                .signalPayloadExplicit(this.nativeSignal)
                .contextVariableScope(ctx.contextVariables().copy())
                .triggeredByEffect(true)
                .createdAtMs(createdAtMs);

        if (actor != null) {
            builder.actor(actor);
        }

        if (originActor != null) {
            builder.originActor(originActor);
        }

        if (sourceItem != null) {
            builder.sourceItem(sourceItem);
        }

        boolean result = dispatchSignalEvent(builder.build());

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                    "[SendSignal] handleEvent returned: {}, antenna={}, sourceItem={}",
                    result,
                    antenna.getId(),
                    sourceItem != null ? sourceItem.getId() : null);
        }

        return result;
    }

    private void pulseAntenna(Room room, HabboItem antenna) {
        if (room == null || antenna == null || antenna.getBaseItem() == null) return;
        if (antenna.getBaseItem().getStateCount() <= 1) return;

        antenna.setExtradata("1".equals(antenna.getExtradata()) ? "0" : "1");
        room.updateItemState(antenna);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);

        itemsSnapshot.removeIf(item -> item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);

        this.items.retainAll(itemsSnapshot);

        List<HabboItem> forwardSnapshot = new ArrayList<>(this.forwardItems);

        forwardSnapshot.removeIf(
                item -> item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);

        this.forwardItems.retainAll(forwardSnapshot);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());

        for (HabboItem item : itemsSnapshot) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");

        message.appendInt(2);
        message.appendInt(signalPerFurni ? 1 : 0);
        message.appendInt(signalPerUser ? 1 : 0);

        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();

            for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {

                if (!trigger.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(trigger.getBaseItem().getSpriteId());
                }
            }

            message.appendInt(invalidTriggers.size());

            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }

        message.appendInt(WIRED_EXTENDED_SETTINGS_MARKER);

        message.appendInt(2);
        message.appendInt(nativeSignal ? nativeAntennaSource : WiredSourceUtil.SOURCE_SELECTED);
        message.appendInt(
                this.furniForward == WiredSourceUtil.SOURCE_SELECTED ? SWF_FURNI_SOURCE_PICKS_2 : this.furniForward);

        message.appendInt(1);
        message.appendInt(this.userForward);

        message.appendInt(0);

        message.appendInt(forwardSnapshot.size());
        for (HabboItem item : forwardSnapshot) {
            message.appendInt(item.getId());
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (settings.isNativeVariableFormat()) return saveNativeSignal(settings);

        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newItems = new ArrayList<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];

            HabboItem it = room.getHabboItem(itemId);

            if (it == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            newItems.add(it);
        }

        for (HabboItem receiver : newItems) {
            if (!isAntennaItem(receiver)) {
                throw new WiredSaveException("Only antenna furni can be selected");
            }
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        int[] params = settings.getIntParams();

        if (params.length >= 6) {
            /*
             * Current Nitro custom UI:
             * [antennaSource, furniForward, userForward, splitFurni, splitUsers, channel]
             */
            int requestedAntennaSource = params[0];

            this.furniForward = normalizeSource(params[1]);

            this.userForward = normalizeSource(params[2]);

            this.signalPerFurni = params[3] == 1;

            this.signalPerUser = params[4] == 1;

            this.channel = params[5];

            this.antennaSource = requestedAntennaSource;
        } else {
            /*
             * Original SWF SendSignal.as:
             * intParams are [split_furni, split_users]. Source choices and
             * the second furni selection are transported by the common wired
             * source arrays and stuffIds2.
             */
            this.signalPerFurni = params.length > 0 && params[0] == 1;
            this.signalPerUser = params.length > 1 && params[1] == 1;
            this.channel = 0;
            this.antennaSource = ANTENNA_PICKED;

            int[] furniSources = settings.getFurniSourceTypes();
            int[] userSources = settings.getUserSourceTypes();

            this.furniForward = normalizeSwfFurniSource(
                    furniSources.length > 1
                            ? furniSources[1]
                            : (settings.getFurniIds2().length > 0
                                    ? SWF_FURNI_SOURCE_PICKS_2
                                    : WiredSourceUtil.SOURCE_TRIGGER));

            this.userForward =
                    normalizeSource(userSources.length > 0 ? userSources[0] : WiredSourceUtil.SOURCE_TRIGGER);
        }

        if (!newItems.isEmpty()) {
            this.antennaSource = newItems.get(0).getId();
        }

        List<HabboItem> newForwardItems = new ArrayList<>();

        if (this.furniForward == WiredSourceUtil.SOURCE_SELECTED && room != null) {
            newForwardItems = settings.getFurniIds2().length > 0
                    ? parseForwardItems(settings.getFurniIds2(), room)
                    : parseForwardItems(settings.getStringParam(), room);
        }

        if (newForwardItems.size() > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        this.items.clear();
        this.items.addAll(newItems);

        this.forwardItems.clear();

        if (this.furniForward == WiredSourceUtil.SOURCE_SELECTED) {
            this.forwardItems.addAll(newForwardItems);
        }

        this.nativeSignal = false;
        this.setDelay(delay);

        LOGGER.debug(
                "[SendSignal] saveData: antennaSource={}, furniForward={}, userForward={}, signalPerFurni={}, signalPerUser={}, channel={}, items={}, forwardItems={}",
                antennaSource,
                furniForward,
                userForward,
                signalPerFurni,
                signalPerUser,
                channel,
                items.size(),
                forwardItems.size());

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);

        List<HabboItem> forwardSnapshot = new ArrayList<>(this.forwardItems);

        JsonData data = new JsonData(
                this.getDelay(),
                itemsSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                forwardSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                antennaSource,
                furniForward,
                userForward,
                signalPerFurni,
                signalPerUser,
                channel);
        data.nativeSignal = nativeSignal;
        data.nativeAntennaSource = nativeAntennaSource;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {

        this.items = new LinkedHashSet<>();
        this.forwardItems = new LinkedHashSet<>();

        nativeSignal = false;
        nativeAntennaSource = WiredSourceUtil.SOURCE_SELECTED;
        String wiredData = set.getString("wired_data");

        JsonData data = WiredUtilityPayloadGuard.fromJson(wiredData, JsonData.class);

        if (data != null) {
            this.nativeSignal = data.nativeSignal;
            this.nativeAntennaSource = data.nativeAntennaSource;
            this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));

            this.antennaSource = data.antennaSource;

            this.furniForward = normalizeSource(data.furniForward);

            this.userForward = data.nativeSignal && WiredSourceUtil.isDefaultUserSource(data.userForward)
                    ? data.userForward
                    : normalizeSource(data.userForward);

            this.signalPerFurni = data.signalPerFurni;
            this.signalPerUser = data.signalPerUser;
            this.channel = data.channel;

            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }

            if (data.forwardItemIds != null) {
                for (Integer id : data.forwardItemIds) {
                    HabboItem item = room.getHabboItem(id);

                    if (item != null) {
                        this.forwardItems.add(item);
                    }
                }
            }

            if (this.antennaSource <= ANTENNA_TRIGGER && !this.items.isEmpty()) {

                HabboItem first = this.items.iterator().next();

                if (first != null) {
                    this.antennaSource = first.getId();
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.forwardItems.clear();

        this.antennaSource = ANTENNA_PICKED;
        this.furniForward = WiredSourceUtil.SOURCE_TRIGGER;
        this.userForward = WiredSourceUtil.SOURCE_TRIGGER;
        this.signalPerFurni = false;
        this.signalPerUser = false;
        this.channel = 0;
        this.nativeSignal = false;
        this.nativeAntennaSource = WiredSourceUtil.SOURCE_SELECTED;

        this.setDelay(0);
    }

    private boolean saveNativeSignal(WiredSettings settings) throws WiredSaveException {
        int[] flags = settings.getIntParams();
        int[] furniSources = settings.getFurniSourceTypes();
        int[] userSources = settings.getUserSourceTypes();
        if (flags.length != 2
                || (flags[0] != 0 && flags[0] != 1)
                || (flags[1] != 0 && flags[1] != 1)
                || furniSources.length != 2
                || userSources.length != 1
                || !isNativeFurniSource(furniSources[0], false)
                || !isNativeFurniSource(furniSources[1], true)
                || !WiredSourceUtil.isDefaultUserSource(userSources[0]))
            throw new WiredSaveException("Invalid signal fields");
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(getRoomId());
        if (room == null) throw new WiredSaveException("Room not found");
        int limit = Emulator.getConfig().getInt("hotel.wired.furni.selection.count");
        if (settings.getFurniIds().length > limit || settings.getFurniIds2().length > limit)
            throw new WiredSaveException("Too many furni selected");
        if (settings.getDelay() < 0
                || settings.getDelay() > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Invalid delay");
        Set<HabboItem> antennas = new LinkedHashSet<>();
        Set<HabboItem> forwarded = new LinkedHashSet<>();
        if (furniSources[0] == 100)
            for (int id : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(id);
                if (!isAntennaItem(item)) throw new WiredSaveException("Only antenna furni can be selected");
                antennas.add(item);
            }
        if (furniSources[1] == 101)
            for (int id : settings.getFurniIds2()) {
                HabboItem item = room.getHabboItem(id);
                if (item == null) throw new WiredSaveException("Forwarded furni not found");
                forwarded.add(item);
            }
        this.items.clear();
        this.items.addAll(antennas);
        this.forwardItems.clear();
        this.forwardItems.addAll(forwarded);
        this.nativeAntennaSource = furniSources[0];
        this.antennaSource =
                antennas.isEmpty() ? ANTENNA_PICKED : antennas.iterator().next().getId();
        this.furniForward = furniSources[1] == 101 ? 100 : furniSources[1];
        this.userForward = userSources[0];
        this.signalPerFurni = flags[0] == 1;
        this.signalPerUser = flags[1] == 1;
        this.channel = 0;
        this.nativeSignal = true;
        this.setDelay(settings.getDelay());
        return true;
    }

    private static boolean isNativeFurniSource(int source, boolean secondary) {
        return source == 0 || source == 200 || source == 201 || source == (secondary ? 101 : 100);
    }

    private int normalizeSource(int source) {
        if (source == 1) {
            return WiredSourceUtil.SOURCE_TRIGGER;
        }

        if (source == WiredSourceUtil.SOURCE_TRIGGER
                || source == WiredSourceUtil.SOURCE_SELECTED
                || source == WiredSourceUtil.SOURCE_SELECTOR
                || source == WiredSourceUtil.SOURCE_SIGNAL) {
            return source;
        }

        return WiredSourceUtil.SOURCE_TRIGGER;
    }

    private int normalizeSwfFurniSource(int source) {
        if (source == SWF_FURNI_SOURCE_PICKS_2) {
            return WiredSourceUtil.SOURCE_SELECTED;
        }

        return normalizeSource(source);
    }

    private List<HabboItem> parseForwardItems(int[] itemIds, Room room) {

        List<HabboItem> results = new ArrayList<>();

        if (itemIds == null || itemIds.length == 0 || room == null) {
            return results;
        }

        Set<Integer> seen = new LinkedHashSet<>();

        for (int itemId : itemIds) {
            if (itemId <= 0 || !seen.add(itemId)) {
                continue;
            }

            HabboItem item = room.getHabboItem(itemId);

            if (item != null) {
                results.add(item);
            }
        }

        return results;
    }

    private List<HabboItem> parseForwardItems(String data, Room room) throws WiredSaveException {

        List<HabboItem> results = new ArrayList<>();

        if (data == null || data.trim().isEmpty() || room == null) {
            return results;
        }

        Set<Integer> seen = new HashSet<>();

        String[] parts = data.split(FORWARD_ITEM_SPLIT_REGEX);

        for (String part : parts) {
            if (part == null) continue;

            String trimmed = part.trim();

            if (trimmed.isEmpty()) continue;

            int itemId;

            try {
                itemId = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                continue;
            }

            if (itemId <= 0 || !seen.add(itemId)) {
                continue;
            }

            HabboItem item = room.getHabboItem(itemId);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            results.add(item);
        }

        return results;
    }

    private boolean isAntennaItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interaction = item.getBaseItem().getInteractionType().getName();

        if (interaction == null) {
            return false;
        }

        String normalized = interaction.toLowerCase();

        return normalized.equals(ANTENNA_INTERACTION);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    public int getChannel() {
        return channel;
    }

    public boolean hasPickedItem(int itemId) {
        try {
            for (HabboItem item : new ArrayList<>(this.items)) {

                if (item != null && item.getId() == itemId) {
                    return true;
                }
            }
        } catch (Exception e) {
            WiredCompatibilityDiagnostics.record(
                    WiredCompatibilityDiagnostics.FailurePoint.EFFECT_SEND_SIGNAL_SELECTION,
                    this.getRoomId(),
                    this.getId(),
                    e);
        }

        return false;
    }

    public boolean unlinkAntenna(int antennaItemId) {
        if (antennaItemId <= 0) {
            return false;
        }

        boolean changed = false;

        Iterator<HabboItem> iterator = this.items.iterator();

        while (iterator.hasNext()) {
            HabboItem item = iterator.next();

            if (item == null || item.getId() != antennaItemId) {
                continue;
            }

            iterator.remove();
            changed = true;
        }

        if (this.antennaSource == antennaItemId) {
            if (!this.items.isEmpty()) {
                HabboItem firstItem = this.items.iterator().next();

                this.antennaSource = firstItem != null ? firstItem.getId() : ANTENNA_PICKED;
            } else {
                this.antennaSource = ANTENNA_PICKED;
            }

            changed = true;
        }

        if (changed) {
            this.needsUpdate(true);
        }

        return changed;
    }

    @Override
    protected long requiredCooldown() {
        /*
         * Signal chains are intentionally allowed to feed back into
         * the same sender during one deferred WIRED publication cycle.
         */
        return 0L;
    }

    protected boolean dispatchSignalEvent(WiredEvent event) {
        return WiredManager.dispatchEffectTriggeredEvent(event);
    }

    static boolean hasReachedMaxSignalDepth(int currentDepth) {
        return MAX_SIGNAL_DEPTH > 0 && currentDepth >= MAX_SIGNAL_DEPTH;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        List<Integer> forwardItemIds;
        int antennaSource;
        int furniForward;
        int userForward;
        boolean signalPerFurni;
        boolean signalPerUser;
        int channel;
        boolean nativeSignal;
        int nativeAntennaSource;

        public JsonData(
                int delay,
                List<Integer> itemIds,
                List<Integer> forwardItemIds,
                int antennaSource,
                int furniForward,
                int userForward,
                boolean signalPerFurni,
                boolean signalPerUser,
                int channel) {

            this.delay = delay;
            this.itemIds = itemIds;
            this.forwardItemIds = forwardItemIds;
            this.antennaSource = antennaSource;
            this.furniForward = furniForward;
            this.userForward = userForward;
            this.signalPerFurni = signalPerFurni;
            this.signalPerUser = signalPerUser;
            this.channel = channel;
        }
    }
}

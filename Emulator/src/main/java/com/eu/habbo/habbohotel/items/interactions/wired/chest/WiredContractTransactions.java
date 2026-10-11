package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContractPayment;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredCustomContract;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectInitTransaction;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableScope;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.outgoing.rooms.items.WiredContractTransactionComposer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A user must accept and confirm a frozen offer; start/cancel never charge the wallet. */
public final class WiredContractTransactions {

    private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();

    private WiredContractTransactions() {}

    private static final class InitConfig {

        int version = 1, userSource;

        WiredChestConfiguration sources = new WiredChestConfiguration();

        List<Integer> contractIds = new ArrayList<>();
    }

    public static final class Offer {

        public String token, receiveText, layoutType;
        public int roomId, contractId, multiplier = 1, stage, seconds;

        public boolean freePayment;

        public List<List<ContractTerm>> paymentOptions = new ArrayList<>();

        public List<ContractTerm> rewards = new ArrayList<>();
    }

    private static final class Pending {

        final Habbo user;
        final Room room;
        final InteractionWiredContract contract;

        final List<InteractionWiredChest> chests;
        final Offer offer;
        final String definition;

        final WiredContextVariableScope variables;

        int option, donation, currency = -1;
        java.util.concurrent.ScheduledFuture<?> timeoutTask;

        Pending(
                Habbo user,
                Room room,
                InteractionWiredContract contract,
                List<InteractionWiredChest> chests,
                Offer offer,
                WiredContextVariableScope variables) {

            this.user = user;
            this.room = room;
            this.contract = contract;
            this.chests = chests;
            this.offer = offer;
            this.variables = variables.copy();
            this.definition = contract.getWiredData();
        }
    }

    public static void start(
            WiredContext ctx,
            WiredEffectInitTransaction effect,
            List<Integer> selected,
            int[] params,
            String configText) {

        if (ctx == null || ctx.room() == null) return;

        InitConfig config = new InitConfig();

        if (configText != null && configText.startsWith("{")) {

            try {
                config = WiredManager.getGson().fromJson(configText, InitConfig.class);
            } catch (RuntimeException ignored) {
                return;
            }
        }

        if (config == null || config.sources == null || config.contractIds == null) return;

        ctx.setIncludeWiredSelectorItems(true);

        List<InteractionWiredChest> chests = config.sources.items(ctx, selected, config.sources.chestSource).stream()
                .filter(InteractionWiredChest.class::isInstance)
                .map(InteractionWiredChest.class::cast)
                .toList();

        List<InteractionWiredContract> contracts = new ArrayList<>();

        List<Integer> ids = config.contractIds.isEmpty() ? selected : config.contractIds;

        for (var item : config.sources.items(ctx, ids, config.sources.typeSource))
            if (item instanceof InteractionWiredContract contract) contracts.add(contract);

        for (var item : ctx.room().getItemsAt(effect.getX(), effect.getY()))
            if (item instanceof InteractionWiredCustomContract custom && !contracts.contains(custom))
                contracts.add(custom);

        // A missing/unplaced contract is a configuration failure, never an unconditional success.

        for (var unit : WiredSourceUtil.resolveUsers(ctx, config.userSource)) {

            Habbo user = ctx.room().getHabbo(unit);
            if (user == null || user.getClient() == null) continue;

            if (contracts.size() != 1) {
                ChestNotifications.send(
                        user, "rejected", "Seleziona un solo contratto valido per avviare la transazione.");
                continue;
            }

            var contract = contracts.get(0);

            var available = new ArrayList<>(chests);

            if (available.isEmpty() && contract.resolveLinkedChest(ctx.room()) != null)
                available.add(contract.resolveLinkedChest(ctx.room()));

            Offer offer = buildOffer(ctx, contract);
            if (offer == null) {
                fail(ctx.room(), user, contract, ctx.contextVariables(), 1);
                continue;
            }

            Integer factor = params[0] == 0 ? 1 : config.sources.amount(ctx, params[1]);

            if (factor == null || factor < 1 || factor > 500 || available.isEmpty()) {
                fail(ctx.room(), user, contract, ctx.contextVariables(), 1);
                continue;
            }

            offer.multiplier = factor;

            if (params[0] == 2) {

                boolean found = false;

                for (int n = factor; n > 0 && !found; n--) {

                    offer.multiplier = n;

                    for (int choice = 0; choice < Math.max(1, offer.paymentOptions.size()); choice++)
                        if (WiredTransactionExecutor.validateEntries(user, entries(offer, available, choice, 0, -1))) {
                            found = true;
                            break;
                        }
                }

                if (!found) {
                    fail(ctx.room(), user, contract, ctx.contextVariables(), 2);
                    continue;
                }
            }

            synchronized (user) {
                var previous = PENDING.remove(user.getHabboInfo().getId());

                if (previous != null) close(previous, false, 3);

                offer.token = UUID.randomUUID().toString();
                offer.roomId = ctx.room().getId();
                offer.contractId = contract.getId();

                offer.seconds = params[4] == 1 ? params[5] : 0;

                var pending = new Pending(user, ctx.room(), contract, available, offer, ctx.contextVariables());

                if (!offer.freePayment && offer.paymentOptions.isEmpty()) {

                    boolean success =
                            WiredTransactionExecutor.executeEntries(user, entries(offer, available, 0, 0, -1));

                    close(pending, success, success ? 0 : 2);
                    continue;
                }

                if (user.getRoomUnit().hasStatus(RoomUnitStatus.TRADING)) {
                    close(pending, false, 4);
                    continue;
                }

                PENDING.put(user.getHabboInfo().getId(), pending);
                send(pending);

                int userId = user.getHabboInfo().getId();

                if (offer.seconds > 0)
                    pending.timeoutTask =
                            Emulator.getThreading().run(() -> expire(userId, pending), offer.seconds * 1000);
            }
        }
    }

    private static Offer buildOffer(WiredContext ctx, InteractionWiredContract contract) {

        Offer offer = new Offer();
        var config = contract.getConfiguration();
        offer.receiveText = config.receiveText;
        offer.layoutType = config.layoutType;

        if (contract instanceof InteractionWiredCustomContract && config.customNative) {

            if (config.paymentEnabled
                    && !customTerms(
                            ctx, config.payment, config.paymentIds, config.paymentKind, config.paymentAmount, 0, offer))
                return null;

            if (config.rewardEnabled
                    && !customTerms(
                            ctx, config.reward, config.rewardIds, config.rewardKind, config.rewardAmount, 1, offer))
                return null;

        } else {

            var terms = contract.getAssetTerms();

            Map<Integer, List<ContractTerm>> options = new TreeMap<>();

            for (int index = 0; index < terms.size(); index++) {

                var term = terms.get(index);

                if (term.direction == 1) offer.rewards.add(term);
                else {

                    int group = index < config.paymentRules.size() ? config.paymentRules.get(index) : 0;

                    options.computeIfAbsent(Math.max(0, Math.min(2, group)), key -> new ArrayList<>())
                            .add(term);
                }
            }

            offer.paymentOptions.addAll(options.values());

            offer.freePayment = contract instanceof InteractionWiredContractPayment && config.paymentMode == 0;

            if (offer.freePayment) offer.paymentOptions.clear();
        }

        if (!offer.freePayment && offer.paymentOptions.isEmpty() && offer.rewards.isEmpty()) return null;

        for (var term : offer.rewards) setSprite(term);
        for (var option : offer.paymentOptions) for (var term : option) setSprite(term);
        return offer;
    }

    private static void setSprite(ContractTerm term) {
        if (term.isFurni()) {
            var base = Emulator.getGameEnvironment().getItemManager().getItem(term.baseItemId);
            if (base != null) term.spriteId = base.getSpriteId();
        }
    }

    private static boolean customTerms(
            WiredContext ctx,
            WiredChestConfiguration config,
            List<Integer> ids,
            int kind,
            int constant,
            int direction,
            Offer offer) {

        Integer amount = config.amount(ctx, constant);
        if (amount == null || amount <= 0 || amount > 100000) return false;

        List<ContractTerm> terms = new ArrayList<>();

        if (kind == 0) terms.add(ContractTerm.currency(direction, config.currencyType, amount));
        else {

            for (var item : config.items(ctx, ids == null ? List.of() : ids, config.typeSource)) {

                if (!item.getBaseItem().allowTrade()) return false;

                var term = ContractTerm.furni(
                        direction,
                        item.getBaseItem().getType() == FurnitureType.WALL,
                        item.getBaseItem().getId(),
                        "",
                        amount);

                if (terms.stream().noneMatch(t -> t.baseItemId == term.baseItemId && t.wallItem == term.wallItem))
                    terms.add(term);
            }

            if (terms.isEmpty() || terms.size() > 5) return false;
        }

        if (direction == 0) offer.paymentOptions.add(terms);
        else offer.rewards.addAll(terms);

        return true;
    }

    private static List<WiredTransactionExecutor.Entry> entries(
            Offer offer, List<InteractionWiredChest> chests, int choice, int donation, int currency) {

        List<ContractTerm> terms = new ArrayList<>(offer.rewards);

        if (offer.freePayment) {
            if (donation <= 0 || (currency != -1 && currency != 0 && currency != 5)) return List.of();
            terms.add(ContractTerm.currency(0, currency, donation));
        } else if (!offer.paymentOptions.isEmpty()) {
            if (choice < 0 || choice >= offer.paymentOptions.size()) return List.of();
            terms.addAll(offer.paymentOptions.get(choice));
        }

        List<WiredTransactionExecutor.Entry> result = new ArrayList<>();
        Map<String, Long> reserved = new HashMap<>();
        Map<InteractionWiredChest, Long> space = new HashMap<>();
        for (var term : terms) {
            long remaining = (long) term.amount * (offer.freePayment ? 1 : offer.multiplier);
            if (remaining <= 0 || remaining > Integer.MAX_VALUE) return List.of();
            for (var chest : chests) {
                if (term.isCurrency()
                        ? !(chest instanceof InteractionWiredChestCurrency)
                        : !(chest instanceof InteractionWiredChestFurni)) continue;
                var contents = chest.getContents();
                if (contents.isLocked()) continue;
                String key = chest.getId() + ":" + term.kind + ":"
                        + (term.isCurrency() ? term.currencyType : term.baseItemId) + ":" + term.wallItem + ":"
                        + term.legacyPosterId;
                long available;
                if (term.direction == 0) {
                    long used =
                            term.isCurrency() ? contents.total(ChestStorage.KIND_CURRENCY) : contents.furniItemCount();
                    available = Math.max(0, contents.getCapacity() - used - space.getOrDefault(chest, 0L));
                } else {
                    long stock = term.isCurrency()
                            ? contents.count(ChestStorage.KIND_CURRENCY, term.currencyType)
                            : contents.furniItems().stream()
                                    .filter(item -> item.baseItemId == term.baseItemId
                                            && item.wallItem == term.wallItem
                                            && (term.legacyPosterId == null
                                                    || term.legacyPosterId.isEmpty()
                                                    || term.legacyPosterId.equals(item.extradata)))
                                    .count();
                    available = Math.max(0, stock - reserved.getOrDefault(key, 0L));
                }
                int amount = (int) Math.min(remaining, available);
                if (amount <= 0) continue;
                result.add(new WiredTransactionExecutor.Entry(
                        chest,
                        new ContractTerm(
                                term.direction,
                                term.kind,
                                term.currencyType,
                                term.wallItem,
                                term.baseItemId,
                                term.legacyPosterId,
                                amount)));
                if (term.direction == 1) reserved.merge(key, (long) amount, Long::sum);
                space.merge(chest, term.direction == 0 ? (long) amount : -(long) amount, Long::sum);
                remaining -= amount;
                if (remaining == 0) break;
            }
            if (remaining > 0) return List.of();
        }
        return result;
    }

    public static void respond(Habbo user, String token, int action, int choice, int donation, int currency) {

        if (user == null) return;

        synchronized (user) {
            var pending = PENDING.get(user.getHabboInfo().getId());

            if (pending == null || !pending.offer.token.equals(token)) return;

            if (action == 0) {
                if (PENDING.remove(user.getHabboInfo().getId(), pending)) close(pending, false, 3);
                return;
            }

            if (user.getHabboInfo().getCurrentRoom() != pending.room
                    || pending.room.getHabboItem(pending.contract.getId()) != pending.contract
                    || !pending.definition.equals(pending.contract.getWiredData())
                    || pending.chests.stream().anyMatch(chest -> pending.room.getHabboItem(chest.getId()) != chest)
                    || user.getRoomUnit().hasStatus(RoomUnitStatus.TRADING)) {

                PENDING.remove(user.getHabboInfo().getId(), pending);
                close(pending, false, 4);
                return;
            }

            if (action == 1 && pending.offer.stage == 0) {

                var changes = entries(pending.offer, pending.chests, choice, donation, currency);

                if (!WiredTransactionExecutor.validateEntries(user, changes)) {
                    PENDING.remove(user.getHabboInfo().getId(), pending);
                    close(pending, false, 2);
                    return;
                }

                pending.option = choice;
                pending.donation = donation;
                pending.currency = currency;
                pending.offer.stage = 1;
                send(pending);
                return;
            }

            if (action != 2
                    || pending.offer.stage != 1
                    || !PENDING.remove(user.getHabboInfo().getId(), pending)) return;

            boolean success = WiredTransactionExecutor.executeEntries(
                    user, entries(pending.offer, pending.chests, pending.option, pending.donation, pending.currency));

            close(pending, success, success ? 0 : 2);
        }
    }

    public static void cancel(WiredContext ctx, int mode, List<Integer> ids) {
        cancel(ctx, mode, ids, new WiredTransactionSources());
    }

    public static void cancel(WiredContext ctx, int mode, List<Integer> ids, WiredTransactionSources sources) {
        var resolved = mode == 1
                ? List.<Integer>of()
                : sources.contracts(ctx, ids).stream().map(item -> item.getId()).toList();

        for (var unit : WiredSourceUtil.resolveUsers(ctx, sources.userSource)) {

            var user = ctx.room().getHabbo(unit);
            if (user == null) continue;

            synchronized (user) {
                var pending = PENDING.get(user.getHabboInfo().getId());

                if (pending != null
                        && pending.room == ctx.room()
                        && (mode == 1 || resolved.contains(pending.contract.getId()))
                        && PENDING.remove(user.getHabboInfo().getId(), pending)) close(pending, false, 3);
            }
        }
    }

    public static void leaveRoom(Habbo user, Room room) {
        if (user == null) return;
        synchronized (user) {
            var pending = PENDING.get(user.getHabboInfo().getId());
            if (pending != null
                    && pending.room == room
                    && PENDING.remove(user.getHabboInfo().getId(), pending)) close(pending, false, 3);
        }
    }

    private static void expire(int id, Pending pending) {

        synchronized (pending.user) {
            if (PENDING.remove(id, pending)) close(pending, false, 5);
        }
    }

    private static void send(Pending pending) {
        if (pending.user.getClient() != null)
            pending.user
                    .getClient()
                    .sendResponse(new WiredContractTransactionComposer(
                            WiredManager.getGson().toJson(pending.offer)));
    }

    private static void close(Pending pending, boolean success, int reason) {

        if (pending.timeoutTask != null) pending.timeoutTask.cancel(false);
        pending.offer.stage = success ? 2 : 3;
        send(pending);

        if (success) {

            ChestNotifications.send(pending.user, "success", "Transazione completata.");

            var config = pending.contract.getConfiguration();

            if (!pending.offer.rewards.isEmpty()) {
                var notification = new WiredChestConfiguration();
                notification.popup = config.popup;
                notification.showByDefault = config.showByDefault;
                notification.notifyReward(
                        pending.user,
                        pending.offer.rewards.stream().mapToInt(t -> t.amount).sum());
            }

        } else
            ChestNotifications.send(
                    pending.user,
                    "rejected",
                    reason == 5
                            ? "La transazione è scaduta."
                            : reason == 3
                                    ? "Transazione annullata."
                                    : "Impossibile completare la transazione: controlla saldo, contenuto e capienza degli scrigni.");

        failOrComplete(pending.room, pending.user, pending.contract, pending.variables, success, reason);
    }

    private static void fail(
            Room room, Habbo user, InteractionWiredContract contract, WiredContextVariableScope variables, int reason) {
        failOrComplete(room, user, contract, variables, false, reason);
    }

    private static void failOrComplete(
            Room room,
            Habbo user,
            InteractionWiredContract contract,
            WiredContextVariableScope variables,
            boolean success,
            int reason) {

        var builder = WiredEvent.builder(
                        success ? WiredEvent.Type.TRANSACTION_COMPLETE : WiredEvent.Type.TRANSACTION_FAIL, room)
                .sourceItem(contract)
                .actor(user.getRoomUnit())
                .actionParameter(reason)
                .contextVariableScope(variables.copy());

        var tile = room.getLayout() == null ? null : room.getLayout().getTile(contract.getX(), contract.getY());
        if (tile != null) builder.tile(tile);

        WiredManager.dispatchEffectTriggeredEvent(builder.build());
    }
}

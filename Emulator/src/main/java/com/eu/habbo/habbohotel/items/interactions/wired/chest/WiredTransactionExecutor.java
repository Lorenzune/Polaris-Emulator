package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** All requirements are reserved cumulatively; no reward is minted without chest stock. */
public final class WiredTransactionExecutor {
    private WiredTransactionExecutor() {}

    public record Entry(InteractionWiredChest chest, ContractTerm term) {}

    public static boolean execute(Habbo user, Room room, List<InteractionWiredContract> contracts) {
        List<Entry> entries = new ArrayList<>();
        for (var contract : contracts)
            for (var term : contract.getTerms()) entries.add(new Entry(contract.resolveLinkedChest(room), term));
        return executeEntries(user, entries);
    }

    public static boolean executeEntries(Habbo user, List<Entry> entries) {
        return executeEntries(user, entries, false);
    }

    public static boolean validateEntries(Habbo user, List<Entry> entries) {
        return executeEntries(user, entries, true);
    }

    private static boolean executeEntries(Habbo user, List<Entry> entries, boolean dryRun) {
        if (user == null || user.getClient() == null || entries.isEmpty()) return false;
        synchronized (user) {
            var chests = entries.stream()
                    .map(Entry::chest)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.comparingInt(InteractionWiredChest::getId))
                    .toList();
            return lockChests(user, entries, chests, 0, dryRun);
        }
    }

    private static boolean lockChests(
            Habbo user, List<Entry> entries, List<InteractionWiredChest> chests, int index, boolean dryRun) {
        if (index == chests.size()) {
            synchronized (user.getInventory().getItemsComponent().getItems()) {
                return apply(user, entries, dryRun);
            }
        }
        synchronized (chests.get(index).getContents()) {
            return lockChests(user, entries, chests, index + 1, dryRun);
        }
    }

    private record FurniMove(Entry entry, HabboItem item, ChestFurniStoredItem stored) {}

    private static boolean apply(Habbo user, List<Entry> entries, boolean dryRun) {
        Map<Integer, Long> pay = new HashMap<>(), receive = new HashMap<>();
        Map<InteractionWiredChest, Map<Integer, Long>> take = new HashMap<>();
        Map<InteractionWiredChest, Long> space = new HashMap<>();
        Set<Integer> reservedInventory = new HashSet<>();
        Set<ChestFurniStoredItem> reservedChest = Collections.newSetFromMap(new IdentityHashMap<>());
        List<FurniMove> deposits = new ArrayList<>(), rewards = new ArrayList<>();
        for (Entry entry : entries) {
            var chest = entry.chest();
            var term = entry.term();
            if (chest == null
                    || term == null
                    || term.amount <= 0
                    || chest.getContents().isLocked()) return false;
            boolean payment = term.direction == ContractTerm.DIR_PAY;
            space.merge(chest, payment ? (long) term.amount : -(long) term.amount, Long::sum);
            if (term.isCurrency()) {
                if (!(chest instanceof InteractionWiredChestCurrency) || !currency(term.currencyType)) return false;
                (payment ? pay : receive).merge(term.currencyType, (long) term.amount, Long::sum);
                if (!payment)
                    take.computeIfAbsent(chest, c -> new HashMap<>())
                            .merge(term.currencyType, (long) term.amount, Long::sum);
            } else {
                if (!(chest instanceof InteractionWiredChestFurni) || term.baseItemId <= 0) return false;
                int found = 0;
                if (payment) {
                    for (var item : user.getInventory().getItemsComponent().getItemsAsValueCollection()) {
                        if (found == term.amount) break;
                        if (item.getBaseItem() == null
                                || item.getRoomId() != 0
                                || item.getBaseItem().getId() != term.baseItemId
                                || !item.getBaseItem().allowTrade()
                                || (item.getBaseItem().getType() == FurnitureType.WALL) != term.wallItem
                                || (!poster(term).isEmpty() && !poster(term).equals(item.getExtradata()))
                                || !reservedInventory.add(item.getId())) continue;
                        deposits.add(
                                new FurniMove(entry, item, ChestFurniStoredItem.fromHabboItem(item, item.getId())));
                        found++;
                    }
                } else {
                    for (var stored : chest.getContents().furniItems()) {
                        if (found == term.amount) break;
                        if (stored.baseItemId != term.baseItemId
                                || stored.wallItem != term.wallItem
                                || (!poster(term).isEmpty() && !poster(term).equals(stored.extradata))
                                || !reservedChest.add(stored)) continue;
                        rewards.add(new FurniMove(entry, null, stored));
                        found++;
                    }
                }
                if (found != term.amount) return false;
            }
        }
        for (var amount : pay.entrySet())
            if (amount.getValue() > ChestWiredCurrencyUtil.getBalance(user, amount.getKey())) return false;
        for (var amount : receive.entrySet())
            if ((long) ChestWiredCurrencyUtil.getBalance(user, amount.getKey())
                            - pay.getOrDefault(amount.getKey(), 0L)
                            + amount.getValue()
                    > Integer.MAX_VALUE) return false;
        for (var chest : take.entrySet())
            for (var amount : chest.getValue().entrySet())
                if (amount.getValue() > chest.getKey().getContents().count(ChestStorage.KIND_CURRENCY, amount.getKey()))
                    return false;
        for (var delta : space.entrySet()) {
            var contents = delta.getKey().getContents();
            long used = delta.getKey() instanceof InteractionWiredChestCurrency
                    ? contents.total(ChestStorage.KIND_CURRENCY)
                    : contents.furniItemCount();
            if (used + delta.getValue() > contents.getCapacity()) return false;
        }
        if (dryRun) return true;
        // Prepare every reward instance before charging; creation failures cannot consume payment.
        List<HabboItem> created = new ArrayList<>(), removed = new ArrayList<>();
        try {
            for (var reward : rewards) {
                var base = Emulator.getGameEnvironment().getItemManager().getItem(reward.stored().baseItemId);
                if (base == null) throw new IllegalStateException("Missing reward furniture");
                var stored = reward.stored();
                var item = Emulator.getGameEnvironment()
                        .getItemManager()
                        .createItem(
                                user.getHabboInfo().getId(),
                                base,
                                stored.limitedStack,
                                stored.limitedSells,
                                stored.extradata);
                if (item == null) throw new IllegalStateException("Reward creation failed");
                created.add(item);
            }
            for (var deposit : deposits) {
                user.getInventory().getItemsComponent().removeHabboItem(deposit.item());
                if (user.getInventory()
                                .getItemsComponent()
                                .getHabboItem(deposit.item().getId())
                        != null) throw new IllegalStateException("Payment removal cancelled");
                removed.add(deposit.item());
            }
            if (!ChestWiredCurrencyUtil.exchange(user, pay, receive))
                throw new IllegalStateException("Wallet exchange failed");
        } catch (RuntimeException failure) {
            for (var item : removed) user.getInventory().getItemsComponent().addItem(item);
            if (!created.isEmpty()) Emulator.getThreading().runPersistence(new QueryDeleteHabboItems(created));
            return false;
        }
        for (var reward : rewards)
            reward.entry()
                    .chest()
                    .getContents()
                    .removeFurniByType(
                            reward.stored().wallItem, reward.stored().baseItemId, reward.stored().extradata, 1);
        for (var deposit : deposits) deposit.entry().chest().getContents().addFurniItem(deposit.stored());
        for (var entry : entries) {
            var term = entry.term();
            var contents = entry.chest().getContents();
            boolean payment = term.direction == ContractTerm.DIR_PAY;
            if (term.isCurrency()) {
                if (payment) contents.add(ChestStorage.KIND_CURRENCY, term.currencyType, term.amount);
                else contents.take(ChestStorage.KIND_CURRENCY, term.currencyType, term.amount);
            }
            var log = new ChestStorage.LogEntry(
                    payment ? "wired_deposit" : "wired_withdraw",
                    System.currentTimeMillis(),
                    user.getHabboInfo().getUsername(),
                    payment ? 0 : term.amount,
                    payment ? term.amount : 0);
            if (term.isCurrency()) log.withCurrency(term.currencyType);
            else
                log.setFurniItems((payment ? deposits : rewards)
                        .stream().filter(move -> move.entry() == entry).map(FurniMove::stored));
            contents.addLog(log);
        }
        for (var item : created) {
            user.getInventory().getItemsComponent().addItem(item);
            user.getClient().sendResponse(new AddHabboItemComposer(item));
        }
        for (var item : removed) user.getClient().sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
        if (!removed.isEmpty()) Emulator.getThreading().runPersistence(new QueryDeleteHabboItems(removed));
        if (!removed.isEmpty() || !created.isEmpty()) user.getClient().sendResponse(new InventoryRefreshComposer());
        entries.stream().map(Entry::chest).distinct().forEach(InteractionWiredChest::persistContents);
        return true;
    }

    private static boolean currency(int type) {
        return type == -1 || type == 0 || type == 5;
    }

    private static String poster(ContractTerm term) {
        return term.legacyPosterId == null ? "" : term.legacyPosterId;
    }
}

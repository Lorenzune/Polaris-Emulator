package com.eu.habbo.messages.incoming.catalog.recycler;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.RecyclerCompleteComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public class RecycleEvent extends MessageHandler {
    private static final long RECYCLER_PROCESSING_DELAY_MS = 10_000L;
    private static final Set<Integer> PENDING_RECYCLER_USERS = ConcurrentHashMap.newKeySet();

    @Override
    public void handle() throws Exception {
        if (ShutdownEmulator.timestamp > 0) {
            this.client.sendResponse(new HotelWillCloseInMinutesComposer(
                    (ShutdownEmulator.timestamp - Emulator.getIntUnixTimestamp()) / 60));
            return;
        }

        if (Emulator.getGameEnvironment().getCatalogManager().ecotronItem != null && ItemManager.RECYCLER_ENABLED) {
            Set<HabboItem> items = new HashSet<>();

            int count = this.packet.readInt();
            if (count != Emulator.getConfig().getInt("recycler.value", 8)) return;

            for (int i = 0; i < count; i++) {
                HabboItem item = this.client
                        .getHabbo()
                        .getInventory()
                        .getItemsComponent()
                        .getHabboItem(this.packet.readInt());

                if (item == null) return;

                if (item.getBaseItem().allowRecyle()) {
                    items.add(item);
                }
            }

            if (items.size() != count) {
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            int recycledPrizeCount = 0;
            for (HabboItem item : items) {
                if (Emulator.getGameEnvironment().getCatalogManager()
                                .getRecyclerPrizeRarity(item.getBaseItem().getId())
                        > 0) {
                    recycledPrizeCount++;
                }
            }

            // Compute the reward BEFORE consuming the inputs. Previously the
            // inputs were deleted first, so a null reward (misconfiguration)
            // permanently destroyed the 8 furni with nothing in return.
            Item recyclerPrize = Emulator.getGameEnvironment()
                    .getCatalogManager()
                    .getRandomRecyclerPrize();
            if (recyclerPrize == null) {
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }
            Habbo habbo = this.client.getHabbo();
            int habboId = habbo.getHabboInfo().getId();

            // This lock is server-side: the client countdown alone must not
            // allow duplicate recycle packets during the 10-second animation.
            if (!PENDING_RECYCLER_USERS.add(habboId)) {
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            int ducketCost = Emulator.getGameEnvironment().getCatalogManager().getRecyclerDucketCost();
            String recyclerOperationId = EconomyOperationId.create("catalog.recycler:" + habboId);
            if (ducketCost > 0 && !habbo.tryTakePoints(
                    0, ducketCost, "catalog.recycler", recyclerOperationId, habboId)) {
                PENDING_RECYCLER_USERS.remove(habboId);
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            int recyclerPrizeId = recyclerPrize.getId();
            int recycledItemCount = items.size();
            int recycledPrizeItemCount = recycledPrizeCount;
            ScheduledFuture<?> rewardDelivery = Emulator.getThreading().run(
                    () -> deliverReward(habbo, recyclerPrizeId, recycledItemCount, recycledPrizeItemCount),
                    RECYCLER_PROCESSING_DELAY_MS);

            if (rewardDelivery == null) {
                PENDING_RECYCLER_USERS.remove(habboId);
                if (ducketCost > 0) habbo.givePoints(0, ducketCost, "catalog.recycler.refund");
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return;
            }

            for (HabboItem item : items) {
                habbo.getInventory().getItemsComponent().removeHabboItem(item);
                this.client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
                Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(item.getId()));
            }
        } else {
            this.client.sendResponse(new RecyclerCompleteComposer(RecyclerCompleteComposer.RECYCLING_CLOSED));
        }
    }

    private static void deliverReward(Habbo habbo, int recyclerPrizeId, int recycledItemCount, int recycledPrizeCount) {
        int habboId = habbo.getHabboInfo().getId();

        try {
            HabboItem reward = Emulator.getGameEnvironment()
                    .getItemManager()
                    .handleRecycle(habbo, Integer.toString(recyclerPrizeId));

            if (reward == null) {
                if (habbo.getClient() != null) {
                    habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                    habbo.getClient().sendResponse(new RecyclerCompleteComposer(RecyclerCompleteComposer.RECYCLING_CLOSED));
                }
                return;
            }

            habbo.getInventory().getItemsComponent().addItem(reward);

            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new AddHabboItemComposer(reward));
                habbo.getClient().sendResponse(new RecyclerCompleteComposer(RecyclerCompleteComposer.RECYCLING_COMPLETE));
                habbo.getClient().sendResponse(new InventoryRefreshComposer());
            }

            AchievementInteractions.progress(habbo, "RecycledItems", recycledItemCount);
            if (recycledPrizeCount > 0) {
                AchievementInteractions.progress(
                        habbo, "RecycleEcoprizes", recycledPrizeCount);
            }
            int rewardRarity = Emulator.getGameEnvironment().getCatalogManager()
                    .getRecyclerPrizeRarity(recyclerPrizeId);
            if (rewardRarity >= 5) AchievementInteractions.progress(habbo, "RecyclerGrandPrize");
            else if (rewardRarity >= 3) AchievementInteractions.progress(habbo, "RecyclerRarePrize");
        } finally {
            PENDING_RECYCLER_USERS.remove(habboId);
        }
    }
}

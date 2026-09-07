package com.eu.habbo.messages.incoming.catalog.recycler;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.RecyclerLogicComposer;
import com.eu.habbo.messages.outgoing.catalog.ReloadRecyclerComposer;
import com.eu.habbo.messages.outgoing.catalog.recycler.RecyclerAdminResultComposer;
import com.eu.habbo.messages.outgoing.catalog.recycler.RecyclerAdminStateComposer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RecyclerAdminSaveEvent extends MessageHandler {
    private static final int MAX_DUCKET_COST = 1_000_000;
    private static final int MAX_LEVELS = 32;
    private static final int MAX_PRIZES_PER_LEVEL = 500;

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_RECYCLER_EDITOR)) {
            this.client.sendResponse(new RecyclerAdminResultComposer(false, "No permission"));
            return;
        }

        int ducketCost = this.packet.readInt();
        int levelCount = this.packet.readInt();
        if (ducketCost < 0 || ducketCost > MAX_DUCKET_COST || levelCount < 1 || levelCount > MAX_LEVELS) {
            this.client.sendResponse(new RecyclerAdminResultComposer(false, "Invalid recycler configuration"));
            return;
        }

        Map<Integer, Integer> probabilities = new HashMap<>();
        Map<Integer, Set<Integer>> prizeItemIds = new HashMap<>();
        Set<Integer> allPrizeIds = new HashSet<>();

        for (int index = 0; index < levelCount; index++) {
            int rarity = this.packet.readInt();
            int denominator = this.packet.readInt();
            int prizeCount = this.packet.readInt();
            if (rarity < 1 || denominator < 1 || denominator > 1_000_000 || prizeCount < 0
                    || prizeCount > MAX_PRIZES_PER_LEVEL || probabilities.putIfAbsent(rarity, denominator) != null) {
                this.client.sendResponse(new RecyclerAdminResultComposer(false, "Invalid rarity level"));
                return;
            }

            Set<Integer> prizes = new HashSet<>();
            for (int prizeIndex = 0; prizeIndex < prizeCount; prizeIndex++) {
                int itemId = this.packet.readInt();
                if (itemId < 1 || !prizes.add(itemId) || !allPrizeIds.add(itemId)) {
                    this.client.sendResponse(new RecyclerAdminResultComposer(false, "A prize can only belong to one level"));
                    return;
                }
            }
            prizeItemIds.put(rarity, prizes);
        }

        if (allPrizeIds.isEmpty() || !Emulator.getGameEnvironment().getCatalogManager()
                .saveRecyclerConfiguration(ducketCost, probabilities, prizeItemIds)) {
            this.client.sendResponse(new RecyclerAdminResultComposer(false, "Could not save recycler configuration"));
            return;
        }

        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new RecyclerLogicComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new ReloadRecyclerComposer());
        this.client.sendResponse(new RecyclerAdminResultComposer(true, "Furni-Matic configuration saved"));
        this.client.sendResponse(new RecyclerAdminStateComposer(
                Emulator.getGameEnvironment().getCatalogManager().getRecyclerConfigurationSnapshot()));
    }
}

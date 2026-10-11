package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Currency helpers shared by wired chest effects and contract transactions.
 */
public final class ChestWiredCurrencyUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChestWiredCurrencyUtil.class);

    private ChestWiredCurrencyUtil() {}

    public static int getBalance(Habbo habbo, int currencyType) {
        if (habbo == null) {
            return 0;
        }
        if (currencyType < 0) {
            return habbo.getHabboInfo().getCredits();
        }
        return habbo.getHabboInfo().getCurrencyAmount(currencyType);
    }

    public static boolean has(Habbo habbo, int currencyType, int amount) {
        return amount <= 0 || getBalance(habbo, currencyType) >= amount;
    }

    public static boolean take(Habbo habbo, int currencyType, int amount) {
        if (habbo == null || amount <= 0) {
            return amount <= 0;
        }
        if (!has(habbo, currencyType, amount)) {
            return false;
        }
        int ledgerCurrencyType = currencyType < 0 ? EconomyLedger.CREDITS : currencyType;
        try {
            LedgerWalletMutation.execute(
                    habbo,
                    new EconomyOperation(
                            EconomyOperationId.create(
                                    "wired-contract:" + habbo.getHabboInfo().getId()),
                            habbo.getHabboInfo().getId(),
                            habbo.getHabboInfo().getId(),
                            "wired_contract_debit",
                            "wired.contract.currency",
                            ledgerCurrencyType,
                            -amount,
                            null,
                            ""));
        } catch (Exception exception) {
            LOGGER.error(
                    "Unable to debit wired contract currency for user {}",
                    habbo.getHabboInfo().getId(),
                    exception);
            return false;
        }
        return true;
    }

    /** Debit and credit all currencies in one database transaction, without partial wallet changes. */
    public static boolean exchange(
            Habbo habbo, java.util.Map<Integer, Long> payment, java.util.Map<Integer, Long> reward) {
        if (payment.isEmpty() && reward.isEmpty()) return true;
        var operations = new java.util.ArrayList<EconomyOperation>();
        String operationId = EconomyOperationId.create(
                "wired-contract:" + habbo.getHabboInfo().getId());
        try {
            for (int direction = 0; direction < 2; direction++) {
                var amounts = direction == 0 ? payment : reward;
                for (var entry : amounts.entrySet()) {
                    int amount = Math.toIntExact(entry.getValue());
                    int delta = direction == 0 ? -amount : amount;
                    int type = entry.getKey();
                    if (type == -1) {
                        var event = new com.eu.habbo.plugin.events.users.UserCreditsEvent(habbo, delta);
                        if (com.eu.habbo.Emulator.getPluginManager()
                                        .fireEvent(event)
                                        .isCancelled()
                                || event.credits != delta) return false;
                    } else {
                        var event = new com.eu.habbo.plugin.events.users.UserPointsEvent(habbo, delta, type);
                        if (com.eu.habbo.Emulator.getPluginManager()
                                        .fireEvent(event)
                                        .isCancelled()
                                || event.points != delta
                                || event.type != type) return false;
                    }
                    operations.add(new EconomyOperation(
                            operationId + ":" + direction + ":" + type,
                            habbo.getHabboInfo().getId(),
                            habbo.getHabboInfo().getId(),
                            direction == 0 ? "wired_contract_debit" : "wired_contract_reward",
                            "wired.contract.currency",
                            type,
                            delta,
                            null,
                            ""));
                }
            }
            LedgerWalletMutation.executeBatch(habbo, operations);
        } catch (Exception failure) {
            LOGGER.error(
                    "Unable to commit wired contract wallet exchange for user {}",
                    habbo.getHabboInfo().getId(),
                    failure);
            return false;
        }
        if (habbo.getClient() != null) {
            if (payment.containsKey(-1) || reward.containsKey(-1))
                habbo.getClient().sendResponse(new com.eu.habbo.messages.outgoing.users.UserCreditsComposer(habbo));
            if (payment.containsKey(0) || reward.containsKey(0))
                habbo.getClient().sendResponse(new com.eu.habbo.messages.outgoing.users.UserCurrencyComposer(habbo));
            if (payment.containsKey(5) || reward.containsKey(5))
                habbo.getClient()
                        .sendResponse(new com.eu.habbo.messages.outgoing.users.UserPointsComposer(
                                habbo.getHabboInfo().getCurrencyAmount(5),
                                Math.toIntExact(reward.getOrDefault(5, 0L) - payment.getOrDefault(5, 0L)),
                                5));
        }
        return true;
    }

    public static void give(Habbo habbo, int currencyType, int amount) {
        if (habbo == null || amount <= 0) {
            return;
        }
        if (currencyType < 0) {
            habbo.giveCredits(amount, "wired.reward");
        } else {
            habbo.givePoints(currencyType, amount, "wired.reward");
        }
    }
}

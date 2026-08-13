package com.eu.habbo.habbohotel.earnings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import org.junit.jupiter.api.Test;

class PendingCurrencyEarningsTest {
    @Test
    void capturesOnlyPositiveSpendableCurrencies() {
        assertTrue(PendingCurrencyEarnings.shouldCapture(operation("command", "command.reward", EconomyLedger.CREDITS, 10)));
        assertTrue(PendingCurrencyEarnings.shouldCapture(operation("wired_reward", "wired.currency", 0, 10)));
        assertTrue(PendingCurrencyEarnings.shouldCapture(operation("achievement_reward", "achievement.reward", 5, 10)));
        assertFalse(PendingCurrencyEarnings.shouldCapture(operation("purchase", "catalog.purchase", EconomyLedger.CREDITS, -10)));
        assertFalse(PendingCurrencyEarnings.shouldCapture(operation("seasonal_reward", "event.reward", 7, 10)));
    }

    @Test
    void aClaimNeverReturnsToPendingEarnings() {
        assertFalse(PendingCurrencyEarnings.shouldCapture(
                operation(PendingCurrencyEarnings.CLAIM_OPERATION, "earnings.claim.games", 5, 10)));
        assertFalse(PendingCurrencyEarnings.shouldCapture(
                operation("credit_grant", "earnings.claim.marketplace", EconomyLedger.CREDITS, 10)));
    }

    @Test
    void classifiesKnownSourcesAndUsesBonusBagAsSafeFallback() {
        assertEquals(EarningsCategory.ACHIEVEMENTS,
                PendingCurrencyEarnings.categoryFor(operation("achievement_reward", "achievement.complete", 5, 10)));
        assertEquals(EarningsCategory.DAILY_GIFT,
                PendingCurrencyEarnings.categoryFor(operation("credit_grant", "daily_task", EconomyLedger.CREDITS, 10)));
        assertEquals(EarningsCategory.GAMES,
                PendingCurrencyEarnings.categoryFor(operation("wired_reward", "wired.currency", 0, 10)));
        assertEquals(EarningsCategory.GAMES,
                PendingCurrencyEarnings.categoryFor(operation("credit_grant", "wired.reward", EconomyLedger.CREDITS, 10)));
        assertEquals(EarningsCategory.GAMES,
                PendingCurrencyEarnings.categoryFor(operation("currency_grant", "wired.reward", 0, 10)));
        assertEquals(EarningsCategory.GAMES,
                PendingCurrencyEarnings.categoryFor(operation("currency_grant", "wired.reward", 5, 10)));
        assertEquals(EarningsCategory.BONUS_BAG,
                PendingCurrencyEarnings.categoryFor(operation("credit_grant", "command.reward", EconomyLedger.CREDITS, 10)));
        assertEquals(EarningsCategory.DONATIONS,
                PendingCurrencyEarnings.categoryFor(operation("credit_grant", "staff.command.credits", EconomyLedger.CREDITS, 10)));
    }

    private static EconomyOperation operation(String operation, String reason, int currencyType, int delta) {
        return new EconomyOperation(
                "test:" + operation + ":" + currencyType + ":" + delta,
                1,
                1,
                operation,
                reason,
                currencyType,
                delta,
                null,
                "");
    }
}

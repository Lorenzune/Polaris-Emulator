package com.eu.habbo.habbohotel.earnings;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyGiftManagerTest {
    @Test
    void createsIndependentOperationsForAllThreeConfiguredCurrencies() {
        LocalDate date = LocalDate.of(2026, 8, 11);
        DailyGiftManager.DailyGift gift = new DailyGiftManager.DailyGift(10, 20, 30);

        List<EconomyOperation> operations = DailyGiftManager.operations(42, date, gift);

        assertEquals(3, operations.size());
        assertEquals(List.of(EconomyLedger.CREDITS, PendingCurrencyEarnings.DUCKETS, PendingCurrencyEarnings.DIAMONDS),
                operations.stream().map(EconomyOperation::currencyType).toList());
        assertEquals(List.of(10, 20, 30), operations.stream().map(EconomyOperation::delta).toList());
        assertTrue(operations.stream().allMatch(operation -> operation.reason().equals(DailyGiftManager.GIFT_REASON)));
        assertTrue(operations.stream().allMatch(PendingCurrencyEarnings::shouldCapture));
        assertTrue(operations.stream().allMatch(operation ->
                PendingCurrencyEarnings.categoryFor(operation) == EarningsCategory.DAILY_GIFT));
    }

    @Test
    void zeroCurrenciesAreOmittedAndAnEmptyGiftIsDisabled() {
        DailyGiftManager.DailyGift partial = new DailyGiftManager.DailyGift(0, 15, 0);
        DailyGiftManager.DailyGift empty = new DailyGiftManager.DailyGift(0, 0, 0);

        assertTrue(partial.hasRewards());
        assertEquals(1, DailyGiftManager.operations(42, LocalDate.of(2026, 8, 11), partial).size());
        assertFalse(empty.hasRewards());
        assertTrue(DailyGiftManager.operations(42, LocalDate.of(2026, 8, 11), empty).isEmpty());
    }
}

package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredSignalChainBudgetTest {

    @Test
    void stopsAdmittingAfterConfiguredChainLimitAndReportsOnlyOnce() {
        WiredSignalChainBudget budget = new WiredSignalChainBudget(1, 0, 2, () -> 0L);

        assertTrue(budget.tryAdmit());
        assertTrue(budget.tryAdmit());
        assertFalse(budget.tryAdmit());
        assertEquals(2, budget.admittedEvents());

        assertTrue(budget.markLimitReported());
        assertFalse(budget.markLimitReported());
    }

    @Test
    void zeroLimitsKeepSignalChainsUnboundedForExplicitOptOut() {
        WiredSignalChainBudget budget = new WiredSignalChainBudget(0, 0, 0, () -> 0L);

        for (int index = 0; index < 10_000; index++) {
            assertTrue(budget.tryAdmit());
        }
        assertFalse(budget.shouldYield());
    }

    @Test
    void elapsedSliceYieldsWithoutRejectingAnyPublicationAndResetsOnResume() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        WiredSignalChainBudget budget = new WiredSignalChainBudget(100, 1, 10_000, clock::get);
        budget.markSignal();

        assertTrue(budget.tryAdmit());
        clock.set(5_000_000L);

        assertTrue(budget.shouldYield());
        assertTrue(budget.tryAdmit());
        budget.beginSlice();
        assertFalse(budget.shouldYield());
        assertEquals(2, budget.admittedEvents());
    }

    @Test
    void eventSliceCountsExecutedWorkWithoutLimitingAcceptedFanOut() {
        var budget = new WiredSignalChainBudget(2, 0, 10_000, () -> 0L);
        budget.markSignal();
        for (int i = 0; i < 3000; i++) assertTrue(budget.tryAdmit());
        assertFalse(budget.shouldYield());
        budget.processed(2);
        assertTrue(budget.shouldYield());
        budget.beginSlice();
        assertFalse(budget.shouldYield());
        assertEquals(3000, budget.admittedEvents());
    }
}

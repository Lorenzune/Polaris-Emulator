package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredSignalChainBudgetTest {

    @Test
    void stopsAdmittingAfterConfiguredChainLimitAndReportsOnlyOnce() {
        WiredSignalChainBudget budget = new WiredSignalChainBudget(2, 0);

        assertTrue(budget.tryAdmit());
        assertTrue(budget.tryAdmit());
        assertFalse(budget.tryAdmit());
        assertTrue(budget.isExhausted());
        assertEquals(2, budget.admittedEvents());

        assertTrue(budget.markLimitReported());
        assertFalse(budget.markLimitReported());
    }

    @Test
    void zeroLimitsKeepSignalChainsUnboundedForExplicitOptOut() {
        WiredSignalChainBudget budget = new WiredSignalChainBudget(0, 0);

        for (int index = 0; index < 10_000; index++) {
            assertTrue(budget.tryAdmit());
        }
        assertFalse(budget.isExhausted());
    }

    @Test
    void stopsSignalChainWhenItsTimeBudgetExpires() throws InterruptedException {
        WiredSignalChainBudget budget = new WiredSignalChainBudget(100, 1);

        assertTrue(budget.tryAdmit());
        Thread.sleep(5L);

        assertTrue(budget.abortIfDurationExceeded());
        assertFalse(budget.tryAdmit());
        assertEquals("duration limit 1ms", budget.limitReason());
    }
}

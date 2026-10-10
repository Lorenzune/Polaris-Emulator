package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class HabboInfoCreditCompatibilityTest {
    @Test
    void setCreditsIsAnAbsoluteSynchronousSave() throws Exception {
        try (var fixture = new LegacyWalletMutationTest.Fixture(100, 0)) {
            fixture.info.setCredits(250);
            assertEquals(250, fixture.info.getCredits());
            verify(fixture.connection).commit();
        }
    }

    @Test
    void setCreditsRejectsNegativeBalancesBeforeSaving() {
        RecordingHabboInfo info = new RecordingHabboInfo(100);

        assertThrows(IllegalArgumentException.class, () -> info.setCredits(-1));

        assertEquals(100, info.getCredits());
        assertEquals(0, info.saveCount);
    }

    @Test
    void addCreditsAppliesADeltaClampsAndSavesSynchronously() throws Exception {
        try (var fixture = new LegacyWalletMutationTest.Fixture(100, 0)) {
            fixture.info.addCredits(50);
            assertEquals(150, fixture.info.getCredits());
            verify(fixture.connection).commit();
        }
        try (var fixture = new LegacyWalletMutationTest.Fixture(150, 0)) {
            fixture.info.addCredits(-200);
            assertEquals(0, fixture.info.getCredits());
            verify(fixture.connection).commit();
        }
        try (var fixture = new LegacyWalletMutationTest.Fixture(Integer.MAX_VALUE, 0)) {
            fixture.info.applyPersistedCredits(Integer.MAX_VALUE);
            fixture.info.addCredits(1);
            assertEquals(Integer.MAX_VALUE, fixture.info.getCredits());
            verify(fixture.connection).commit();
            fixture.ledger.verifyNoInteractions();
        }
    }

    @Test
    void committedLedgerBalanceUpdatesMemoryWithoutRunningLegacyPersistence() {
        RecordingHabboInfo info = new RecordingHabboInfo(100);

        info.applyPersistedCredits(275);

        assertEquals(275, info.getCredits());
        assertEquals(0, info.saveCount);
        assertThrows(IllegalArgumentException.class, () -> info.applyPersistedCredits(-1));
        assertEquals(275, info.getCredits());
        assertEquals(0, info.saveCount);
    }

    private static final class RecordingHabboInfo extends HabboInfo {
        private int saveCount;

        private RecordingHabboInfo(int credits) {
            super(42, credits);
        }

        @Override
        public void run() {
            this.saveCount++;
        }
    }
}

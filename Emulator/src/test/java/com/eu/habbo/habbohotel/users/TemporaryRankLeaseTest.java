package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.permissions.Rank;
import org.junit.jupiter.api.Test;

class TemporaryRankLeaseTest {
    @Test
    void privilegesExpireWithoutDatabaseOrTimer() {
        HabboInfo info = mock(HabboInfo.class, CALLS_REAL_METHODS);
        Rank base = new Rank(1);
        Rank staff = new Rank(3);
        info.setTemporaryRank(staff, base, System.currentTimeMillis() - 1);
        assertSame(base, info.getRank());
        assertSame(staff, info.getStoredRank());
    }

    @Test
    void expiredLeaseResolvesRemovedRanksToTheSafeFallback() {
        HabboInfo info = mock(HabboInfo.class, CALLS_REAL_METHODS);
        Rank base = new Rank(1);
        Rank removed = new Rank(3);
        Rank temporary = new Rank(4);
        info.setTemporaryRank(temporary, removed, System.currentTimeMillis() - 1, id -> base);
        assertSame(base, info.getRank());
    }

    @Test
    void concurrentExpiredLeaseAndPermanentDemotionNeverExposeExpiredPrivileges() throws Exception {
        HabboInfo info = mock(HabboInfo.class, CALLS_REAL_METHODS);
        Rank base = new Rank(1);
        Rank staff = new Rank(3);
        info.setRank(base);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var writer = workers.submit(() -> {
                start.await();
                for (int i = 0; i < 10_000; i++) {
                    info.setTemporaryRank(staff, base, 0);
                    info.setRank(base);
                }
                return null;
            });
            var reader = workers.submit(() -> {
                start.await();
                for (int i = 0; i < 10_000; i++) assertSame(base, info.getRank());
                return null;
            });
            start.countDown();
            writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
            reader.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void permanentSameRankClearsTheLease() {
        HabboInfo info = mock(HabboInfo.class, CALLS_REAL_METHODS);
        Rank base = new Rank(1);
        Rank staff = new Rank(3);
        info.setTemporaryRank(staff, base, System.currentTimeMillis() - 1);
        info.setRank(staff);
        assertSame(staff, info.getRank());
    }

    @Test
    void anActiveLeaseUsesItsAssignedRank() {
        HabboInfo info = mock(HabboInfo.class, CALLS_REAL_METHODS);
        Rank base = new Rank(1);
        Rank staff = new Rank(3);
        info.setTemporaryRank(staff, base, System.currentTimeMillis() + 60_000);
        assertSame(staff, info.getRank());
    }
}

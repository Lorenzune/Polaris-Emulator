package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TemporaryRankStoreIT {
    @Test
    void durableLeaseRestoresOfflineAndPermanentChangeWinsConcurrentGrant() throws Exception {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) throw new AssertionError("Docker is required in CI");
            assumeTrue(false, "Docker/Testcontainers unavailable");
        }
        try (var source = TestDatabase.freshDatabase("temporary_ranks")) {
            MigrationRunner.migrate(source);
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO users (id, username, password, ip_register, ip_current, `rank`) VALUES (920001, 'temp_rank_it', '!', '127.0.0.1', '127.0.0.1', 1)");
                assertEquals(
                        new TemporaryRankStore.State(3, 1, 61_000),
                        TemporaryRankStore.grant(connection, 920001, 3, 60_000, 99, "test", 1_000, rank -> rank < 5));
            }
            // A fresh connection represents a restart/offline login.
            try (Connection connection = source.getConnection()) {
                assertEquals(
                        new TemporaryRankStore.State(1, null, 0),
                        TemporaryRankStore.refresh(connection, 920001, 61_000));
            }
            CountDownLatch grantedLock = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var grant = workers.submit(() -> {
                    try (Connection connection = source.getConnection()) {
                        TemporaryRankStore.grant(connection, 920001, 3, 60_000, 99, "race", 100_000, rank -> {
                            grantedLock.countDown();
                            try {
                                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(failure);
                            }
                            return true;
                        });
                        return null;
                    }
                });
                assertTrue(grantedLock.await(5, TimeUnit.SECONDS));
                var permanent = workers.submit(() -> {
                    try (Connection connection = source.getConnection()) {
                        TemporaryRankStore.permanent(connection, 920001, 4);
                        return null;
                    }
                });
                release.countDown();
                grant.get(10, TimeUnit.SECONDS);
                permanent.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            try (Connection connection = source.getConnection()) {
                assertEquals(
                        new TemporaryRankStore.State(4, null, 0),
                        TemporaryRankStore.refresh(connection, 920001, 200_000));
            }
        }
    }
}

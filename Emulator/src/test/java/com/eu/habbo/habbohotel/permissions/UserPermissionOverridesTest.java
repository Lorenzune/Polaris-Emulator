package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class UserPermissionOverridesTest {
    private UserPermissionOverrides.UserOverride row(PermissionSetting setting, long expires) {
        return new UserPermissionOverrides.UserOverride("acc_test", setting, expires, "reason", 99, "staff");
    }

    @Test
    void expiryIsCheckedEvenWithinTheSameCacheEntry() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicInteger loads = new AtomicInteger();
        var service = new UserPermissionOverrides(
                id -> {
                    loads.incrementAndGet();
                    return List.of(row(PermissionSetting.ALLOWED, 2000));
                },
                clock::get);
        assertNotNull(service.snapshot(42).active("acc_test", 1999));
        clock.set(2000);
        assertNull(service.snapshot(42).active("acc_test", clock.get()));
        assertEquals(1, loads.get());
    }

    @Test
    void databaseFailureDoesNotRestoreRankFallbackOrKeepAnOldGrantAndRetriesAreBounded() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicInteger loads = new AtomicInteger();
        var service = new UserPermissionOverrides(
                id -> {
                    if (loads.incrementAndGet() == 1) return List.of(row(PermissionSetting.DISALLOWED, 0));
                    throw new IllegalStateException("database down");
                },
                clock::get);
        assertTrue(service.snapshot(42).available());
        clock.addAndGet(UserPermissionOverrides.CACHE_MS);
        assertFalse(service.snapshot(42).available());
        for (int i = 0; i < 100; i++) assertFalse(service.snapshot(42).available());
        assertEquals(2, loads.get());
        clock.addAndGet(UserPermissionOverrides.RETRY_MS);
        assertFalse(service.snapshot(42).available());
        assertEquals(3, loads.get());
    }

    @Test
    void emptyResultIsAvailableAndInvalidationForcesFreshData() {
        AtomicInteger loads = new AtomicInteger();
        var service = new UserPermissionOverrides(
                id -> {
                    loads.incrementAndGet();
                    return List.of();
                },
                () -> 1000);
        assertTrue(service.snapshot(42).available());
        assertEquals(Map.of(), service.snapshot(42).values());
        service.invalidate(42);
        service.snapshot(42);
        service.invalidateAll();
        service.snapshot(42);
        assertEquals(3, loads.get());
    }

    @Test
    void concurrentChecksLoadEachUserOnlyOnce() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        var service = new UserPermissionOverrides(
                id -> {
                    loads.incrementAndGet();
                    started.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    return List.of();
                },
                () -> 1000);
        try (var workers = Executors.newFixedThreadPool(4)) {
            var first = workers.submit(() -> service.snapshot(42));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var second = workers.submit(() -> service.snapshot(42));
            release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).available());
            assertTrue(second.get(5, TimeUnit.SECONDS).available());
            assertEquals(1, loads.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void reloadDuringDatabaseReadCannotPublishAnOldAuthorizationSnapshot() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        var service = new UserPermissionOverrides(
                id -> {
                    if (loads.incrementAndGet() == 1) {
                        started.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                        return List.of(row(PermissionSetting.ALLOWED, 0));
                    }
                    return List.of(row(PermissionSetting.DISALLOWED, 0));
                },
                () -> 1000);
        try (var workers = Executors.newSingleThreadExecutor()) {
            var pending = workers.submit(() -> service.snapshot(42));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            service.invalidateAll();
            release.countDown();
            assertFalse(pending.get(5, TimeUnit.SECONDS).available());
            assertEquals(
                    PermissionSetting.DISALLOWED,
                    service.snapshot(42).active("acc_test", 1000).setting());
        } finally {
            release.countDown();
        }
    }

    @Test
    void oversizedStoredOverrideSetsDenyInsteadOfBeingTruncatedIntoAnEmptyFallback() {
        var service = new UserPermissionOverrides(
                id -> java.util.Collections.nCopies(257, row(PermissionSetting.ALLOWED, 0)), () -> 1000);
        assertFalse(service.snapshot(42).available());
    }

    @Test
    void durationsAreBoundedAndCannotOverflow() {
        assertEquals(0, UserPermissionOverrides.durationMillis("perm"));
        assertEquals(1_800_000, UserPermissionOverrides.durationMillis("30m"));
        assertEquals(1_209_600_000, UserPermissionOverrides.durationMillis("2w"));
        for (String value : List.of("-1h", "0m", "31d", "999999999999999999999w", "1h OR 1=1", "1.5h"))
            assertThrows(IllegalArgumentException.class, () -> UserPermissionOverrides.durationMillis(value));
    }
}

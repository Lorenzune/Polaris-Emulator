package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PermissionAdministrationIT {
    @Test
    void migrationsAuditRollbackAndConcurrentOverrides() throws Exception {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) throw new AssertionError("Docker required in CI");
            assumeTrue(false, "Docker/Testcontainers unavailable");
        }
        try (var source = TestDatabase.freshDatabase("permission_admin")) {
            MigrationRunner.migrate(source);
            try (var db = source.getConnection();
                    var sql = db.createStatement()) {
                sql.executeUpdate(
                        "INSERT INTO users (id,username,password,ip_register,ip_current,`rank`) VALUES (920001,'override_target','!','127.0.0.1','127.0.0.1',1),(920099,'override_admin','!','127.0.0.1','127.0.0.1',7)");
            }
            var store = new UserPermissionOverrides(id -> List.of(), System::currentTimeMillis);
            var actor = new PermissionAuditLog.Actor(920099, "override_admin", "perm", "isolated integration");
            try (var db = source.getConnection()) {
                assertTrue(store.change(db, 920001, "acc_test", PermissionSetting.ALLOWED, 0, actor, (a, t, p) -> {
                    assertEquals(7, a);
                    assertEquals(1, t);
                }));
                assertEquals(1, PermissionAuditLog.recent(db, 920001, 0).size());
                try (var sql = db.createStatement()) {
                    sql.executeUpdate(
                            "CREATE TRIGGER reject_permission_audit BEFORE INSERT ON permission_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test audit failure'");
                }
                assertThrows(
                        SQLException.class,
                        () -> store.change(
                                db, 920001, "acc_test", PermissionSetting.DISALLOWED, 0, actor, (a, t, p) -> {}));
                try (var sql = db.createStatement();
                        var rows = sql.executeQuery(
                                "SELECT value FROM user_permission_overrides WHERE user_id=920001 AND permission_key='acc_test'")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
                try (var sql = db.createStatement()) {
                    sql.executeUpdate("DROP TRIGGER reject_permission_audit");
                }
            }
            CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var grant = workers.submit(() -> {
                    try (var db = source.getConnection()) {
                        store.change(db, 920001, "acc_test", PermissionSetting.ALLOWED, 0, actor, (a, t, p) -> {
                            locked.countDown();
                            try {
                                if (!release.await(5, TimeUnit.SECONDS)) throw new SQLException("timeout");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new SQLException(e);
                            }
                        });
                    }
                    return null;
                });
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var deny = workers.submit(() -> {
                    try (var db = source.getConnection()) {
                        store.change(db, 920001, "acc_test", PermissionSetting.DISALLOWED, 0, actor, (a, t, p) -> {});
                    }
                    return null;
                });
                release.countDown();
                grant.get(10, TimeUnit.SECONDS);
                deny.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            try (var db = source.getConnection();
                    var sql = db.createStatement();
                    var rows = sql.executeQuery(
                            "SELECT value FROM user_permission_overrides WHERE user_id=920001 AND permission_key='acc_test'")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
                assertEquals(3, PermissionAuditLog.recent(db, 920001, 0).size());
            }
        }
    }
}

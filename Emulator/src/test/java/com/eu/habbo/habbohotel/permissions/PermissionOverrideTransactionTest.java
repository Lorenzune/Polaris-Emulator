package com.eu.habbo.habbohotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PermissionOverrideTransactionTest {
    final PermissionAuditLog.Actor actor = new PermissionAuditLog.Actor(99, "admin", "perm", "test");

    static final class Fixture {
        final Connection db = mock(Connection.class);
        final Map<String, PreparedStatement> statements = new HashMap<>();

        Fixture() throws Exception {
            when(db.getAutoCommit()).thenReturn(true);
            when(db.prepareStatement(anyString())).thenAnswer(inv -> {
                String sql = inv.getArgument(0);
                PreparedStatement statement = mock(PreparedStatement.class);
                statements.put(sql, statement);
                ResultSet rows = mock(ResultSet.class);
                when(statement.executeQuery()).thenReturn(rows);
                if (sql.startsWith("SELECT id")) {
                    when(rows.next()).thenReturn(true, true, false);
                    when(rows.getInt(1)).thenReturn(42, 99);
                    when(rows.getInt(2)).thenReturn(1, 7);
                } else if (sql.startsWith("SELECT COUNT")) {
                    when(rows.next()).thenReturn(true, false);
                    when(rows.getInt(1)).thenReturn(0);
                }
                return statement;
            });
        }
    }

    @Test
    void auditFailureRollsBackAndKeepsPreviouslyCachedState() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger loads = new AtomicInteger();
        var store = new UserPermissionOverrides(
                id -> {
                    loads.incrementAndGet();
                    return List.of();
                },
                () -> 1000L);
        store.snapshot(42);
        when(f.db.prepareStatement(startsWith("INSERT INTO permission_audit")))
                .thenThrow(new SQLException("audit down"));
        assertThrows(
                SQLException.class,
                () -> store.change(f.db, 42, "acc_test", PermissionSetting.ALLOWED, 0, actor, (a, t, p) -> {}));
        verify(f.db).rollback();
        verify(f.db, never()).commit();
        verify(f.db).setAutoCommit(true);
        store.snapshot(42);
        assertEquals(1, loads.get());
    }

    @Test
    void guardUsesLockedRanksAndRejectionNeverWritesPrivileges() throws Exception {
        Fixture f = new Fixture();
        var store = new UserPermissionOverrides(id -> List.of(), () -> 1000L);
        assertThrows(
                SecurityException.class,
                () -> store.change(f.db, 42, "acc_test", PermissionSetting.ALLOWED, 0, actor, (a, t, p) -> {
                    assertEquals(7, a);
                    assertEquals(1, t);
                    throw new SecurityException("demoted");
                }));
        verify(f.db).rollback();
        verify(f.db, never()).commit();
        assertTrue(f.statements.keySet().stream().allMatch(sql -> sql.startsWith("SELECT")));
    }

    @Test
    void cacheIsInvalidatedOnlyAfterCommitAndAuditIsInSameTransaction() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger loads = new AtomicInteger();
        var store = new UserPermissionOverrides(
                id -> {
                    loads.incrementAndGet();
                    return List.of();
                },
                () -> 1000L);
        store.snapshot(42);
        doAnswer(inv -> {
                    store.snapshot(42);
                    assertEquals(1, loads.get());
                    return null;
                })
                .when(f.db)
                .commit();
        assertTrue(store.change(f.db, 42, "acc_test", PermissionSetting.ROOM_OWNER, 2000, actor, (a, t, p) -> {}));
        var audit = f.statements.entrySet().stream()
                .filter(e -> e.getKey().startsWith("INSERT INTO permission_audit"))
                .findFirst()
                .orElseThrow()
                .getValue();
        var order = inOrder(audit, f.db);
        order.verify(audit).executeUpdate();
        order.verify(f.db).commit();
        store.snapshot(42);
        assertEquals(2, loads.get());
    }

    @Test
    void selfAndMalformedKeysAreRejectedBeforeOpeningTransaction() throws Exception {
        Fixture f = new Fixture();
        var store = new UserPermissionOverrides(id -> List.of(), () -> 1000L);
        assertThrows(
                IllegalArgumentException.class,
                () -> store.change(f.db, 99, "acc_test", PermissionSetting.ALLOWED, 0, actor, (a, t, p) -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.change(f.db, 42, "acc_test; DROP", PermissionSetting.ALLOWED, 0, actor, (a, t, p) -> {}));
        verify(f.db, never()).setAutoCommit(false);
    }
}

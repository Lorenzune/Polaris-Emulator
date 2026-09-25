package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WiredRoomDiagnosticsTest {

    @Test
    void rejectedDelayedAdmissionRollsBackAndRecoversAfterAcceptedWorkCompletes() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1_000, 100, 1, 50, 150, 70, 5);

        assertTrue(diagnostics.tryScheduleDelayedEvent(1_000L, "fixture", 7, "first"));
        assertFalse(diagnostics.tryScheduleDelayedEvent(1_001L, "fixture", 7, "rejected"));
        assertEquals(1, pending(diagnostics, 1_001L));

        diagnostics.completeDelayedEvent();
        assertEquals(0, pending(diagnostics, 1_002L));

        assertTrue(diagnostics.tryScheduleDelayedEvent(1_003L, "fixture", 7, "recovered"));
        assertEquals(1, pending(diagnostics, 1_003L));
    }

    @Test
    void executorOverloadDoesNotMarkLowUsageRoomAsHeavy() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1_000, 1_000, 100, 50, 150, 70, 1, 1, 60, 20);

        assertTrue(diagnostics.tryConsumeExecutionBudget(1, 1_000L, "fixture", 7, "slow stack"));
        diagnostics.recordExecution(60L, 1_000L, "fixture", 7, "slow stack");

        WiredRoomDiagnostics.Snapshot snapshot = diagnostics.snapshot(0, 10, 0L, 2_000L);
        Map<WiredRoomDiagnostics.Type, WiredRoomDiagnostics.LogEntry> logs = logsByType(snapshot);

        assertFalse(snapshot.isHeavy());
        assertEquals(1, logs.get(WiredRoomDiagnostics.Type.EXECUTOR_OVERLOAD).getCount());
        assertEquals(0, logs.get(WiredRoomDiagnostics.Type.MARKED_AS_HEAVY).getCount());
    }

    @Test
    void highWiredUsageMarksRoomAsHeavyWithoutSlowExecution() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1_000, 100, 100, 50, 150, 70, 1, 2, 60, 20);

        assertTrue(diagnostics.tryConsumeExecutionBudget(80, 1_000L, "fixture", 7, "high usage"));
        diagnostics.recordExecution(10L, 1_000L, "fixture", 7, "fast stack");

        WiredRoomDiagnostics.Snapshot snapshot = diagnostics.snapshot(0, 10, 0L, 2_000L);
        Map<WiredRoomDiagnostics.Type, WiredRoomDiagnostics.LogEntry> logs = logsByType(snapshot);

        assertTrue(snapshot.isHeavy());
        assertEquals(1, logs.get(WiredRoomDiagnostics.Type.MARKED_AS_HEAVY).getCount());
        assertEquals(0, logs.get(WiredRoomDiagnostics.Type.EXECUTOR_OVERLOAD).getCount());
    }

    private static Map<WiredRoomDiagnostics.Type, WiredRoomDiagnostics.LogEntry> logsByType(
            WiredRoomDiagnostics.Snapshot snapshot) {
        return snapshot.getLogs().stream().collect(java.util.stream.Collectors.toMap(
                WiredRoomDiagnostics.LogEntry::getType, entry -> entry));
    }

    private static int pending(WiredRoomDiagnostics diagnostics, long now) {
        return diagnostics.snapshot(0, 10, 0L, now).getDelayedEventsPending();
    }
}

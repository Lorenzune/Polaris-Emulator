package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class AvatarMigrationBackfillPolicyTest {
    @Test
    void permitsOnlyTheMissingAvatarPrerequisitesBehindThePetRelease() {
        assertTrue(MigrationRunner.permitsAvatarBackfill(history(
                "20261011153000",
                migration("20261011120000", MigrationState.IGNORED),
                migration("20261011150000", MigrationState.IGNORED))));
        assertTrue(MigrationRunner.permitsAvatarBackfill(history(
                "20261011153000",
                migration("20261011120000", MigrationState.SUCCESS),
                migration("20261011150000", MigrationState.IGNORED))));
    }

    @Test
    void keepsFreshAndFullyMigratedDatabasesStrict() {
        assertFalse(MigrationRunner.permitsAvatarBackfill(history(null)));
        assertFalse(MigrationRunner.permitsAvatarBackfill(history("20261011153000")));
        assertFalse(MigrationRunner.permitsAvatarBackfill(
                history("20261011180000", migration("20261011120000", MigrationState.IGNORED))));
    }

    @Test
    void rejectsUnrelatedGapsAndInvalidAvatarDependencyOrder() {
        assertFalse(MigrationRunner.permitsAvatarBackfill(history(
                "20261011153000",
                migration("20261011120000", MigrationState.IGNORED),
                migration("20261011140000", MigrationState.IGNORED))));
        assertFalse(MigrationRunner.permitsAvatarBackfill(history(
                "20261011153000",
                migration("20261011120000", MigrationState.IGNORED),
                migration("20261011150000", MigrationState.SUCCESS))));
    }

    private static MigrationInfoService history(String current, MigrationInfo... migrations) {
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo latest = current == null ? null : migration(current, MigrationState.SUCCESS);
        when(info.current()).thenReturn(latest);
        when(info.all()).thenReturn(migrations);
        return info;
    }

    private static MigrationInfo migration(String version, MigrationState state) {
        MigrationInfo info = mock(MigrationInfo.class);
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        when(info.getState()).thenReturn(state);
        return info;
    }
}

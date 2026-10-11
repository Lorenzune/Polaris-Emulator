package com.eu.habbo.database.migration;

import java.sql.SQLException;
import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One explicit compatibility transition for the published, non-reentrant avatar repair. */
final class AvatarRepairMigrationRecovery {
    static final String VERSION = "20261011170000";
    static final int ORIGINAL_CHECKSUM = 1699687904;
    static final int REENTRANT_CHECKSUM = 2015814367;
    private static final Logger LOGGER = LoggerFactory.getLogger(AvatarRepairMigrationRecovery.class);

    private AvatarRepairMigrationRecovery() {}

    record Plan(int installedRank, int checksum, boolean failed) {}

    static Plan plan(Flyway flyway) {
        MigrationInfo[] history = flyway.info().all();
        MigrationInfo repair = Arrays.stream(history)
                .filter(m ->
                        m.getVersion() != null && VERSION.equals(m.getVersion().getVersion()))
                .findFirst()
                .orElse(null);
        if (repair == null || repair.getAppliedChecksum() == null) return null;
        boolean failed = repair.getState() == MigrationState.FAILED;
        int applied = repair.getAppliedChecksum();
        if ((!failed && applied != ORIGINAL_CHECKSUM)
                || (failed && applied != ORIGINAL_CHECKSUM && applied != REENTRANT_CHECKSUM)) return null;
        if (!Integer.valueOf(REENTRANT_CHECKSUM).equals(repair.getResolvedChecksum())
                || !repair.isDescriptionMatching()
                || !repair.isTypeMatching()) {
            throw new MigrationException(
                    "Avatar repair recovery refused: the packaged revision is not the audited recovery.");
        }
        for (String prerequisite : new String[] {"20261011120000", "20261011150000"}) {
            if (Arrays.stream(history)
                    .noneMatch(m -> m.getVersion() != null
                            && prerequisite.equals(m.getVersion().getVersion())
                            && m.getState().isApplied()
                            && m.getState() != MigrationState.FAILED)) {
                throw new MigrationException("Avatar repair recovery refused: missing prerequisite V" + prerequisite);
            }
        }
        if (failed
                && Arrays.stream(history)
                        .anyMatch(m -> m.getVersion() != null
                                && m.getVersion().compareTo(repair.getVersion()) > 0
                                && m.getState().isApplied())) {
            throw new MigrationException("Avatar repair recovery refused: later migrations are already applied.");
        }
        var validation = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .ignoreMigrationPatterns("*:future", "*:pending")
                .load()
                .validateWithResult();
        if (validation.invalidMigrations.isEmpty()
                || validation.invalidMigrations.stream()
                        .anyMatch(error -> !VERSION.equals(error.version)
                                || (error.errorDetails.errorCode != CoreErrorCode.CHECKSUM_MISMATCH
                                        && error.errorDetails.errorCode != CoreErrorCode.FAILED_VERSIONED_MIGRATION))) {
            throw new MigrationException("Avatar repair recovery refused: unrelated migration validation errors. "
                    + validation.getAllErrorMessages());
        }
        return new Plan(repair.getInstalledRank(), applied, failed);
    }

    /** Call only after the backup succeeds. Never repairs other versions or marks failed SQL successful. */
    static void apply(DataSource dataSource, Plan plan) throws SQLException {
        String sql = plan.failed()
                ? "DELETE FROM flyway_schema_history WHERE installed_rank=? AND version=? AND checksum=? AND success=0"
                : "UPDATE flyway_schema_history SET checksum=" + REENTRANT_CHECKSUM
                        + " WHERE installed_rank=? AND version=? AND checksum=? AND success=1";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, plan.installedRank());
            statement.setString(2, VERSION);
            statement.setInt(3, plan.checksum());
            if (statement.executeUpdate() != 1) {
                throw new MigrationException("Avatar repair recovery refused: schema history changed concurrently.");
            }
        }
        LOGGER.warn(
                "[migrate] Backed-up avatar repair compatibility transition V{}: {} (original checksum {}).",
                VERSION,
                plan.failed()
                        ? "failed entry cleared; reentrant SQL must complete normally"
                        : "known applied checksum aligned",
                plan.checksum());
    }
}

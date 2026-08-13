package com.eu.habbo.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DailyGiftSchemaContractTest {
    @Test
    void migrationDefinesHotelSettingsAndPerDayAssignmentUniqueness() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260811200000__hotel_settings_daily_gift.sql"));

        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS `hotel_settings`"));
        assertTrue(migration.contains("'daily_gift.credits'"));
        assertTrue(migration.contains("'daily_gift.duckets'"));
        assertTrue(migration.contains("'daily_gift.diamonds'"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS `users_daily_gift_assignments`"));
        assertTrue(migration.contains("`user_id` INT NOT NULL"));
        assertFalse(migration.contains("`user_id` INT UNSIGNED NOT NULL"));
        assertTrue(migration.contains("PRIMARY KEY (`user_id`, `gift_date`)"));
    }

    @Test
    void pendingEarningsForeignKeyMatchesTheSignedUsersPrimaryKey() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260811180000__pending_currency_earnings.sql"));

        assertTrue(migration.contains("`user_id` INT NOT NULL"));
        assertFalse(migration.contains("`user_id` INT UNSIGNED NOT NULL"));
    }
}

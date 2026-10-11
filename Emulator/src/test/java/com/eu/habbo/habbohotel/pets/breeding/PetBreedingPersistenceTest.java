package com.eu.habbo.habbohotel.pets.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.pets.Pet;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PetBreedingPersistenceTest {
    @Test
    void birthAndNestConsumptionCommitTogetherAndRollbackTogether() throws Exception {
        String url = System.getenv("POLARIS_PET_MIGRATION_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "Requires disposable MariaDB");
        String schema = "pet_birth_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, "root", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "`");
            try {
                connection.setCatalog(schema);
                statement.execute("CREATE TABLE items(id INT PRIMARY KEY, room_id INT) ENGINE=InnoDB");
                statement.execute(
                        "CREATE TABLE users_pets(id INT PRIMARY KEY AUTO_INCREMENT, user_id INT, room_id INT, name VARCHAR(15) NOT NULL UNIQUE, race INT, type INT, color VARCHAR(6), experience INT, energy INT, respect INT, created INT) ENGINE=InnoDB");
                statement.execute("INSERT INTO items VALUES(1,7),(2,7),(3,8)");
                var game = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
                var config = mock(ConfigurationManager.class);
                try (var emulator = mockStatic(Emulator.class)) {
                    emulator.when(Emulator::getConfig).thenReturn(config);
                    emulator.when(Emulator::getGameEnvironment).thenReturn(game);
                    when(game.getPetManager().getPetData(29).getType()).thenReturn(29);
                    Pet child = new Pet(29, 1, "FFFFFF", "Baby", 10);
                    child.needsUpdate = true;
                    PetBreedingPersistence.createFromNest(connection, child, 1, 7);
                    assertTrue(child.getId() > 0);
                    assertEquals(0, count(statement, "SELECT COUNT(*) FROM items WHERE id=1"));
                    assertEquals(1, count(statement, "SELECT COUNT(*) FROM users_pets"));

                    Pet duplicateName = new Pet(29, 1, "FFFFFF", "Baby", 10);
                    duplicateName.needsUpdate = true;
                    assertThrows(
                            SQLException.class,
                            () -> PetBreedingPersistence.createFromNest(connection, duplicateName, 2, 7));
                    assertEquals(
                            1,
                            count(statement, "SELECT COUNT(*) FROM items WHERE id=2"),
                            "failed birth must restore the nest");
                    assertEquals(1, count(statement, "SELECT COUNT(*) FROM users_pets"));

                    Pet stranger = new Pet(29, 1, "FFFFFF", "Other", 10);
                    stranger.needsUpdate = true;
                    assertThrows(
                            SQLException.class,
                            () -> PetBreedingPersistence.createFromNest(connection, stranger, 3, 7));
                    assertThrows(
                            SQLException.class,
                            () -> PetBreedingPersistence.createFromNest(connection, stranger, 1, 7));
                    assertEquals(1, count(statement, "SELECT COUNT(*) FROM items WHERE id=3"));
                    assertEquals(
                            1,
                            count(statement, "SELECT COUNT(*) FROM users_pets"),
                            "missing or moved nests cannot create extra pets");
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
                connection.setCatalog("mysql");
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    private static int count(java.sql.Statement statement, String sql) throws SQLException {
        try (var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}

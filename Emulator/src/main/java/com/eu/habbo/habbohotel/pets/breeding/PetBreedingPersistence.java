package com.eu.habbo.habbohotel.pets.breeding;

import com.eu.habbo.habbohotel.pets.Pet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class PetBreedingPersistence {
    private PetBreedingPersistence() {}
    public static void createFromNest(Connection connection, Pet child, int nestId, int roomId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM items WHERE id = ? AND room_id = ?")) {
                delete.setInt(1, nestId); delete.setInt(2, roomId);
                if (delete.executeUpdate() != 1) throw new SQLException("Nest no longer persisted in room");
            }
            child.save(connection);
            if (child.getId() <= 0) throw new SQLException("Offspring was not persisted");
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            connection.rollback(); throw failure;
        }
    }
}

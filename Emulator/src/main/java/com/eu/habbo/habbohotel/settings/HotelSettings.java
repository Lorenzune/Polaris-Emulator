package com.eu.habbo.habbohotel.settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Database-backed hotel settings that are independent from emulator bootstrap configuration. */
public final class HotelSettings {
    private static final String READ_VALUE = "SELECT `value` FROM `hotel_settings` WHERE `key` = ? LIMIT 1";

    private HotelSettings() {}

    public static int getNonNegativeInt(Connection connection, String key, int defaultValue, int maximum)
            throws SQLException {
        if (connection == null) throw new IllegalArgumentException("connection must not be null");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (defaultValue < 0 || maximum < defaultValue) throw new IllegalArgumentException("invalid integer bounds");

        try (PreparedStatement statement = connection.prepareStatement(READ_VALUE)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return defaultValue;
                return parseNonNegativeInt(result.getString("value"), defaultValue, maximum);
            }
        }
    }

    static int parseNonNegativeInt(String value, int defaultValue, int maximum) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Math.min(Math.max(0, Integer.parseInt(value.strip())), maximum);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}

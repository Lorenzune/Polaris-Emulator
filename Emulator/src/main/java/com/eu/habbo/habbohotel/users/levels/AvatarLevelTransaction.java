package com.eu.habbo.habbohotel.users.levels;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

/** Do not treat the intermediate receive/send order of an atomic trade as earned avatar points. */
public final class AvatarLevelTransaction implements AutoCloseable {
    private final Connection connection;
    private final int[] users;
    private boolean deferred;

    public AvatarLevelTransaction(Connection connection, int... users) throws SQLException {
        this.connection = connection;
        this.users =
                Arrays.stream(users).filter(id -> id > 0).distinct().sorted().toArray();
        setDeferred(true);
    }

    public void flush() throws SQLException {
        setDeferred(false);
        for (int user : users) {
            try (var call = connection.prepareCall("{CALL avatar_levels_refresh(?)}")) {
                call.setInt(1, user);
                call.execute();
            }
        }
    }

    private void setDeferred(boolean value) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("SET @avatar_levels_defer = " + (value ? "1" : "0"));
            this.deferred = value;
        }
    }

    @Override
    public void close() throws SQLException {
        if (this.deferred) setDeferred(false);
    }
}

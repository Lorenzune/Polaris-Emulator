package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.BlockedUsersComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class GetBlockedUsersEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null || this.client.getHabbo().getHabboInfo() == null) {
            return;
        }

        List<String> blockedUsernames = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT target.username " +
                             "FROM users_blocked blocked " +
                             "INNER JOIN users target ON target.id = blocked.target_id " +
                             "WHERE blocked.user_id = ? " +
                             "ORDER BY target.username")) {
            statement.setInt(1, this.client.getHabbo().getHabboInfo().getId());

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    blockedUsernames.add(result.getString("username"));
                }
            }
        }

        this.client.sendResponse(new BlockedUsersComposer(blockedUsernames));
    }
}

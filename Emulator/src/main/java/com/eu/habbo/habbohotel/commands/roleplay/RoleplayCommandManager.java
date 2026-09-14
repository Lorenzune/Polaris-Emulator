package com.eu.habbo.habbohotel.commands.roleplay;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoleplayCommandManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleplayCommandManager.class);
    private static RoleplayCommandManager INSTANCE;

    private final Map<Integer, GenericRoleplayCommand> commands = new ConcurrentHashMap<>();

    public static synchronized RoleplayCommandManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RoleplayCommandManager();
        }
        return INSTANCE;
    }

    public RoleplayCommandManager() {
    }

    public synchronized int load() {
        int loaded = 0;
        Map<Integer, GenericRoleplayCommand> loadedCommands = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM roleplay_commands WHERE enabled = 1")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        RoleplayCommandData data = new RoleplayCommandData(set);
                        GenericRoleplayCommand cmd = new GenericRoleplayCommand(data);
                        loadedCommands.put(data.getId(), cmd);
                        loaded++;
                    } catch (Exception ex) {
                        LOGGER.error("Failed to load roleplay command ID {}", set.getInt("id"), ex);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("SQL exception occurred while loading roleplay commands", e);
            return this.commands.size();
        }

        // Replace the active set only after the database query completed. A failed
        // reload must not leave the emulator without its previous commands.
        CommandHandler.removeCommandsByKeyPrefix("roleplay_cmd_");
        this.commands.clear();
        loadedCommands.forEach((id, command) -> {
            this.commands.put(id, command);
            CommandHandler.addCommand("roleplay_cmd_" + id, command);
        });

        LOGGER.info("Roleplay Command Manager -> Loaded {} dynamic commands!", loaded);
        return loaded;
    }

    public int reload() {
        return load();
    }

    public Map<Integer, GenericRoleplayCommand> getCommands() {
        return Collections.unmodifiableMap(new HashMap<>(commands));
    }

    public java.util.Collection<RoleplayCommandData> getCommandData() {
        List<RoleplayCommandData> list = new ArrayList<>(commands.size());
        for (GenericRoleplayCommand cmd : commands.values()) {
            if (cmd != null && cmd.getData() != null) {
                list.add(cmd.getData());
            }
        }
        list.sort(Comparator
                .comparing((RoleplayCommandData data) -> data.getCategory() == null ? "" : data.getCategory(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(RoleplayCommandData::getSortOrder)
                .thenComparing(RoleplayCommandData::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(RoleplayCommandData::getId));
        return list;
    }

    public GenericRoleplayCommand getCommand(int id) {
        return commands.get(id);
    }
}
